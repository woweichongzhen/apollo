package com.ctrip.framework.apollo.common.config;

import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 可刷新的配置抽象类
 */
public abstract class RefreshableConfig {

    private static final Logger logger = LoggerFactory.getLogger(RefreshableConfig.class);

    /**
     * 数组分隔符
     */
    private static final String LIST_SEPARATOR = ",";

    /**
     * 逗号分割器
     */
    protected Splitter splitter = Splitter.on(LIST_SEPARATOR).omitEmptyStrings().trimResults();

    /**
     * 配置刷新周期
     */
    private static final int CONFIG_REFRESH_INTERVAL = 60;

    @Autowired
    private ConfigurableEnvironment environment;

    private List<RefreshablePropertySource> propertySources;

    /**
     * register refreshable property source.
     * Notice: The front property source has higher priority.
     * <p>
     * 注册可刷新的属性源
     * 靠前的属性源具有更高的优先级
     */
    protected abstract List<RefreshablePropertySource> getRefreshablePropertySources();

    @PostConstruct
    public void setup() {
        // 获取属性源
        propertySources = getRefreshablePropertySources();
        if (CollectionUtils.isEmpty(propertySources)) {
            throw new IllegalStateException("Property sources can not be empty.");
        }

        // 遍历属性源并刷新，添加属性源到环境变量的最后
        for (RefreshablePropertySource propertySource : propertySources) {
            propertySource.refresh();
            environment.getPropertySources().addLast(propertySource);
        }

        // 用于更新配置的线程池
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(
                1, ApolloThreadFactory.create("ConfigRefresher", true));

        // 提交定时任务，定时刷新配置
        executorService.scheduleWithFixedDelay(() -> {
            try {
                propertySources.forEach(RefreshablePropertySource::refresh);
            } catch (Throwable t) {
                logger.error("Refresh configs failed.", t);
                Tracer.logError("Refresh configs failed.", t);
            }
        }, CONFIG_REFRESH_INTERVAL, CONFIG_REFRESH_INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * 获取int属性值
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 值
     */
    public int getIntProperty(String key, int defaultValue) {
        try {
            String value = getValue(key);
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (Throwable e) {
            Tracer.logError("Get int property failed.", e);
            return defaultValue;
        }
    }

    /**
     * 获取boolean属性值
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 值
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        try {
            String value = getValue(key);
            return value == null ? defaultValue : "true".equals(value);
        } catch (Throwable e) {
            Tracer.logError("Get boolean property failed.", e);
            return defaultValue;
        }
    }

    /**
     * 获取指定key的字符串数组
     *
     * @param key          指定key
     * @param defaultValue 默认数组
     * @return 字符串数组
     */
    public String[] getArrayProperty(String key, String[] defaultValue) {
        try {
            String value = getValue(key);
            return Strings.isNullOrEmpty(value)
                    ? defaultValue
                    : value.split(LIST_SEPARATOR);
        } catch (Throwable e) {
            Tracer.logError("Get array property failed.", e);
            return defaultValue;
        }
    }

    /**
     * 从环境中获取属性值，并提供默认值
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 属性值
     */
    public String getValue(String key, String defaultValue) {
        try {
            return environment.getProperty(key, defaultValue);
        } catch (Throwable e) {
            Tracer.logError("Get value failed.", e);
            return defaultValue;
        }
    }

    /**
     * 从环境中获取属性值
     *
     * @param key 键
     * @return 值
     */
    public String getValue(String key) {
        return environment.getProperty(key);
    }

}
