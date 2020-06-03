package com.ctrip.framework.apollo.spring.config;

import com.ctrip.framework.apollo.Config;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * 配置属性源工厂
 */
public class ConfigPropertySourceFactory {

    /**
     * 配置属性源集合
     */
    private final List<ConfigPropertySource> configPropertySources = Lists.newLinkedList();

    /**
     * 包装成配置属性源，同时添加到配置属性源集合中
     *
     * @param name   命名空间
     * @param source 配置对象
     * @return 配置属性源
     */
    public ConfigPropertySource getConfigPropertySource(String name, Config source) {
        ConfigPropertySource configPropertySource = new ConfigPropertySource(name, source);

        configPropertySources.add(configPropertySource);

        return configPropertySource;
    }

    /**
     * 获取所有的配置属性源
     *
     * @return 配置属性源集合
     */
    public List<ConfigPropertySource> getAllConfigPropertySources() {
        return Lists.newLinkedList(configPropertySources);
    }
}
