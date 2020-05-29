package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.Apollo;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.dto.ApolloConfig;
import com.ctrip.framework.apollo.core.dto.ApolloNotificationMessages;
import com.ctrip.framework.apollo.core.dto.ServiceDTO;
import com.ctrip.framework.apollo.core.schedule.ExponentialSchedulePolicy;
import com.ctrip.framework.apollo.core.schedule.SchedulePolicy;
import com.ctrip.framework.apollo.core.signature.Signature;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.enums.ConfigSourceType;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.exceptions.ApolloConfigStatusCodeException;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.ctrip.framework.apollo.util.http.HttpRequest;
import com.ctrip.framework.apollo.util.http.HttpResponse;
import com.ctrip.framework.apollo.util.http.HttpUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 远端仓库配置
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class RemoteConfigRepository extends AbstractConfigRepository {

    private static final Logger logger = LoggerFactory.getLogger(RemoteConfigRepository.class);

    private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR);

    private static final Joiner.MapJoiner MAP_JOINER = Joiner.on("&").withKeyValueSeparator("=");

    private static final Escaper pathEscaper = UrlEscapers.urlPathSegmentEscaper();

    private static final Escaper queryParamEscaper = UrlEscapers.urlFormParameterEscaper();

    /**
     * 配置服务定位器
     */
    private final ConfigServiceLocator mServiceLocator;

    /**
     * http工具类
     */
    private final HttpUtil mHttpUtil;

    /**
     * 配置工具类
     */
    private final ConfigUtil mConfigUtil;

    /**
     * 远端配置长轮询服务
     */
    private final RemoteConfigLongPollService remoteConfigLongPollService;

    /**
     * 本地缓存的配置引用
     */
    private volatile AtomicReference<ApolloConfig> mConfigCache;

    /**
     * 命名空间名称
     */
    private final String mNamespace;

    private final static ScheduledExecutorService mExecutorService;

    /**
     * configservice服务信息的原子引用
     * 长轮询到通知的 Config Service 信息。在下一次轮询配置时，优先从该 Config Service 请求
     */
    private final AtomicReference<ServiceDTO> mLongPollServiceDto;

    /**
     * 通知消息的原子引用
     */
    private final AtomicReference<ApolloNotificationMessages> mRemoteMessages;

    /**
     * 加载配置的限流器
     */
    private final RateLimiter mLoadConfigRateLimiter;

    /**
     * 是否强制拉取缓存的标记
     * <p>
     * 若为 true ，则多一轮从 Config Service 拉取配置
     * 为 true 的原因，RemoteConfigRepository 知道 Config Service 有配置刷新
     */
    private final AtomicBoolean mConfigNeedForceRefresh;

    /**
     * 加载配置失败重试策略
     * {@link ExponentialSchedulePolicy}
     * 区间范围是 [1, 8] 秒
     */
    private final SchedulePolicy mLoadConfigFailSchedulePolicy;

    private final Gson gson;

    static {
        mExecutorService = Executors.newScheduledThreadPool(1,
                ApolloThreadFactory.create("RemoteConfigRepository", true));
    }

    /**
     * Constructor.
     *
     * @param namespace the namespace
     */
    public RemoteConfigRepository(String namespace) {
        mNamespace = namespace;
        mConfigCache = new AtomicReference<>();
        mConfigUtil = ApolloInjector.getInstance(ConfigUtil.class);
        mHttpUtil = ApolloInjector.getInstance(HttpUtil.class);
        mServiceLocator = ApolloInjector.getInstance(ConfigServiceLocator.class);
        remoteConfigLongPollService = ApolloInjector.getInstance(RemoteConfigLongPollService.class);
        mLongPollServiceDto = new AtomicReference<>();
        mRemoteMessages = new AtomicReference<>();
        mLoadConfigRateLimiter = RateLimiter.create(mConfigUtil.getLoadConfigQPS());
        mConfigNeedForceRefresh = new AtomicBoolean(true);
        mLoadConfigFailSchedulePolicy = new ExponentialSchedulePolicy(
                mConfigUtil.getOnErrorRetryInterval(),
                mConfigUtil.getOnErrorRetryInterval() * 8);
        gson = new Gson();

        // 尝试第一次同步配置，初始化缓存
        this.trySync();

        // 初始化定时刷新配置的定时任务
        this.schedulePeriodicRefresh();

        // 将自己注册到 RemoteConfigLongPollService 中，实现配置更新的实时通知
        this.scheduleLongPollingRefresh();
    }

    @Override
    public Properties getConfig() {
        // 获取配置时，如果缓存为空，执行一次同步
        if (mConfigCache.get() == null) {
            this.sync();
        }
        // 缓存不为空，则执行转换
        return transformApolloConfigToProperties(mConfigCache.get());
    }

    @Override
    public void setUpstreamRepository(ConfigRepository upstreamConfigRepository) {
        //remote config doesn't need upstream
        // 远端配置不需要备选
    }

    @Override
    public ConfigSourceType getSourceType() {
        return ConfigSourceType.REMOTE;
    }

    /**
     * 初始化定时刷新配置的定时任务
     */
    private void schedulePeriodicRefresh() {
        logger.debug("Schedule periodic refresh with interval: {} {}",
                mConfigUtil.getRefreshInterval(), mConfigUtil.getRefreshIntervalTimeUnit());
        mExecutorService.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        Tracer.logEvent("Apollo.ConfigService", String.format("periodicRefresh: %s", mNamespace));
                        logger.debug("refresh config for namespace: {}", mNamespace);
                        // 尝试同步配置
                        trySync();
                        Tracer.logEvent("Apollo.Client.Version", Apollo.VERSION);
                    }
                }, mConfigUtil.getRefreshInterval(), mConfigUtil.getRefreshInterval(),
                mConfigUtil.getRefreshIntervalTimeUnit());
    }

    @Override
    protected synchronized void sync() {
        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "syncRemoteConfig");

        try {
            // 获取之前缓存的apollo配置
            ApolloConfig previous = mConfigCache.get();

            // 再加载当前的apollo配置
            ApolloConfig current = loadApolloConfig();

            // reference equals means HTTP 304
            // 若不相等，说明有更改，即http返回码非304
            if (previous != current) {
                logger.debug("Remote Config refreshed!");
                mConfigCache.set(current);
                // 触发监听器改变通知
                this.fireRepositoryChange(mNamespace, this.getConfig());
            }

            if (current != null) {
                Tracer.logEvent(String.format("Apollo.Client.Configs.%s", current.getNamespaceName()),
                        current.getReleaseKey());
            }

            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            transaction.setStatus(ex);
            throw ex;
        } finally {
            transaction.complete();
        }
    }

    /**
     * 转换apollo配置为属性
     *
     * @param apolloConfig apollo配置
     * @return 属性
     */
    private Properties transformApolloConfigToProperties(ApolloConfig apolloConfig) {
        Properties result = propertiesFactory.getPropertiesInstance();
        result.putAll(apolloConfig.getConfigurations());
        return result;
    }

    /**
     * 加载apollo配置
     *
     * @return apollo配置
     */
    private ApolloConfig loadApolloConfig() {
        // 限流，5分钟的限流
        if (!mLoadConfigRateLimiter.tryAcquire(5, TimeUnit.SECONDS)) {
            //wait at most 5 seconds
            // 等待最多5分钟
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
            }
        }

        String appId = mConfigUtil.getAppId();
        String cluster = mConfigUtil.getCluster();
        String dataCenter = mConfigUtil.getDataCenter();
        String secret = mConfigUtil.getAccessKeySecret();
        Tracer.logEvent("Apollo.Client.ConfigMeta", STRING_JOINER.join(appId, cluster, mNamespace));

        // 计算最大重试次数。强制刷新则重试两次
        int maxRetries = mConfigNeedForceRefresh.get() ? 2 : 1;
        // 0意味着错误时不沉睡
        long onErrorSleepTime = 0; // 0 means no sleep
        Throwable exception = null;

        // 获取所有配置服务地址
        List<ServiceDTO> configServices = getConfigServices();
        String url = null;
        retryLoopLabel:

        // 循环读取配置重试次数直到成功。每一次，都会循环所有的 ServiceDTO 数组
        for (int i = 0; i < maxRetries; i++) {
            // 先打乱配置服务
            List<ServiceDTO> randomConfigServices = Lists.newLinkedList(configServices);
            Collections.shuffle(randomConfigServices);

            //Access the server which notifies the client first
            // 访问首先通知客户端的服务器，获取到时置空，避免下次循环到时重复访问上次的服务
            if (mLongPollServiceDto.get() != null) {
                randomConfigServices.add(0, mLongPollServiceDto.getAndSet(null));
            }

            // 遍历服务地址
            for (ServiceDTO configService : randomConfigServices) {
                // 如果失败后需要沉睡，那 sleep 一定时间
                if (onErrorSleepTime > 0) {
                    logger.warn(
                            "Load config failed, will retry in {} {}. appId: {}, cluster: {}, namespaces: {}",
                            onErrorSleepTime, mConfigUtil.getOnErrorRetryIntervalTimeUnit(), appId, cluster,
                            mNamespace);

                    try {
                        mConfigUtil.getOnErrorRetryIntervalTimeUnit().sleep(onErrorSleepTime);
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }

                // 组装需要轮询的url
                url = this.assembleQueryConfigUrl(configService.getHomepageUrl(), appId, cluster, mNamespace,
                        dataCenter, mRemoteMessages.get(), mConfigCache.get());

                logger.debug("Loading config from {}", url);

                // 如果需要认证，对url，appid，secret进行加密，构建认证头
                HttpRequest request = new HttpRequest(url);
                if (!StringUtils.isBlank(secret)) {
                    Map<String, String> headers = Signature.buildHttpHeaders(url, appId, secret);
                    request.setHeaders(headers);
                }

                Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "queryConfig");
                transaction.addData("Url", url);
                try {
                    // 发起请求
                    HttpResponse<ApolloConfig> response = mHttpUtil.doGet(request, ApolloConfig.class);
                    // 关闭强制刷新的标识
                    mConfigNeedForceRefresh.set(false);
                    // 标记请求成功
                    mLoadConfigFailSchedulePolicy.success();

                    transaction.addData("StatusCode", response.getStatusCode());
                    transaction.setStatus(Transaction.SUCCESS);

                    // 如果返回码为304，直接把缓存中的返回
                    if (response.getStatusCode() == 304) {
                        logger.debug("Config server responds with 304 HTTP status code.");
                        return mConfigCache.get();
                    }

                    ApolloConfig result = response.getBody();

                    logger.debug("Loaded config for {}: {}", mNamespace, result);

                    return result;
                } catch (ApolloConfigStatusCodeException ex) {
                    ApolloConfigStatusCodeException statusCodeException = ex;
                    //config not found
                    // 如果配置未发现，终止循环，抛出指定的异常
                    if (ex.getStatusCode() == 404) {
                        String message = String.format(
                                "Could not find config for namespace - appId: %s, cluster: %s, namespace: %s, " +
                                        "please check whether the configs are released in Apollo!",
                                appId, cluster, mNamespace);
                        statusCodeException = new ApolloConfigStatusCodeException(
                                ex.getStatusCode(),
                                message);
                    }
                    Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(statusCodeException));
                    transaction.setStatus(statusCodeException);
                    exception = statusCodeException;
                    if (ex.getStatusCode() == 404) {
                        break retryLoopLabel;
                    }
                } catch (Throwable ex) {
                    Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
                    transaction.setStatus(ex);
                    // 其他异常
                    exception = ex;
                } finally {
                    transaction.complete();
                }

                // if force refresh, do normal sleep, if normal config load, do exponential sleep
                // 如果强制刷新，进行正常时间重试；如果配置正常，请进行指数睡眠
                onErrorSleepTime = mConfigNeedForceRefresh.get()
                        ? mConfigUtil.getOnErrorRetryInterval()
                        : mLoadConfigFailSchedulePolicy.fail();
            }

        }
        String message = String.format(
                "Load Apollo Config failed - appId: %s, cluster: %s, namespace: %s, url: %s",
                appId, cluster, mNamespace, url);
        throw new ApolloConfigException(message, exception);
    }

    /**
     * 组装轮询 Config Service 的配置读取 /configs/{appId}/{clusterName}/{namespace:.+} 接口的 URL
     *
     * @param uri            uri
     * @param appId          应用编号
     * @param cluster        集群名称
     * @param namespace      命名空间名称
     * @param dataCenter     数据中心
     * @param remoteMessages 远端发送消息
     * @param previousConfig 上一份配置
     * @return url
     */
    String assembleQueryConfigUrl(String uri, String appId, String cluster, String namespace,
                                  String dataCenter, ApolloNotificationMessages remoteMessages,
                                  ApolloConfig previousConfig) {
        // 路径参数
        String path = "configs/%s/%s/%s";
        List<String> pathParams = Lists.newArrayList(
                pathEscaper.escape(appId),
                pathEscaper.escape(cluster),
                pathEscaper.escape(namespace));
        Map<String, String> queryParams = Maps.newHashMap();

        if (previousConfig != null) {
            queryParams.put("releaseKey", queryParamEscaper.escape(previousConfig.getReleaseKey()));
        }

        if (!Strings.isNullOrEmpty(dataCenter)) {
            queryParams.put("dataCenter", queryParamEscaper.escape(dataCenter));
        }

        // 获取本地ip
        String localIp = mConfigUtil.getLocalIp();
        if (!Strings.isNullOrEmpty(localIp)) {
            queryParams.put("ip", queryParamEscaper.escape(localIp));
        }

        if (remoteMessages != null) {
            queryParams.put("messages", queryParamEscaper.escape(gson.toJson(remoteMessages)));
        }

        String pathExpanded = String.format(path, pathParams.toArray());

        if (!queryParams.isEmpty()) {
            pathExpanded += "?" + MAP_JOINER.join(queryParams);
        }
        if (!uri.endsWith("/")) {
            uri += "/";
        }
        return uri + pathExpanded;
    }

    /**
     * 将自己注册到 RemoteConfigLongPollService 中，实现配置更新的实时通知
     * 当 RemoteConfigLongPollService 长轮询到该 RemoteConfigRepository 的 Namespace 下的配置更新时，
     * 会回调 {@link #onLongPollNotified(ServiceDTO, ApolloNotificationMessages)} 方法
     */
    private void scheduleLongPollingRefresh() {
        remoteConfigLongPollService.submit(mNamespace, this);
    }

    /**
     * 长轮询到配置通知时，回调该方法
     *
     * @param longPollNotifiedServiceDto 长轮询配置通知dto
     * @param remoteMessages             远端通知消息
     */
    public void onLongPollNotified(ServiceDTO longPollNotifiedServiceDto, ApolloNotificationMessages remoteMessages) {
        // 设置本地使用的configservice，下次优先使用
        mLongPollServiceDto.set(longPollNotifiedServiceDto);

        // 设置远端消息
        mRemoteMessages.set(remoteMessages);
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                // 设置为强制刷新
                mConfigNeedForceRefresh.set(true);
                // 开始同步配置
                trySync();
            }
        });
    }

    /**
     * 获取可用的配置服务信息
     *
     * @return 可用的配置服务dto集合
     */
    private List<ServiceDTO> getConfigServices() {
        List<ServiceDTO> services = mServiceLocator.getConfigServices();
        if (services.size() == 0) {
            throw new ApolloConfigException("No available config service");
        }

        return services;
    }
}
