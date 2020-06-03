package com.ctrip.framework.apollo.spi;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 默认的配置注册
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfigRegistry implements ConfigRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConfigRegistry.class);

    /**
     * 配置工厂缓存
     * key：命名空间
     * value：配置工厂实例
     */
    private final Map<String, ConfigFactory> instances = Maps.newConcurrentMap();

    @Override
    public void register(String namespace, ConfigFactory factory) {
        if (instances.containsKey(namespace)) {
            LOGGER.warn("ConfigFactory({}) is overridden by {}!", namespace, factory.getClass());
        }

        instances.put(namespace, factory);
    }

    @Override
    public ConfigFactory getFactory(String namespace) {
        return instances.get(namespace);
    }
}
