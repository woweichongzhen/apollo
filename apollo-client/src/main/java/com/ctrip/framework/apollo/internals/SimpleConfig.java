package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.enums.ConfigSourceType;
import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.google.common.base.Function;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 简单的配置
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class SimpleConfig extends AbstractConfig implements RepositoryChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(SimpleConfig.class);

    /**
     * 本配置的命名空间
     */
    private final String namespace;

    /**
     * 配置仓库
     */
    private final ConfigRepository configRepository;

    private volatile Properties configProperties;

    /**
     * 配置来源
     */
    private volatile ConfigSourceType sourceType = ConfigSourceType.NONE;

    /**
     * @param namespace        the namespace for this config instance 本配置的命名空间
     * @param configRepository the config repository for this config instance 本配置的配置仓库
     */
    public SimpleConfig(String namespace, ConfigRepository configRepository) {
        this.namespace = namespace;
        this.configRepository = configRepository;
        this.initialize();
    }

    /**
     * 初始化配置
     */
    private void initialize() {
        try {
            // 更新内存配置
            updateConfig(configRepository.getConfig(), configRepository.getSourceType());
        } catch (Throwable ex) {
            Tracer.logError(ex);
            logger.warn("Init Apollo Simple Config failed - namespace: {}, reason: {}", namespace,
                    ExceptionUtil.getDetailMessage(ex));
        } finally {
            // 注册状态改变监听器，即时没有可用的仓库，一旦仓库可用，可以即时通知
            configRepository.addChangeListener(this);
        }
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        if (configProperties == null) {
            logger.warn("Could not load config from Apollo, always return default value!");
            return defaultValue;
        }
        return configProperties.getProperty(key, defaultValue);
    }

    @Override
    public Set<String> getPropertyNames() {
        if (configProperties == null) {
            return Collections.emptySet();
        }

        return configProperties.stringPropertyNames();
    }

    @Override
    public ConfigSourceType getSourceType() {
        return sourceType;
    }

    @Override
    public synchronized void onRepositoryChange(String namespace, Properties newProperties) {
        if (newProperties.equals(configProperties)) {
            return;
        }

        // 缓存新配置属性
        Properties newConfigProperties = propertiesFactory.getPropertiesInstance();
        newConfigProperties.putAll(newProperties);

        // 转换list为属性名的map
        List<ConfigChange> changes = calcPropertyChanges(namespace, configProperties, newConfigProperties);
        Map<String, ConfigChange> changeMap = Maps.uniqueIndex(changes,
                new Function<ConfigChange, String>() {
                    @Override
                    public String apply(ConfigChange input) {
                        return input.getPropertyName();
                    }
                });

        // 更新配置属性
        this.updateConfig(newConfigProperties, configRepository.getSourceType());

        // 清空配置缓存
        this.clearConfigCache();

        // 触发配置更改
        this.fireConfigChange(new ConfigChangeEvent(this.namespace, changeMap));

        Tracer.logEvent("Apollo.Client.ConfigChanges", this.namespace);
    }

    /**
     * 更新内存配置
     *
     * @param newConfigProperties 新配置属性
     * @param sourceType          配置来源
     */
    private void updateConfig(Properties newConfigProperties, ConfigSourceType sourceType) {
        configProperties = newConfigProperties;
        this.sourceType = sourceType;
    }
}
