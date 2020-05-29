package com.ctrip.framework.apollo.configservice.service.config;

import com.ctrip.framework.apollo.biz.entity.Release;
import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;
import com.ctrip.framework.apollo.biz.message.Topics;
import com.ctrip.framework.apollo.biz.service.ReleaseMessageService;
import com.ctrip.framework.apollo.biz.service.ReleaseService;
import com.ctrip.framework.apollo.biz.utils.ReleaseMessageKeyGenerator;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.dto.ApolloNotificationMessages;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 通过guava缓存实现的配置服务
 * <p>
 * config service with guava cache
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConfigServiceWithCache extends AbstractConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigServiceWithCache.class);

    /**
     * 默认缓存过期时间，单位：分钟
     */
    private static final long DEFAULT_EXPIRED_AFTER_ACCESS_IN_MINUTES = 60;

    private static final String TRACER_EVENT_CACHE_INVALIDATE = "ConfigCache.Invalidate";
    private static final String TRACER_EVENT_CACHE_LOAD = "ConfigCache.LoadFromDB";
    private static final String TRACER_EVENT_CACHE_LOAD_ID = "ConfigCache.LoadFromDBById";
    private static final String TRACER_EVENT_CACHE_GET = "ConfigCache.Get";
    private static final String TRACER_EVENT_CACHE_GET_ID = "ConfigCache.GetById";

    private static final Splitter STRING_SPLITTER =
            Splitter.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR).omitEmptyStrings();

    @Autowired
    private ReleaseService releaseService;

    @Autowired
    private ReleaseMessageService releaseMessageService;

    /**
     * ConfigCacheEntry 缓存
     * <p>
     * key：Watch Key
     * value：{@link ReleaseMessage#getMessage}
     */
    private LoadingCache<String, ConfigCacheEntry> configCache;

    /**
     * Release 缓存
     * <p>
     * KEY ：Release 编号
     * value：发布版本
     */
    private LoadingCache<Long, Optional<Release>> configIdCache;

    /**
     * 无 ConfigCacheEntry 占位对象
     */
    private final ConfigCacheEntry nullConfigCacheEntry;

    public ConfigServiceWithCache() {
        nullConfigCacheEntry = new ConfigCacheEntry(ConfigConsts.NOTIFICATION_ID_PLACEHOLDER, null);
    }

    /**
     * 初始化缓存对象，缓存过期，加载机制
     */
    @PostConstruct
    void initialize() {
        configCache = CacheBuilder.newBuilder()
                .expireAfterAccess(DEFAULT_EXPIRED_AFTER_ACCESS_IN_MINUTES, TimeUnit.MINUTES)
                .build(new CacheLoader<String, ConfigCacheEntry>() {
                    @Override
                    public ConfigCacheEntry load(String key) {
                        // 如果格式不正确，返回一个空的占位对象
                        List<String> namespaceInfo = STRING_SPLITTER.splitToList(key);
                        if (namespaceInfo.size() != 3) {
                            Tracer.logError(
                                    new IllegalArgumentException(String.format("Invalid cache load key %s", key)));
                            return nullConfigCacheEntry;
                        }

                        Transaction transaction = Tracer.newTransaction(TRACER_EVENT_CACHE_LOAD, key);
                        try {
                            // 查找最新的发布消息，用于判断读取缓存时，判断缓存是否过期
                            ReleaseMessage latestReleaseMessage =
                                    releaseMessageService.findLatestReleaseMessageForMessages(Lists
                                            .newArrayList(key));
                            // 获取最新的有效的发布对象
                            Release latestRelease = releaseService.findLatestActiveRelease(namespaceInfo.get(0),
                                    namespaceInfo.get(1),
                                    namespaceInfo.get(2));

                            transaction.setStatus(Transaction.SUCCESS);

                            // 获取消息id，即通知id
                            long notificationId = latestReleaseMessage == null
                                    ? ConfigConsts.NOTIFICATION_ID_PLACEHOLDER
                                    : latestReleaseMessage.getId();

                            // 无通知id并且发布消息为空，返回占位对象
                            if (notificationId == ConfigConsts.NOTIFICATION_ID_PLACEHOLDER
                                    && latestRelease == null) {
                                return nullConfigCacheEntry;
                            }

                            return new ConfigCacheEntry(notificationId, latestRelease);
                        } catch (Throwable ex) {
                            transaction.setStatus(ex);
                            throw ex;
                        } finally {
                            transaction.complete();
                        }
                    }
                });
        configIdCache = CacheBuilder.newBuilder()
                .expireAfterAccess(DEFAULT_EXPIRED_AFTER_ACCESS_IN_MINUTES, TimeUnit.MINUTES)
                .build(new CacheLoader<Long, Optional<Release>>() {
                    @Override
                    public Optional<Release> load(Long key) {
                        Transaction transaction = Tracer.newTransaction(TRACER_EVENT_CACHE_LOAD_ID,
                                String.valueOf(key));
                        try {
                            // 获取未抛弃的发布版本
                            Release release = releaseService.findActiveOne(key);

                            transaction.setStatus(Transaction.SUCCESS);

                            return Optional.ofNullable(release);
                        } catch (Throwable ex) {
                            transaction.setStatus(ex);
                            throw ex;
                        } finally {
                            transaction.complete();
                        }
                    }
                });
    }

    @Override
    protected Release findActiveOne(long id, ApolloNotificationMessages clientMessages) {
        Tracer.logEvent(TRACER_EVENT_CACHE_GET_ID, String.valueOf(id));
        return configIdCache.getUnchecked(id).orElse(null);
    }

    @Override
    protected Release findLatestActiveRelease(String appId, String clusterName, String namespaceName,
                                              ApolloNotificationMessages clientMessages) {
        // 根据 appId + clusterName + namespaceName ，获得 ReleaseMessage 的 消息内容
        String key = ReleaseMessageKeyGenerator.generate(appId, clusterName, namespaceName);

        Tracer.logEvent(TRACER_EVENT_CACHE_GET, key);

        // 从缓存 configCache 中，读取 ConfigCacheEntry 对象
        ConfigCacheEntry cacheEntry = configCache.getUnchecked(key);

        //cache is out-dated
        // 若客户端的通知编号更大，说明缓存已经过期。
        if (clientMessages != null
                && clientMessages.has(key) &&
                clientMessages.get(key) > cacheEntry.getNotificationId()) {
            //invalidate the cache and try to load from db again
            invalidate(key);
            // 重新从 DB 中加载
            cacheEntry = configCache.getUnchecked(key);
        }

        return cacheEntry.getRelease();
    }

    /**
     * 先根据消息内容，即 watch key把指定缓存过期掉
     *
     * @param key 缓存key
     */
    private void invalidate(String key) {
        configCache.invalidate(key);
        Tracer.logEvent(TRACER_EVENT_CACHE_INVALIDATE, key);
    }

    @Override
    public void handleMessage(ReleaseMessage message, String channel) {
        logger.info("message received - channel: {}, message: {}", channel, message);

        // 处理监听收到的消息，非指定通道，或消息内容为空，不处理
        if (!Topics.APOLLO_RELEASE_TOPIC.equals(channel)
                || Strings.isNullOrEmpty(message.getMessage())) {
            return;
        }

        try {
            // 先根据消息内容，即 watch key把指定缓存过期掉
            invalidate(message.getMessage());

            //warm up the cache
            // 再进行预热缓存，即根据消息内容，读取 ConfigCacheEntry 对象，重新从 DB 中加载
            configCache.getUnchecked(message.getMessage());
        } catch (Throwable ex) {
            //ignore
        }
    }

    /**
     * 配置缓存实体
     */
    private static class ConfigCacheEntry {

        /**
         * 通知id
         */
        private final long notificationId;

        /**
         * 发布版本
         */
        private final Release release;

        public ConfigCacheEntry(long notificationId, Release release) {
            this.notificationId = notificationId;
            this.release = release;
        }

        public long getNotificationId() {
            return notificationId;
        }

        public Release getRelease() {
            return release;
        }
    }
}
