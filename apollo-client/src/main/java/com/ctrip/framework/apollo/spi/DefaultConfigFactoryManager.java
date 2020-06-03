package com.ctrip.framework.apollo.spi;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * 默认的配置工厂管理器
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfigFactoryManager implements ConfigFactoryManager {

    /**
     * 配置注册
     */
    private final ConfigRegistry configRegistry;

    /**
     * 配置工厂缓存
     * key：命名空间
     * value：配置工厂实例
     */
    private final Map<String, ConfigFactory> factories = Maps.newConcurrentMap();

    public DefaultConfigFactoryManager() {
        configRegistry = ApolloInjector.getInstance(ConfigRegistry.class);
    }

    @Override
    public ConfigFactory getFactory(String namespace) {
        // 第一次检测注册中心的配置工厂
        ConfigFactory factory = configRegistry.getFactory(namespace);
        if (factory != null) {
            return factory;
        }

        // 第二次从缓存中获取配置工厂
        factory = factories.get(namespace);
        if (factory != null) {
            return factory;
        }

        // 第三次根据命名空间实例化配置工厂
        factory = ApolloInjector.getInstance(ConfigFactory.class, namespace);
        if (factory != null) {
            return factory;
        }

        // 第四次获取默认的配置工厂
        factory = ApolloInjector.getInstance(ConfigFactory.class);

        // 缓存默认的配置工厂
        factories.put(namespace, factory);

        // 工厂不应该为null
        return factory;
    }
}
