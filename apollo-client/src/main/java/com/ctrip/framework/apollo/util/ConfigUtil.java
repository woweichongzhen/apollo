package com.ctrip.framework.apollo.util;

import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.MetaDomainConsts;
import com.ctrip.framework.apollo.core.enums.Env;
import com.ctrip.framework.apollo.core.enums.EnvUtils;
import com.ctrip.framework.foundation.Foundation;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static com.ctrip.framework.apollo.util.factory.PropertiesFactory.APOLLO_PROPERTY_ORDER_ENABLE;

/**
 * 配置工具类
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConfigUtil {

    private static final Logger logger = LoggerFactory.getLogger(ConfigUtil.class);

    /**
     * 定时刷新配置的周期
     */
    private int refreshInterval = 5;

    /**
     * 定时刷新配置的单位
     */
    private TimeUnit refreshIntervalTimeUnit = TimeUnit.MINUTES;
    private int connectTimeout = 1000; //1 second
    private int readTimeout = 5000; //5 seconds
    private String cluster;
    private int loadConfigQPS = 2; //2 times per second
    private int longPollQPS = 2; //2 times per second
    //for on error retry
    private long onErrorRetryInterval = 1;//1 second

    /**
     * 失败重试等待时间单位，单位秒
     */
    private TimeUnit onErrorRetryIntervalTimeUnit = TimeUnit.SECONDS;
    //for typed config cache of parser result, e.g. integer, double, long, etc.
    private long maxConfigCacheSize = 500;//500 cache key
    private long configCacheExpireTime = 1;//1 minute
    private TimeUnit configCacheExpireTimeUnit = TimeUnit.MINUTES;//1 minute
    private long longPollingInitialDelayInMills = 2000;//2 seconds

    /**
     * 自动更新注入到spring的属性，默认更新
     */
    private boolean autoUpdateInjectedSpringProperties = true;
    private final RateLimiter warnLogRateLimiter;

    /**
     * 是否开启属性排序，默认未开启
     */
    private boolean propertiesOrdered = false;

    public ConfigUtil() {
        warnLogRateLimiter = RateLimiter.create(0.017); // 1 warning log output per minute
        initRefreshInterval();
        initConnectTimeout();
        initReadTimeout();
        initCluster();
        initQPS();
        initMaxConfigCacheSize();
        initLongPollingInitialDelayInMills();
        initAutoUpdateInjectedSpringProperties();
        initPropertiesOrdered();
    }

    /**
     * 获取当前应用的应用编号
     *
     * @return 如果appid无价值，使用 {@link ConfigConsts.NO_APPID_PLACEHOLDER}
     */
    public String getAppId() {
        String appId = Foundation.app().getAppId();
        if (Strings.isNullOrEmpty(appId)) {
            appId = ConfigConsts.NO_APPID_PLACEHOLDER;
            if (warnLogRateLimiter.tryAcquire()) {
                logger.warn("app.id is not set, please make sure it is set in classpath:/META-INF/app.properties, now" +
                        " apollo will only load public namespace configurations!");
            }
        }
        return appId;
    }

    /**
     * Get the access key secret for the current application.
     *
     * @return the current access key secret, null if there is no such secret.
     */
    public String getAccessKeySecret() {
        return Foundation.app().getAccessKeySecret();
    }

    /**
     * Get the data center info for the current application.
     *
     * @return the current data center, null if there is no such info.
     */
    public String getDataCenter() {
        return Foundation.server().getDataCenter();
    }

    private void initCluster() {
        //Load data center from system property
        cluster = System.getProperty(ConfigConsts.APOLLO_CLUSTER_KEY);

        //Use data center as cluster
        if (Strings.isNullOrEmpty(cluster)) {
            cluster = getDataCenter();
        }

        //Use default cluster
        if (Strings.isNullOrEmpty(cluster)) {
            cluster = ConfigConsts.CLUSTER_NAME_DEFAULT;
        }
    }

    /**
     * 获取当前应用的集群名称
     *
     * @return 集群名称，如果未定义返回 default
     */
    public String getCluster() {
        return cluster;
    }

    /**
     * 获取当前的环境
     * Get the current environment.
     *
     * @return the env, UNKNOWN if env is not set or invalid
     */
    public Env getApolloEnv() {
        return EnvUtils.transformEnv(Foundation.server().getEnvType());
    }

    public String getLocalIp() {
        return Foundation.net().getHostAddress();
    }

    /**
     * 获取元数据服务域名
     *
     * @return 元数据服务域名
     */
    public String getMetaServerDomainName() {
        return MetaDomainConsts.getDomain(getApolloEnv());
    }

    private void initConnectTimeout() {
        String customizedConnectTimeout = System.getProperty("apollo.connectTimeout");
        if (!Strings.isNullOrEmpty(customizedConnectTimeout)) {
            try {
                connectTimeout = Integer.parseInt(customizedConnectTimeout);
            } catch (Throwable ex) {
                logger.error("Config for apollo.connectTimeout is invalid: {}", customizedConnectTimeout);
            }
        }
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    private void initReadTimeout() {
        String customizedReadTimeout = System.getProperty("apollo.readTimeout");
        if (!Strings.isNullOrEmpty(customizedReadTimeout)) {
            try {
                readTimeout = Integer.parseInt(customizedReadTimeout);
            } catch (Throwable ex) {
                logger.error("Config for apollo.readTimeout is invalid: {}", customizedReadTimeout);
            }
        }
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    private void initRefreshInterval() {
        String customizedRefreshInterval = System.getProperty("apollo.refreshInterval");
        if (!Strings.isNullOrEmpty(customizedRefreshInterval)) {
            try {
                refreshInterval = Integer.parseInt(customizedRefreshInterval);
            } catch (Throwable ex) {
                logger.error("Config for apollo.refreshInterval is invalid: {}", customizedRefreshInterval);
            }
        }
    }

    public int getRefreshInterval() {
        return refreshInterval;
    }

    public TimeUnit getRefreshIntervalTimeUnit() {
        return refreshIntervalTimeUnit;
    }

    private void initQPS() {
        String customizedLoadConfigQPS = System.getProperty("apollo.loadConfigQPS");
        if (!Strings.isNullOrEmpty(customizedLoadConfigQPS)) {
            try {
                loadConfigQPS = Integer.parseInt(customizedLoadConfigQPS);
            } catch (Throwable ex) {
                logger.error("Config for apollo.loadConfigQPS is invalid: {}", customizedLoadConfigQPS);
            }
        }

        String customizedLongPollQPS = System.getProperty("apollo.longPollQPS");
        if (!Strings.isNullOrEmpty(customizedLongPollQPS)) {
            try {
                longPollQPS = Integer.parseInt(customizedLongPollQPS);
            } catch (Throwable ex) {
                logger.error("Config for apollo.longPollQPS is invalid: {}", customizedLongPollQPS);
            }
        }
    }

    public int getLoadConfigQPS() {
        return loadConfigQPS;
    }

    public int getLongPollQPS() {
        return longPollQPS;
    }

    public long getOnErrorRetryInterval() {
        return onErrorRetryInterval;
    }

    public TimeUnit getOnErrorRetryIntervalTimeUnit() {
        return onErrorRetryIntervalTimeUnit;
    }

    /**
     * 获取默认本地缓存目录
     *
     * @return 默认本地缓存目录，下面有应用appId一级
     */
    public String getDefaultLocalCacheDir() {
        // 先从自定义中获取，如果不为空，就使用自定义的
        String cacheRoot = getCustomizedCacheRoot();

        if (!Strings.isNullOrEmpty(cacheRoot)) {
            return cacheRoot + File.separator + getAppId();
        }

        // 使用默认的，根据windows和linux构造目录路径
        cacheRoot = isOSWindows() ? "C:\\opt\\data\\%s" : "/opt/data/%s";
        return String.format(cacheRoot, getAppId());
    }

    /**
     * 获取自定义的根目录
     *
     * @return 自定义的根目录
     */
    private String getCustomizedCacheRoot() {
        // 从JVM环境变量获取
        String cacheRoot = System.getProperty("apollo.cacheDir");
        if (Strings.isNullOrEmpty(cacheRoot)) {
            // 从OS环境变量获取
            cacheRoot = System.getenv("APOLLO_CACHEDIR");
        }
        if (Strings.isNullOrEmpty(cacheRoot)) {
            // 从 server.properties 配置文件获取
            cacheRoot = Foundation.server().getProperty("apollo.cacheDir", null);
        }
        if (Strings.isNullOrEmpty(cacheRoot)) {
            // 从app.properties 配置文件获取
            cacheRoot = Foundation.app().getProperty("apollo.cacheDir", null);
        }

        return cacheRoot;
    }

    /**
     * 判断是否为本地模式，即环境是否为 LOCAL 环境
     *
     * @return true是，false不是
     */
    public boolean isInLocalMode() {
        try {
            return Env.LOCAL == getApolloEnv();
        } catch (Throwable ex) {
            //ignore
        }
        return false;
    }

    /**
     * 判断操作系统是否为windows
     *
     * @return true为 windows ，false为linux
     */
    public boolean isOSWindows() {
        String osName = System.getProperty("os.name");
        if (Strings.isNullOrEmpty(osName)) {
            return false;
        }
        return osName.startsWith("Windows");
    }

    private void initMaxConfigCacheSize() {
        String customizedConfigCacheSize = System.getProperty("apollo.configCacheSize");
        if (!Strings.isNullOrEmpty(customizedConfigCacheSize)) {
            try {
                maxConfigCacheSize = Long.parseLong(customizedConfigCacheSize);
            } catch (Throwable ex) {
                logger.error("Config for apollo.configCacheSize is invalid: {}", customizedConfigCacheSize);
            }
        }
    }

    public long getMaxConfigCacheSize() {
        return maxConfigCacheSize;
    }

    public long getConfigCacheExpireTime() {
        return configCacheExpireTime;
    }

    public TimeUnit getConfigCacheExpireTimeUnit() {
        return configCacheExpireTimeUnit;
    }

    private void initLongPollingInitialDelayInMills() {
        String customizedLongPollingInitialDelay = System
                .getProperty("apollo.longPollingInitialDelayInMills");
        if (!Strings.isNullOrEmpty(customizedLongPollingInitialDelay)) {
            try {
                longPollingInitialDelayInMills = Long.parseLong(customizedLongPollingInitialDelay);
            } catch (Throwable ex) {
                logger.error("Config for apollo.longPollingInitialDelayInMills is invalid: {}",
                        customizedLongPollingInitialDelay);
            }
        }
    }

    public long getLongPollingInitialDelayInMills() {
        return longPollingInitialDelayInMills;
    }

    /**
     * 初始化是否自动更新spring注入的属性
     */
    private void initAutoUpdateInjectedSpringProperties() {
        // 先判断系统属性中是否开启
        String enableAutoUpdate = System.getProperty("apollo.autoUpdateInjectedSpringProperties");
        if (Strings.isNullOrEmpty(enableAutoUpdate)) {
            // 再判断 app.properties 中是否开启
            enableAutoUpdate = Foundation.app()
                    .getProperty("apollo.autoUpdateInjectedSpringProperties", null);
        }
        if (!Strings.isNullOrEmpty(enableAutoUpdate)) {
            autoUpdateInjectedSpringProperties = Boolean.parseBoolean(enableAutoUpdate.trim());
        }
    }

    public boolean isAutoUpdateInjectedSpringPropertiesEnabled() {
        return autoUpdateInjectedSpringProperties;
    }

    private void initPropertiesOrdered() {
        String enablePropertiesOrdered = System.getProperty(APOLLO_PROPERTY_ORDER_ENABLE);

        if (Strings.isNullOrEmpty(enablePropertiesOrdered)) {
            enablePropertiesOrdered = Foundation.app().getProperty(APOLLO_PROPERTY_ORDER_ENABLE, "false");
        }

        if (!Strings.isNullOrEmpty(enablePropertiesOrdered)) {
            try {
                propertiesOrdered = Boolean.parseBoolean(enablePropertiesOrdered);
            } catch (Throwable ex) {
                logger.warn("Config for {} is invalid: {}, set default value: false",
                        APOLLO_PROPERTY_ORDER_ENABLE, enablePropertiesOrdered);
            }
        }
    }

    public boolean isPropertiesOrderEnabled() {
        return propertiesOrdered;
    }
}
