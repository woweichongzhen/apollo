package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.dto.ApolloConfigNotification;
import com.ctrip.framework.apollo.core.dto.ApolloNotificationMessages;
import com.ctrip.framework.apollo.core.dto.ServiceDTO;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.core.schedule.ExponentialSchedulePolicy;
import com.ctrip.framework.apollo.core.schedule.SchedulePolicy;
import com.ctrip.framework.apollo.core.signature.Signature;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.ctrip.framework.apollo.util.http.HttpRequest;
import com.ctrip.framework.apollo.util.http.HttpResponse;
import com.ctrip.framework.apollo.util.http.HttpUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 远端配置长轮询服务
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class RemoteConfigLongPollService {

    private static final Logger logger = LoggerFactory.getLogger(RemoteConfigLongPollService.class);

    private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR);

    private static final Joiner.MapJoiner MAP_JOINER = Joiner.on("&").withKeyValueSeparator("=");

    private static final Escaper queryParamEscaper = UrlEscapers.urlFormParameterEscaper();

    /**
     * 初始化的通知id。-1
     */
    private static final long INIT_NOTIFICATION_ID = ConfigConsts.NOTIFICATION_ID_PLACEHOLDER;
    //90 seconds, should be longer than server side's long polling timeout, which is now 60 seconds

    /**
     * 长轮询超时时间
     * 90秒，应该比服务器端的长轮询超时（现在为60秒）更长
     */
    private static final int LONG_POLLING_READ_TIMEOUT = 90 * 1000;

    /**
     * 长轮询线程池
     */
    private final ExecutorService longPollingService;

    /**
     * 是否停止长轮询的标志
     */
    private final AtomicBoolean longPollingStopped;

    /**
     * 长轮询失败重试策略，范围【1,120】秒
     */
    private final SchedulePolicy longPollFailSchedulePolicyInSecond;

    /**
     * 长轮询限流器
     */
    private final RateLimiter longPollRateLimiter;

    /**
     * 长轮询是否开始的标志
     */
    private final AtomicBoolean longPollStarted;

    /**
     * 命名空间和对应命名空间仓库的缓存
     * key：命名空间
     * value：命名空间仓库缓存
     */
    private final Multimap<String, RemoteConfigRepository> longPollNamespaces;

    /**
     * 长轮询通知编号的缓存
     * key：命名空间
     * value：通知id
     */
    private final ConcurrentMap<String, Long> notifications;

    /**
     * 通知到的消息缓存
     * key：命名空间
     * value：监控的消息（watchkey）
     */
    private final Map<String, ApolloNotificationMessages> remoteNotificationMessages;

    // -> notificationId
    private final Type responseType;

    private final Gson gson;

    private final ConfigUtil configUtil;

    private HttpUtil httpUtil;

    private ConfigServiceLocator serviceLocator;

    /**
     * Constructor.
     */
    public RemoteConfigLongPollService() {
        longPollFailSchedulePolicyInSecond = new ExponentialSchedulePolicy(1, 120); //in second
        longPollingStopped = new AtomicBoolean(false);
        longPollingService = Executors.newSingleThreadExecutor(
                ApolloThreadFactory.create("RemoteConfigLongPollService", true));
        longPollStarted = new AtomicBoolean(false);
        longPollNamespaces =
                Multimaps.synchronizedSetMultimap(HashMultimap.<String, RemoteConfigRepository>create());
        notifications = Maps.newConcurrentMap();
        remoteNotificationMessages = Maps.newConcurrentMap();
        responseType = new TypeToken<List<ApolloConfigNotification>>() {
        }.getType();
        gson = new Gson();
        configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        httpUtil = ApolloInjector.getInstance(HttpUtil.class);
        serviceLocator = ApolloInjector.getInstance(ConfigServiceLocator.class);
        longPollRateLimiter = RateLimiter.create(configUtil.getLongPollQPS());
    }

    /**
     * 提交长轮询任务
     *
     * @param namespace              命名空间
     * @param remoteConfigRepository 远程仓库配置
     * @return true添加成功，false添加失败
     */
    public boolean submit(String namespace, RemoteConfigRepository remoteConfigRepository) {
        // 添加对应命名空间和仓库的缓存
        boolean added = longPollNamespaces.put(namespace, remoteConfigRepository);
        // 初始化对应的通知id
        notifications.putIfAbsent(namespace, INIT_NOTIFICATION_ID);
        // 如果长轮询未启动，开始启动
        if (!longPollStarted.get()) {
            startLongPolling();
        }
        return added;
    }

    /**
     * 启动长轮询
     */
    private void startLongPolling() {
        // CAS 更新为已启动
        if (!longPollStarted.compareAndSet(false, true)) {
            // 更新失败说明正在启动中
            //already started
            return;
        }
        try {
            final String appId = configUtil.getAppId();
            final String cluster = configUtil.getCluster();
            final String dataCenter = configUtil.getDataCenter();
            final String secret = configUtil.getAccessKeySecret();
            final long longPollingInitialDelayInMills = configUtil.getLongPollingInitialDelayInMills();

            longPollingService.submit(new Runnable() {
                @Override
                public void run() {
                    // 如果长轮询需要初始化延迟一定时间，则先睡一会
                    if (longPollingInitialDelayInMills > 0) {
                        try {
                            logger.debug("Long polling will start in {} ms.", longPollingInitialDelayInMills);
                            TimeUnit.MILLISECONDS.sleep(longPollingInitialDelayInMills);
                        } catch (InterruptedException e) {
                            //ignore
                        }
                    }
                    // 做长轮询刷新任务
                    doLongPollingRefresh(appId, cluster, dataCenter, secret);
                }
            });
        } catch (Throwable ex) {
            // 发生异常设置为未开始
            longPollStarted.set(false);
            ApolloConfigException exception =
                    new ApolloConfigException("Schedule long polling refresh failed", ex);
            Tracer.logError(exception);
            logger.warn(ExceptionUtil.getDetailMessage(exception));
        }
    }

    void stopLongPollingRefresh() {
        this.longPollingStopped.compareAndSet(false, true);
    }

    /**
     * 长轮询刷新任务，while true 执行
     *
     * @param appId      应用编号
     * @param cluster    集群名称
     * @param dataCenter 数据中心
     * @param secret     秘钥
     */
    private void doLongPollingRefresh(String appId, String cluster, String dataCenter, String secret) {
        final Random random = new Random();
        ServiceDTO lastServiceDto = null;
        // 如果长轮询未停止，且线程未中断，执行长轮询任务
        while (!longPollingStopped.get() && !Thread.currentThread().isInterrupted()) {
            // 限流5秒
            if (!longPollRateLimiter.tryAcquire(5, TimeUnit.SECONDS)) {
                // 最多等待5S
                //wait at most 5 seconds
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                }
            }
            Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "pollNotification");

            String url = null;
            try {
                // 随机用一个configservice
                if (lastServiceDto == null) {
                    List<ServiceDTO> configServices = getConfigServices();
                    lastServiceDto = configServices.get(random.nextInt(configServices.size()));
                }

                // 组装长轮询url
                url = assembleLongPollRefreshUrl(lastServiceDto.getHomepageUrl(), appId, cluster, dataCenter,
                        notifications);

                logger.debug("Long polling from {}", url);

                // 设置请求超时时间，认证请求头
                HttpRequest request = new HttpRequest(url);
                request.setReadTimeout(LONG_POLLING_READ_TIMEOUT);
                if (!StringUtils.isBlank(secret)) {
                    Map<String, String> headers = Signature.buildHttpHeaders(url, appId, secret);
                    request.setHeaders(headers);
                }

                transaction.addData("Url", url);

                // 发起长轮询请求
                final HttpResponse<List<ApolloConfigNotification>> response =
                        httpUtil.doGet(request, responseType);

                logger.debug("Long polling response: {}, url: {}", response.getStatusCode(), url);
                // 如果返回码为200，则有新的通知，刷新本地缓存
                if (response.getStatusCode() == 200
                        && response.getBody() != null) {
                    // 更新通知id即消息id
                    updateNotifications(response.getBody());
                    // 更新远端通知的消息
                    updateRemoteNotifications(response.getBody());
                    transaction.addData("Result", response.getBody().toString());

                    // 通知远端仓库，执行强制拉取配置
                    notify(lastServiceDto, response.getBody());
                }

                //try to load balance
                // 如果返回为304，说明没改动，重置dto，执行负载均衡
                if (response.getStatusCode() == 304 && random.nextBoolean()) {
                    lastServiceDto = null;
                }

                // 成功后重置指数重试时间
                longPollFailSchedulePolicyInSecond.success();
                transaction.addData("StatusCode", response.getStatusCode());
                transaction.setStatus(Transaction.SUCCESS);
            } catch (Throwable ex) {
                lastServiceDto = null;
                Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
                transaction.setStatus(ex);
                // 失败根据配置，指数时间睡眠再重试
                long sleepTimeInSecond = longPollFailSchedulePolicyInSecond.fail();
                logger.warn(
                        "Long polling failed, will retry in {} seconds. appId: {}, cluster: {}, namespaces: {}, long " +
                                "polling url: {}, reason: {}",
                        sleepTimeInSecond, appId, cluster, assembleNamespaces(), url,
                        ExceptionUtil.getDetailMessage(ex));
                try {
                    TimeUnit.SECONDS.sleep(sleepTimeInSecond);
                } catch (InterruptedException ie) {
                    //ignore
                }
            } finally {
                transaction.complete();
            }
        }
    }

    /**
     * 通知远端仓库，执行强制拉取配置
     *
     * @param lastServiceDto 最后一次的configservice地址
     * @param notifications  服务返回的通知信息
     */
    private void notify(ServiceDTO lastServiceDto, List<ApolloConfigNotification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return;
        }
        for (ApolloConfigNotification notification : notifications) {
            String namespaceName = notification.getNamespaceName();

            //create a new list to avoid ConcurrentModificationException
            // 需要重建的命名空间仓库信息
            List<RemoteConfigRepository> toBeNotified = Lists.newArrayList(longPollNamespaces.get(namespaceName));

            // 远端原始消息
            ApolloNotificationMessages originalMessages = remoteNotificationMessages.get(namespaceName);
            ApolloNotificationMessages remoteMessages = originalMessages == null
                    ? null
                    : originalMessages.clone();
            //since .properties are filtered out by default, so we need to check if there is any listener for it
            // 获取 properties 属性文件的远端仓库
            toBeNotified.addAll(longPollNamespaces.get(
                    String.format("%s.%s", namespaceName, ConfigFileFormat.Properties.getValue())));

            // 遍历远端仓库，执行强制配置更新
            for (RemoteConfigRepository remoteConfigRepository : toBeNotified) {
                try {
                    remoteConfigRepository.onLongPollNotified(lastServiceDto, remoteMessages);
                } catch (Throwable ex) {
                    Tracer.logError(ex);
                }
            }
        }
    }

    /**
     * 更新通知id即消息id
     *
     * @param deltaNotifications 服务端返回的增量更新的消息id等信息
     */
    private void updateNotifications(List<ApolloConfigNotification> deltaNotifications) {
        for (ApolloConfigNotification notification : deltaNotifications) {
            if (Strings.isNullOrEmpty(notification.getNamespaceName())) {
                continue;
            }

            // 如果包含该命名空间，更新通知id
            String namespaceName = notification.getNamespaceName();
            if (notifications.containsKey(namespaceName)) {
                notifications.put(namespaceName, notification.getNotificationId());
            }
            //since .properties are filtered out by default, so we need to check if there is notification with
            // .properties suffix
            // 因为 .properties 在默认情况下被过滤掉，所以我们需要检查是否有 .properties 后缀的通知。如有，更新 notifications
            // 也就是把 .properties 再加回来，然后更新通知id
            String namespaceNameWithPropertiesSuffix =
                    String.format("%s.%s", namespaceName, ConfigFileFormat.Properties.getValue());
            if (notifications.containsKey(namespaceNameWithPropertiesSuffix)) {
                notifications.put(namespaceNameWithPropertiesSuffix, notification.getNotificationId());
            }
        }
    }

    /**
     * 更新远端通知的消息
     *
     * @param deltaNotifications 增量更新的通知
     */
    private void updateRemoteNotifications(List<ApolloConfigNotification> deltaNotifications) {
        for (ApolloConfigNotification notification : deltaNotifications) {
            if (Strings.isNullOrEmpty(notification.getNamespaceName())) {
                continue;
            }

            if (notification.getMessages() == null || notification.getMessages().isEmpty()) {
                continue;
            }

            // 如果本地消息为空，直接更新，否则合并，根据消息id大小决定是否替换
            ApolloNotificationMessages localRemoteMessages =
                    remoteNotificationMessages.get(notification.getNamespaceName());
            if (localRemoteMessages == null) {
                localRemoteMessages = new ApolloNotificationMessages();
                remoteNotificationMessages.put(notification.getNamespaceName(), localRemoteMessages);
            }

            localRemoteMessages.mergeFrom(notification.getMessages());
        }
    }

    private String assembleNamespaces() {
        return STRING_JOINER.join(longPollNamespaces.keySet());
    }

    /**
     * 组装长轮询url
     *
     * @param uri              uri
     * @param appId            应用编号
     * @param cluster          集群名称
     * @param dataCenter       数据中心
     * @param notificationsMap 通知id集合
     * @return 长轮询url
     */
    String assembleLongPollRefreshUrl(String uri, String appId, String cluster, String dataCenter,
                                      Map<String, Long> notificationsMap) {
        Map<String, String> queryParams = Maps.newHashMap();
        queryParams.put("appId", queryParamEscaper.escape(appId));
        queryParams.put("cluster", queryParamEscaper.escape(cluster));
        queryParams
                .put("notifications", queryParamEscaper.escape(this.assembleNotifications(notificationsMap)));

        if (!Strings.isNullOrEmpty(dataCenter)) {
            queryParams.put("dataCenter", queryParamEscaper.escape(dataCenter));
        }
        String localIp = configUtil.getLocalIp();
        if (!Strings.isNullOrEmpty(localIp)) {
            queryParams.put("ip", queryParamEscaper.escape(localIp));
        }

        String params = MAP_JOINER.join(queryParams);
        if (!uri.endsWith("/")) {
            uri += "/";
        }

        return uri + "notifications/v2?" + params;
    }

    /**
     * 组装通知id的请求参数
     *
     * @param notificationsMap 通知id的map
     * @return 通知id请求参数
     */
    String assembleNotifications(Map<String, Long> notificationsMap) {
        List<ApolloConfigNotification> notifications = Lists.newArrayList();
        for (Map.Entry<String, Long> entry : notificationsMap.entrySet()) {
            ApolloConfigNotification notification = new ApolloConfigNotification(entry.getKey(), entry.getValue());
            notifications.add(notification);
        }
        return gson.toJson(notifications);
    }

    /**
     * 获取configService的服务配置
     *
     * @return 服务配置
     */
    private List<ServiceDTO> getConfigServices() {
        List<ServiceDTO> services = serviceLocator.getConfigServices();
        if (services.size() == 0) {
            throw new ApolloConfigException("No available config service");
        }

        return services;
    }
}
