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


public abstract class RefreshableConfig {

    private static final Logger logger = LoggerFactory.getLogger(RefreshableConfig.class);

    /**
     * 数组分隔符
     */
    private static final String LIST_SEPARATOR = ",";
    //TimeUnit: second
    private static final int CONFIG_REFRESH_INTERVAL = 60;

    protected Splitter splitter = Splitter.on(LIST_SEPARATOR).omitEmptyStrings().trimResults();

    @Autowired
    private ConfigurableEnvironment environment;

    private List<RefreshablePropertySource> propertySources;

    /**
     * register refreshable property source.
     * Notice: The front property source has higher priority.
     */
    protected abstract List<RefreshablePropertySource> getRefreshablePropertySources();

    @PostConstruct
    public void setup() {

        propertySources = getRefreshablePropertySources();
        if (CollectionUtils.isEmpty(propertySources)) {
            throw new IllegalStateException("Property sources can not be empty.");
        }

        //add property source to environment
        for (RefreshablePropertySource propertySource : propertySources) {
            propertySource.refresh();
            environment.getPropertySources().addLast(propertySource);
        }

        //task to update configs
        ScheduledExecutorService
                executorService =
                Executors.newScheduledThreadPool(1, ApolloThreadFactory.create("ConfigRefresher", true));

        executorService
                .scheduleWithFixedDelay(() -> {
                    try {
                        propertySources.forEach(RefreshablePropertySource::refresh);
                    } catch (Throwable t) {
                        logger.error("Refresh configs failed.", t);
                        Tracer.logError("Refresh configs failed.", t);
                    }
                }, CONFIG_REFRESH_INTERVAL, CONFIG_REFRESH_INTERVAL, TimeUnit.SECONDS);
    }

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
     * 获取属性值
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
     * 获取指定key的数组属性
     *
     * @param key          指定key
     * @param defaultValue 默认值
     * @return 指定key的属性
     */
    public String[] getArrayProperty(String key, String[] defaultValue) {
        try {
            String value = getValue(key);
            return Strings.isNullOrEmpty(value) ? defaultValue : value.split(LIST_SEPARATOR);
        } catch (Throwable e) {
            Tracer.logError("Get array property failed.", e);
            return defaultValue;
        }
    }

    /**
     * 获取超级用户
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 超级用户
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
