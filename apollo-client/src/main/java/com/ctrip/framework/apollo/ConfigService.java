package com.ctrip.framework.apollo;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.internals.ConfigManager;
import com.ctrip.framework.apollo.spi.ConfigFactory;
import com.ctrip.framework.apollo.spi.ConfigRegistry;

/**
 * 客户端配置服务使用入口
 * <p>
 * Entry point for client config use
 * <p>
 * apollo客户端提供两种形式的配置对象接口：
 * Config ，配置接口
 * ConfigFile ，配置文件接口
 * 实际情况下，我们使用 Config 居多。
 * 另外，有一点需要注意，Config 和 ConfigFile 差异在于形式，而不是类型。
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConfigService {

    /**
     * 单例
     */
    private static final ConfigService INSTANCE = new ConfigService();

    private volatile ConfigManager configManager;
    private volatile ConfigRegistry configRegistry;

    /**
     * volatile+synchronized自检索保证配置管理对象为一个
     *
     * @return 配置管理
     */
    private ConfigManager getManager() {
        if (configManager == null) {
            synchronized (this) {
                if (configManager == null) {
                    configManager = ApolloInjector.getInstance(ConfigManager.class);
                }
            }
        }

        return configManager;
    }

    /**
     * volatile+synchronized自检索保证注配置注册为一个
     *
     * @return 配置注册
     */
    private ConfigRegistry getRegistry() {
        if (configRegistry == null) {
            synchronized (this) {
                if (configRegistry == null) {
                    configRegistry = ApolloInjector.getInstance(ConfigRegistry.class);
                }
            }
        }

        return configRegistry;
    }

    /**
     * 获取应用配置实例
     * Get Application's config instance.
     *
     * @return config instance 配置实例
     */
    public static Config getAppConfig() {
        return getConfig(ConfigConsts.NAMESPACE_APPLICATION);
    }

    /**
     * 获取指定命名空间的配置实例
     * Get the config instance for the namespace.
     *
     * @param namespace the namespace of the config 命名空间
     * @return config instance 指定命名空间的配置实例
     */
    public static Config getConfig(String namespace) {
        return INSTANCE.getManager().getConfig(namespace);
    }

    /**
     * 获取配置文件
     *
     * @param namespace        命名空间
     * @param configFileFormat 配置文件格式
     * @return 配置文件
     */
    public static ConfigFile getConfigFile(String namespace, ConfigFileFormat configFileFormat) {
        return INSTANCE.getManager().getConfigFile(namespace, configFileFormat);
    }

    /**
     * 设置默认命名空间的配置
     *
     * @param config 配置
     */
    static void setConfig(Config config) {
        setConfig(ConfigConsts.NAMESPACE_APPLICATION, config);
    }

    /**
     * 对指定命名空间设置配置
     * <p>
     * 在 Apollo 的设计中，ConfigManager 不允许设置 Namespace 对应的 Config 对象，
     * 而是通过 ConfigFactory 统一创建，虽然此时的创建是假的，直接返回了 config 方法参数。
     * <p>
     * Manually set the config for the namespace specified, use with caution.
     *
     * @param namespace the namespace 命名空间
     * @param config    the config instance 配置
     */
    static void setConfig(String namespace, final Config config) {
        INSTANCE.getRegistry().register(namespace, new ConfigFactory() {
            @Override
            public Config create(String namespace) {
                return config;
            }

            @Override
            public ConfigFile createConfigFile(String namespace, ConfigFileFormat configFileFormat) {
                return null;
            }

        });
    }

    /**
     * 设置默认命名空间的配置工厂
     *
     * @param factory 配置工厂
     */
    static void setConfigFactory(ConfigFactory factory) {
        setConfigFactory(ConfigConsts.NAMESPACE_APPLICATION, factory);
    }

    /**
     * 设置指定命名空间的配置工厂
     * Manually set the config factory for the namespace specified, use with caution.
     *
     * @param namespace the namespace
     * @param factory   the factory instance
     */
    static void setConfigFactory(String namespace, ConfigFactory factory) {
        INSTANCE.getRegistry().register(namespace, factory);
    }

    /**
     * 重置配置管理和注册中心，仅供测试使用
     */
    static void reset() {
        synchronized (INSTANCE) {
            INSTANCE.configManager = null;
            INSTANCE.configRegistry = null;
        }
    }
}
