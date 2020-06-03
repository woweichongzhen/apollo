package com.ctrip.framework.apollo.spi;

/**
 * 配置注册
 * The manually config registry, use with caution!
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigRegistry {
    /**
     * 为指定命名空间注册配置工厂
     * Register the config factory for the namespace specified.
     *
     * @param namespace the namespace
     * @param factory   the factory for this namespace
     */
    void register(String namespace, ConfigFactory factory);

    /**
     * 根据命名空间获取注册的配置工厂
     * Get the registered config factory for the namespace.
     *
     * @param namespace the namespace
     * @return the factory registered for this namespace
     */
    ConfigFactory getFactory(String namespace);
}
