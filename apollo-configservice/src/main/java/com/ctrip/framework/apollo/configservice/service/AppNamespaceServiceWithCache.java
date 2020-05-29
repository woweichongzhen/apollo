package com.ctrip.framework.apollo.configservice.service;

import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.repository.AppNamespaceRepository;
import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.configservice.wrapper.CaseInsensitiveMapWrapper;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 缓存应用命名空间
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Service
public class AppNamespaceServiceWithCache implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(AppNamespaceServiceWithCache.class);

    private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR).skipNulls();

    private final AppNamespaceRepository appNamespaceRepository;

    private final BizConfig bizConfig;

    /**
     * 增量扫描周期
     */
    private int scanInterval;

    /**
     * 增量扫描时间单位
     */
    private TimeUnit scanIntervalTimeUnit;

    /**
     * 重建周期
     */
    private int rebuildInterval;

    /**
     * 重建周期单位
     */
    private TimeUnit rebuildIntervalTimeUnit;

    /**
     * 定时任务
     */
    private ScheduledExecutorService scheduledExecutorService;

    /**
     * 扫描到的最大消息id
     */
    private long maxIdScanned;

    /**
     * 公共命名空间缓存
     * store namespaceName -> AppNamespace
     * key：namespaceName
     * value：AppNameSpace
     */
    private CaseInsensitiveMapWrapper<AppNamespace> publicAppNamespaceCache;

    /**
     * 应用命名空间缓存：
     * appId+namespaceName -> AppNamespace
     * key：appId+namespaceName
     * value：AppNameSpace
     * 不区分大小写
     */
    private CaseInsensitiveMapWrapper<AppNamespace> appNamespaceCache;

    /**
     * 应用命名空间id缓存
     * store id -> AppNamespace
     */
    private Map<Long, AppNamespace> appNamespaceIdCache;

    public AppNamespaceServiceWithCache(
            final AppNamespaceRepository appNamespaceRepository,
            final BizConfig bizConfig) {
        this.appNamespaceRepository = appNamespaceRepository;
        this.bizConfig = bizConfig;
        initialize();
    }

    /**
     * 启动时初始化缓存
     */
    private void initialize() {
        maxIdScanned = 0;
        publicAppNamespaceCache = new CaseInsensitiveMapWrapper<>(Maps.newConcurrentMap());
        appNamespaceCache = new CaseInsensitiveMapWrapper<>(Maps.newConcurrentMap());
        appNamespaceIdCache = Maps.newConcurrentMap();
        scheduledExecutorService = Executors.newScheduledThreadPool(
                1,
                ApolloThreadFactory.create("AppNamespaceServiceWithCache", true));
    }

    /**
     * 获取命名空间（忽视大小写）
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @return 缓存中的应用命名空间
     */
    public AppNamespace findByAppIdAndNamespace(String appId, String namespaceName) {
        Preconditions.checkArgument(!StringUtils.isContainEmpty(appId, namespaceName),
                "appId and namespaceName must not be empty");
        return appNamespaceCache.get(STRING_JOINER.join(appId, namespaceName));
    }

    /**
     * 获取应用实际拥有的命名空间（忽视大小写）
     *
     * @param appId          应用编号
     * @param namespaceNames 命名空间名称集合
     * @return 属于应用的命名空间
     */
    public List<AppNamespace> findByAppIdAndNamespaces(String appId, Set<String> namespaceNames) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(appId), "appId must not be null");
        if (namespaceNames == null || namespaceNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<AppNamespace> result = Lists.newArrayList();
        for (String namespaceName : namespaceNames) {
            // 从缓存中获取指定的命名空间，最后返回实际拥有的
            AppNamespace appNamespace = appNamespaceCache.get(STRING_JOINER.join(appId, namespaceName));
            if (appNamespace != null) {
                result.add(appNamespace);
            }
        }
        return result;
    }

    /**
     * 从公共命名空间缓存中获取（忽视大小写）
     *
     * @param namespaceName 命名空间名称
     * @return 应用命名空间
     */
    public AppNamespace findPublicNamespaceByName(String namespaceName) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespaceName), "namespaceName must not be empty");
        return publicAppNamespaceCache.get(namespaceName);
    }

    /**
     * 查找公共命名空间（忽视大小写）
     *
     * @param namespaceNames 命名空间名称集合
     * @return 实际上的应用明明空间
     */
    public List<AppNamespace> findPublicNamespacesByNames(Set<String> namespaceNames) {
        if (namespaceNames == null || namespaceNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<AppNamespace> result = Lists.newArrayList();
        for (String namespaceName : namespaceNames) {
            // 过滤公共命名空间
            AppNamespace appNamespace = publicAppNamespaceCache.get(namespaceName);
            if (appNamespace != null) {
                result.add(appNamespace);
            }
        }
        return result;
    }

    @Override
    public void afterPropertiesSet() {
        // 填充基础定时任务数据
        populateDataBaseInterval();
        // 阻塞直到加载完成，全量初始化
        scanNewAppNamespaces();
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            Transaction transaction = Tracer.newTransaction(
                    "Apollo.AppNamespaceServiceWithCache",
                    "rebuildCache");
            try {
                // 重建缓存
                this.updateAndDeleteCache();
                transaction.setStatus(Transaction.SUCCESS);
            } catch (Throwable ex) {
                transaction.setStatus(ex);
                logger.error("Rebuild cache failed", ex);
            } finally {
                transaction.complete();
            }
        }, rebuildInterval, rebuildInterval, rebuildIntervalTimeUnit);

        scheduledExecutorService.scheduleWithFixedDelay(
                // 扫描新缓存，即增量
                this::scanNewAppNamespaces,
                scanInterval,
                scanInterval,
                scanIntervalTimeUnit);
    }

    /**
     * 扫描新命名空间缓存，启动时会全量初始化一次
     */
    private void scanNewAppNamespaces() {
        Transaction transaction = Tracer.newTransaction("Apollo.AppNamespaceServiceWithCache",
                "scanNewAppNamespaces");
        try {
            // 加载
            this.loadNewAppNamespaces();
            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            transaction.setStatus(ex);
            logger.error("Load new app namespaces failed", ex);
        } finally {
            transaction.complete();
        }
    }

    /**
     * 加载新的应用命名空间
     */
    private void loadNewAppNamespaces() {
        boolean hasMore = true;
        while (hasMore && !Thread.currentThread().isInterrupted()) {
            //current batch is 500
            // 一次拉取500条
            List<AppNamespace> appNamespaces =
                    appNamespaceRepository.findFirst500ByIdGreaterThanOrderByIdAsc(maxIdScanned);
            if (CollectionUtils.isEmpty(appNamespaces)) {
                break;
            }
            // 合并到缓存中
            mergeAppNamespaces(appNamespaces);
            int scanned = appNamespaces.size();
            // 更新扫描到的最后id
            maxIdScanned = appNamespaces.get(scanned - 1).getId();
            // 判断是否还有更多
            hasMore = scanned == 500;
            logger.info("Loaded {} new app namespaces with startId {}", scanned, maxIdScanned);
        }
    }

    /**
     * 合并新缓存到当前缓存中
     *
     * @param appNamespaces 新应用命名空间数据
     */
    private void mergeAppNamespaces(List<AppNamespace> appNamespaces) {
        for (AppNamespace appNamespace : appNamespaces) {
            // appId+appNamespaceName，命名空间
            appNamespaceCache.put(assembleAppNamespaceKey(appNamespace), appNamespace);
            // 命名空间id，命名空间
            appNamespaceIdCache.put(appNamespace.getId(), appNamespace);
            // 命名空间名称，命名空间
            if (appNamespace.isPublic()) {
                publicAppNamespaceCache.put(appNamespace.getName(), appNamespace);
            }
        }
    }

    /**
     * 重建缓存，更新或删除
     */
    private void updateAndDeleteCache() {
        // 获取缓存中所有的命名空间id
        List<Long> ids = Lists.newArrayList(appNamespaceIdCache.keySet());
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        // 按500分批，分成多份，每次重建500份缓存
        List<List<Long>> partitionIds = Lists.partition(ids, 500);
        for (List<Long> toRebuild : partitionIds) {
            Iterable<AppNamespace> appNamespaces = appNamespaceRepository.findAllById(toRebuild);

            if (appNamespaces == null) {
                continue;
            }

            //handle updated
            // 处理更新的，返回遍历过的id
            Set<Long> foundIds = handleUpdatedAppNamespaces(appNamespaces);

            //handle deleted
            // 处理删除的，即未更新的就都删除掉
            handleDeletedAppNamespaces(Sets.difference(Sets.newHashSet(toRebuild), foundIds));
        }
    }

    /**
     * 处理更新的缓存
     *
     * @param appNamespaces 数据库的应用命名空间
     * @return 更新后的id
     */
    private Set<Long> handleUpdatedAppNamespaces(Iterable<AppNamespace> appNamespaces) {
        Set<Long> foundIds = Sets.newHashSet();
        for (AppNamespace appNamespace : appNamespaces) {
            foundIds.add(appNamespace.getId());
            AppNamespace thatInCache = appNamespaceIdCache.get(appNamespace.getId());
            // 缓存中存在该命名空间，并且数据库的修改时间比较新，分别更新三份缓存
            if (thatInCache != null
                    && appNamespace.getDataChangeLastModifiedTime().after(thatInCache.getDataChangeLastModifiedTime())) {
                // id缓存
                appNamespaceIdCache.put(appNamespace.getId(), appNamespace);

                // appid+name缓存，先存，不同则删除老的
                String oldKey = assembleAppNamespaceKey(thatInCache);
                String newKey = assembleAppNamespaceKey(appNamespace);
                appNamespaceCache.put(newKey, appNamespace);
                //in case appId or namespaceName changes
                if (!newKey.equals(oldKey)) {
                    appNamespaceCache.remove(oldKey);
                }

                // 公共缓存，加新的删老的
                if (appNamespace.isPublic()) {
                    publicAppNamespaceCache.put(appNamespace.getName(), appNamespace);

                    //in case namespaceName changes
                    if (!appNamespace.getName().equals(thatInCache.getName()) && thatInCache.isPublic()) {
                        publicAppNamespaceCache.remove(thatInCache.getName());
                    }
                } else if (thatInCache.isPublic()) {
                    // 新的并不是公共缓存，老的需要移除掉
                    //just in case isPublic changes
                    publicAppNamespaceCache.remove(thatInCache.getName());
                }
                logger.info("Found AppNamespace changes, old: {}, new: {}", thatInCache, appNamespace);
            }
        }
        return foundIds;
    }

    /**
     * 删除缓存
     *
     * @param deletedIds 要删除的id
     */
    private void handleDeletedAppNamespaces(Set<Long> deletedIds) {
        if (CollectionUtils.isEmpty(deletedIds)) {
            return;
        }

        // 未更新的都删除掉，3分缓存
        for (Long deletedId : deletedIds) {
            AppNamespace deleted = appNamespaceIdCache.remove(deletedId);
            if (deleted == null) {
                continue;
            }
            appNamespaceCache.remove(assembleAppNamespaceKey(deleted));
            if (deleted.isPublic()) {
                AppNamespace publicAppNamespace = publicAppNamespaceCache.get(deleted.getName());
                // in case there is some dirty data, e.g. public namespace deleted in some app and now created in
                // another app
                // 万一有一些脏数据，例如在某些应用中删除了公共命名空间，现在在另一个应用中创建了公共命名空间
                if (publicAppNamespace == deleted) {
                    publicAppNamespaceCache.remove(deleted.getName());
                }
            }
            logger.info("Found AppNamespace deleted, {}", deleted);
        }
    }

    /**
     * 应用命名空间缓存key生成
     *
     * @param appNamespace 应用命名空间
     * @return 缓存key（appId+appNamespaceName）
     */
    private String assembleAppNamespaceKey(AppNamespace appNamespace) {
        return STRING_JOINER.join(appNamespace.getAppId(), appNamespace.getName());
    }

    /**
     * 填充基础定时任务数据
     */
    private void populateDataBaseInterval() {
        scanInterval = bizConfig.appNamespaceCacheScanInterval();
        scanIntervalTimeUnit = bizConfig.appNamespaceCacheScanIntervalTimeUnit();
        rebuildInterval = bizConfig.appNamespaceCacheRebuildInterval();
        rebuildIntervalTimeUnit = bizConfig.appNamespaceCacheRebuildIntervalTimeUnit();
    }

    //only for test use
    private void reset() throws Exception {
        scheduledExecutorService.shutdownNow();
        initialize();
        afterPropertiesSet();
    }
}
