package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigFile;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.spi.ConfigFactory;
import com.ctrip.framework.apollo.spi.ConfigFactoryManager;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * 默认的配置文件管理器
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfigManager implements ConfigManager {

    /**
     * 配置工厂管理
     */
    private final ConfigFactoryManager configFactoryManager;

    /**
     * 配置缓存
     * key：命名空间
     * value：配置实例
     */
    private final Map<String, Config> configs = Maps.newConcurrentMap();

    /**
     * 配置文件缓存
     * key：命名空间
     * value：配置文件实例
     */
    private final Map<String, ConfigFile> configFiles = Maps.newConcurrentMap();

    public DefaultConfigManager() {
        configFactoryManager = ApolloInjector.getInstance(ConfigFactoryManager.class);
    }

    @Override
    public Config getConfig(String namespace) {
        Config config = configs.get(namespace);

        // 缓存中没有，双重自检索，从配置工厂管理器中获取配置工厂，配置工厂根据命名空间创建配置对象
        if (config == null) {
            synchronized (this) {
                config = configs.get(namespace);

                if (config == null) {
                    ConfigFactory factory = configFactoryManager.getFactory(namespace);

                    config = factory.create(namespace);
                    configs.put(namespace, config);
                }
            }
        }

        return config;
    }

    @Override
    public ConfigFile getConfigFile(String namespace, ConfigFileFormat configFileFormat) {
        // 格式化命名空间文件名
        String namespaceFileName = String.format("%s.%s", namespace, configFileFormat.getValue());
        ConfigFile configFile = configFiles.get(namespaceFileName);

        // 缓存中没有，双重自检索，从配置工厂管理器中获取配置工厂，配置工厂根据命名空间创建配置文件对象
        if (configFile == null) {
            synchronized (this) {
                configFile = configFiles.get(namespaceFileName);

                if (configFile == null) {
                    ConfigFactory factory = configFactoryManager.getFactory(namespaceFileName);

                    configFile = factory.createConfigFile(namespaceFileName, configFileFormat);
                    configFiles.put(namespaceFileName, configFile);
                }
            }
        }

        return configFile;
    }
}
