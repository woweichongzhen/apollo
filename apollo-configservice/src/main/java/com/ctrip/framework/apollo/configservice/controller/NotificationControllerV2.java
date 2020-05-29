package com.ctrip.framework.apollo.configservice.controller;

import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;
import com.ctrip.framework.apollo.biz.message.ReleaseMessageListener;
import com.ctrip.framework.apollo.biz.message.Topics;
import com.ctrip.framework.apollo.biz.utils.EntityManagerUtil;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.configservice.service.ReleaseMessageServiceWithCache;
import com.ctrip.framework.apollo.configservice.util.NamespaceUtil;
import com.ctrip.framework.apollo.configservice.util.WatchKeysUtil;
import com.ctrip.framework.apollo.configservice.wrapper.DeferredResultWrapper;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.dto.ApolloConfigNotification;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 长轮询通知API
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@RestController
@RequestMapping("/notifications/v2")
public class NotificationControllerV2 implements ReleaseMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationControllerV2.class);

    /**
     * 监控key和返回结果的map，get请求无结果通知时，则暂时挂起
     */
    private final Multimap<String, DeferredResultWrapper> deferredResults =
            Multimaps.synchronizedSetMultimap(TreeMultimap.create(String.CASE_INSENSITIVE_ORDER, Ordering.natural()));

    /**
     * +号分割
     */
    private static final Splitter STRING_SPLITTER =
            Splitter.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR).omitEmptyStrings();

    /**
     * apollo配置通知序列化类
     */
    private static final Type notificationsTypeReference =
            new TypeToken<List<ApolloConfigNotification>>() {
            }.getType();

    /**
     * 大量通知批量执行线程池
     */
    private final ExecutorService largeNotificationBatchExecutorService;

    private final WatchKeysUtil watchKeysUtil;
    private final ReleaseMessageServiceWithCache releaseMessageService;
    private final EntityManagerUtil entityManagerUtil;
    private final NamespaceUtil namespaceUtil;
    private final Gson gson;
    private final BizConfig bizConfig;

    @Autowired
    public NotificationControllerV2(
            final WatchKeysUtil watchKeysUtil,
            final ReleaseMessageServiceWithCache releaseMessageService,
            final EntityManagerUtil entityManagerUtil,
            final NamespaceUtil namespaceUtil,
            final Gson gson,
            final BizConfig bizConfig) {
        largeNotificationBatchExecutorService = Executors.newSingleThreadExecutor(
                ApolloThreadFactory.create("NotificationControllerV2", true));
        this.watchKeysUtil = watchKeysUtil;
        this.releaseMessageService = releaseMessageService;
        this.entityManagerUtil = entityManagerUtil;
        this.namespaceUtil = namespaceUtil;
        this.gson = gson;
        this.bizConfig = bizConfig;
    }

    /**
     * 轮询获取配置，挂起后执行通知
     *
     * @param appId                 应用编号
     * @param cluster               集群名称
     * @param notificationsAsString 客户端本地的配置通知信息
     * @param dataCenter            数据中心
     * @param clientIp              ip地址
     * @return 返回通知
     */
    @GetMapping
    public DeferredResult<ResponseEntity<List<ApolloConfigNotification>>> pollNotification(
            @RequestParam(value = "appId") String appId,
            @RequestParam(value = "cluster") String cluster,
            @RequestParam(value = "notifications") String notificationsAsString,
            @RequestParam(value = "dataCenter", required = false) String dataCenter,
            @RequestParam(value = "ip", required = false) String clientIp) {
        // 转换客户端本地的通知字符串为实体类
        List<ApolloConfigNotification> notifications = null;
        try {
            notifications = gson.fromJson(notificationsAsString, notificationsTypeReference);
        } catch (Throwable ex) {
            Tracer.logError(ex);
        }

        // 不存在要增量通知的，返回400
        if (CollectionUtils.isEmpty(notifications)) {
            throw new BadRequestException("Invalid format of notifications: " + notificationsAsString);
        }

        // 延迟结果包装类
        DeferredResultWrapper deferredResultWrapper = new DeferredResultWrapper(bizConfig.longPollingTimeoutInMilli());
        // 命名空间集合
        Set<String> namespaces = Sets.newHashSet();
        // 客户端的通知，key为命名空间名称，value为通知id
        Map<String, Long> clientSideNotifications = Maps.newHashMap();
        // 过滤该应用要的通知项，key为命名空间名称，value为客户端的通知
        Map<String, ApolloConfigNotification> filteredNotifications = filterNotifications(appId, notifications);

        // 遍历过滤后应该要通知的选项
        for (Map.Entry<String, ApolloConfigNotification> notificationEntry : filteredNotifications.entrySet()) {
            String normalizedNamespace = notificationEntry.getKey();
            ApolloConfigNotification notification = notificationEntry.getValue();

            // 命名空间
            namespaces.add(normalizedNamespace);
            // 客户端通知
            clientSideNotifications.put(normalizedNamespace, notification.getNotificationId());
            // 记录名字被归一化的 Namespace 。因为最终返回给客户端时，要使用原始的 Namespace 名字，否则客户端无法识别
            if (!Objects.equals(notification.getNamespaceName(), normalizedNamespace)) {
                deferredResultWrapper.recordNamespaceNameNormalizedResult(
                        notification.getNamespaceName(),
                        normalizedNamespace);
            }
        }

        // 要通知的命名空间变化为空，直接400
        if (CollectionUtils.isEmpty(namespaces)) {
            throw new BadRequestException("Invalid format of notifications: " + notificationsAsString);
        }

        // 获取要监听的key的集合（包括自己应用的命名空间，以及关联的公共命名空间）
        Multimap<String, String> watchedKeysMap =
                watchKeysUtil.assembleAllWatchKeys(appId, cluster, namespaces, dataCenter);
        Set<String> watchedKeys = Sets.newHashSet(watchedKeysMap.values());

        /*
         * 1、set deferredResult before the check, for avoid more waiting
         * If the check before setting deferredResult,it may receive a notification the next time
         * when method handleMessage is executed between check and set deferredResult.
         *
         * 设置超时事件
         * 在检查是否有新通知前设置deferredResult，以免长连接会有更多的等待
         * 如果在设置deferredResult之前进行检查，则可能在检查和设置deferredResult之间执行handleMessage方法时会收到下次通知
         */
        deferredResultWrapper
                .onTimeout(() -> logWatchedKeys(watchedKeys, "Apollo.LongPoll.TimeOutKeys"));

        // 完成时移除监控的key
        deferredResultWrapper.onCompletion(() -> {
            //unregister all keys
            for (String key : watchedKeys) {
                deferredResults.remove(key, deferredResultWrapper);
            }
            logWatchedKeys(watchedKeys, "Apollo.LongPoll.CompletedKeys");
        });

        //register all keys
        // 缓存所有要监听的key
        for (String key : watchedKeys) {
            this.deferredResults.put(key, deferredResultWrapper);
        }

        logWatchedKeys(watchedKeys, "Apollo.LongPoll.RegisteredKeys");
        logger.debug("Listening {} from appId: {}, cluster: {}, namespace: {}, datacenter: {}",
                watchedKeys, appId, cluster, namespaces, dataCenter);

        /*
         * 2、check new release
         * 检查所有监控key的最新发布消息
         */
        List<ReleaseMessage> latestReleaseMessages =
                releaseMessageService.findLatestReleaseMessagesGroupByMessages(watchedKeys);

        /*
         * Manually close the entity manager.
         * Since for async request, Spring won't do so until the request is finished,
         * which is unacceptable since we are doing long polling - means the db connection would be hold
         * for a very long time
         *
         * 手动关闭JPA实体管理器。
         * 由于对于异步请求，Spring不会在请求完成之前关闭此管理器，这是不可接受的，
         * 因为我们正在进行长时间轮询-意味着数据库连接将被保持很长时间
         *
         * 但实际上下面的过程已经不需要数据库连接了，因此关闭掉
         */
        entityManagerUtil.closeEntityManager();

        // 根据刚查出来的最新的消息，获取即将要通知的apollo配置通知数组（每个命名空间的通知消息集合）
        List<ApolloConfigNotification> newNotifications = getApolloConfigNotifications(
                namespaces,
                clientSideNotifications,
                watchedKeysMap,
                latestReleaseMessages);
        // 如果得到了新通知，直接返回，无需挂起连接，直接结束长轮询
        if (!CollectionUtils.isEmpty(newNotifications)) {
            deferredResultWrapper.setResult(newNotifications);
        }

        return deferredResultWrapper.getResult();
    }

    /**
     * 过滤该应用需要的通知
     *
     * @param appId         应用编号
     * @param notifications 客户端要的通知
     * @return 返回命名空间和相同的配置通知的map
     */
    private Map<String, ApolloConfigNotification> filterNotifications(String appId,
                                                                      List<ApolloConfigNotification> notifications) {
        Map<String, ApolloConfigNotification> filteredNotifications = Maps.newHashMap();
        for (ApolloConfigNotification notification : notifications) {
            if (Strings.isNullOrEmpty(notification.getNamespaceName())) {
                continue;
            }
            /*
             * strip out .properties suffix
             *
             * 移除 .properties的尾缀，并设置到通知中，即原始的命名空间
             * 例如 application.properties => application
             */
            String originalNamespace = namespaceUtil.filterNamespaceName(notification.getNamespaceName());
            notification.setNamespaceName(originalNamespace);

            /*
             * fix the character case issue, such as FX.apollo <-> fx.apollo
             *
             * 修复字符大小写问题，例如FX.apollo <-> fx.apollo
             *
             * 获得归一化的 Namespace 名字。因为，客户端 Namespace 会填写错大小写。
             * 例如，缓存中 Namespace 名为 fx.apollo ，而客户端 Namespace 名为 FX.Apollo
             * 通过归一化后，统一为 fx.apollo
             * 即统一转换为小写
             */
            String normalizedNamespace = namespaceUtil.normalizeNamespace(appId, originalNamespace);

            /*
             * in case client side namespace name has character case issue and has difference notification ids
             * such as FX.apollo = 1 but fx.apollo = 2, we should let FX.apollo have the chance to update its
             * notification id
             * which means we should record FX.apollo = 1 here and ignore fx.apollo = 2
             *
             * 如果客户端命名空间名称存在 字符大小写问题 并且 具有不同的通知ID ，
             * 例如 FX.apollo = 1 但 fx.apollo = 2 ，我们应该让 FX.apollo 有机会更新其通知ID
             * 这意味着我们应该在此处记录 FX.apollo = 1 并忽略 fx.apollo = 2
             *
             * 保留最小的通知id，更大的id留给后面去更新
             */
            if (filteredNotifications.containsKey(normalizedNamespace) &&
                    filteredNotifications.get(normalizedNamespace).getNotificationId() < notification.getNotificationId()) {
                continue;
            }

            filteredNotifications.put(normalizedNamespace, notification);
        }
        return filteredNotifications;
    }

    /**
     * 根据刚查出来的最新的消息，获取即将要通知的apollo配置通知数组
     *
     * @param namespaces              命名空间集合
     * @param clientSideNotifications 客户端单边通知
     * @param watchedKeysMap          监控的key和通知id的map
     * @param latestReleaseMessages   通过key查找出来的最新的消息
     * @return apollo配置通知集合，包含每个命名空间的通知
     */
    private List<ApolloConfigNotification> getApolloConfigNotifications(Set<String> namespaces,
                                                                        Map<String, Long> clientSideNotifications,
                                                                        Multimap<String, String> watchedKeysMap,
                                                                        List<ReleaseMessage> latestReleaseMessages) {
        List<ApolloConfigNotification> newNotifications = Lists.newArrayList();
        if (!CollectionUtils.isEmpty(latestReleaseMessages)) {
            // 存在最新的通知消息，把消息key和消息id放到map中
            Map<String, Long> latestNotifications = Maps.newHashMap();
            for (ReleaseMessage releaseMessage : latestReleaseMessages) {
                latestNotifications.put(releaseMessage.getMessage(), releaseMessage.getId());
            }

            // 遍历需要通知的命名空间
            for (String namespace : namespaces) {
                // 获取客户端那边的id
                long clientSideId = clientSideNotifications.get(namespace);
                // 临时的通知id
                long latestId = ConfigConsts.NOTIFICATION_ID_PLACEHOLDER;

                // 遍历监控的watchkey
                Collection<String> namespaceWatchedKeys = watchedKeysMap.get(namespace);
                for (String namespaceWatchedKey : namespaceWatchedKeys) {
                    // 如果监控的key存在命名空间通知id（即消息id），则获取到最新的消息id
                    long namespaceNotificationId = latestNotifications.getOrDefault(
                            namespaceWatchedKey,
                            ConfigConsts.NOTIFICATION_ID_PLACEHOLDER);
                    if (namespaceNotificationId > latestId) {
                        latestId = namespaceNotificationId;
                    }
                }

                // 如果最新的消息id大于客户端那边的id，即存在配置更新，则添加到要通知的集合中
                if (latestId > clientSideId) {
                    ApolloConfigNotification notification = new ApolloConfigNotification(namespace, latestId);
                    namespaceWatchedKeys.stream()
                            // 如果监控的key中和最新的消息key符合
                            .filter(latestNotifications::containsKey)
                            .forEach(namespaceWatchedKey ->
                                    // 则把这条消息加入到这个命名空间的返回消息集合中
                                    notification.addMessage(
                                            namespaceWatchedKey,
                                            latestNotifications.get(namespaceWatchedKey)));
                    // 添加对应命名空间的通知
                    newNotifications.add(notification);
                }
            }
        }
        return newNotifications;
    }

    /**
     * 检测到 ReleaseMessage 的 message时，调用此方法通知找到对应的 DeferredResult ，执行返回逻辑
     *
     * @param message 消息
     * @param channel 通道
     */
    @Override
    public void handleMessage(ReleaseMessage message, String channel) {
        logger.info("message received - channel: {}, message: {}", channel, message);

        String content = message.getMessage();
        Tracer.logEvent("Apollo.LongPoll.Messages", content);

        // 并非关心的通道或者消息内容为空，直接返回
        if (!Topics.APOLLO_RELEASE_TOPIC.equals(channel)
                || Strings.isNullOrEmpty(content)) {
            return;
        }

        // 通过消息内容即 watchkey 中，提取改变的命名空间名称
        String changedNamespace = RETRIEVE_NAMESPACE_FROM_RELEASE_MESSAGE.apply(content);

        // 消息内容格式有问题，直接返回
        if (Strings.isNullOrEmpty(changedNamespace)) {
            logger.error("message format invalid - {}", content);
            return;
        }

        // 无人监控，直接返回
        if (!deferredResults.containsKey(content)) {
            return;
        }

        //create a new list to avoid ConcurrentModificationException
        // 创建一个新列表以避免ConcurrentModificationException
        List<DeferredResultWrapper> results = Lists.newArrayList(deferredResults.get(content));

        // 创建需要通知的对象，即监听的命名空间的改变
        ApolloConfigNotification configNotification = new ApolloConfigNotification(changedNamespace, message.getId());
        configNotification.addMessage(content, message.getId());

        //do async notification if too many clients
        // 如果存在太多客户端连接，执行异步通知，即一部分一部分的通知，避免 惊群效应
        if (results.size() > bizConfig.releaseMessageNotificationBatch()) {
            largeNotificationBatchExecutorService.submit(() -> {
                logger.debug("Async notify {} clients for key {} with batch {}", results.size(), content,
                        bizConfig.releaseMessageNotificationBatch());
                // 遍历客户端挂起的 result，再提交
                for (int i = 0; i < results.size(); i++) {
                    /*
                     * 每N个客户端，等待一定时间
                     * 假设一个公共 Namespace 有 10W 台机器使用，如果该公共 Namespace 发布时直接下发配置更新消息的话，
                     * 就会导致这 10W 台机器一下子都来请求配置，这动静就有点大了，而且对 Config Service 的压力也会比较大。
                     * 所以每下发一定配置是，就等待一会
                     */
                    if (i > 0 && i % bizConfig.releaseMessageNotificationBatch() == 0) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(bizConfig.releaseMessageNotificationBatchIntervalInMilli());
                        } catch (InterruptedException e) {
                            //ignore
                        }
                    }
                    logger.debug("Async notify {}", results.get(i));
                    results.get(i).setResult(configNotification);
                }
            });
            return;
        }

        logger.debug("Notify {} clients for key {}", results.size(), content);

        // 数量不是很多，直接设置result，通知即可
        for (DeferredResultWrapper result : results) {
            result.setResult(configNotification);
        }
        logger.debug("Notification completed");
    }

    /**
     * 通过 message 获取命名空间的名称等参数
     * 消息组成：appId+cluster+namespace
     */
    private static final Function<String, String> RETRIEVE_NAMESPACE_FROM_RELEASE_MESSAGE =
            releaseMessage -> {
                if (Strings.isNullOrEmpty(releaseMessage)) {
                    return null;
                }
                List<String> keys = STRING_SPLITTER.splitToList(releaseMessage);
                if (keys.size() != 3) {
                    logger.error("message format invalid - {}", releaseMessage);
                    return null;
                }
                return keys.get(2);
            };

    private void logWatchedKeys(Set<String> watchedKeys, String eventName) {
        for (String watchedKey : watchedKeys) {
            Tracer.logEvent(eventName, watchedKey);
        }
    }
}
