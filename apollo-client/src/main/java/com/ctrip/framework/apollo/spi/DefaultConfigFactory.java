package com.ctrip.framework.apollo.spi;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigFile;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.PropertiesCompatibleConfigFile;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.internals.*;
import com.ctrip.framework.apollo.util.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认的配置工厂
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfigFactory implements ConfigFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultConfigFactory.class);

    /**
     * 配置工具
     */
    private final ConfigUtil configUtil;

    public DefaultConfigFactory() {
        configUtil = ApolloInjector.getInstance(ConfigUtil.class);
    }

    @Override
    public Config create(String namespace) {
        // 根据命名空间，决定文件格式，根据是否为 properties文件，决定创建的配置
        ConfigFileFormat format = determineFileFormat(namespace);
        if (ConfigFileFormat.isPropertiesCompatible(format)) {
            return new DefaultConfig(namespace, createPropertiesCompatibleFileConfigRepository(namespace, format));
        }
        return new DefaultConfig(namespace, createLocalConfigRepository(namespace));
    }

    @Override
    public ConfigFile createConfigFile(String namespace, ConfigFileFormat configFileFormat) {
        // 创建本地配置文件仓库
        ConfigRepository configRepository = createLocalConfigRepository(namespace);
        switch (configFileFormat) {
            case Properties:
                return new PropertiesConfigFile(namespace, configRepository);
            case XML:
                return new XmlConfigFile(namespace, configRepository);
            case JSON:
                return new JsonConfigFile(namespace, configRepository);
            case YAML:
                return new YamlConfigFile(namespace, configRepository);
            case YML:
                return new YmlConfigFile(namespace, configRepository);
            case TXT:
                return new TxtConfigFile(namespace, configRepository);
            default:
                break;
        }

        return null;
    }

    /**
     * 创建本地仓库
     *
     * @param namespace 命名空间
     * @return 本地文件仓库
     */
    LocalFileConfigRepository createLocalConfigRepository(String namespace) {
        // 如果开启仅本地模式，只创建一个本地配置文件仓库
        if (configUtil.isInLocalMode()) {
            logger.warn(
                    "==== Apollo is in local mode! Won't pull configs from remote server for namespace {} ! ====",
                    namespace);
            return new LocalFileConfigRepository(namespace);
        }
        // 否则用远程作为本地的备选
        return new LocalFileConfigRepository(namespace, createRemoteConfigRepository(namespace));
    }

    /**
     * 创建远程同步仓库
     *
     * @param namespace 命名空间
     * @return 远程配置仓库
     */
    RemoteConfigRepository createRemoteConfigRepository(String namespace) {
        return new RemoteConfigRepository(namespace);
    }

    /**
     * 创建属性文件的兼容配置仓库，即对配置文件又包裹了一层，可以把yaml文件转为属性
     *
     * @param namespace 命名空间
     * @param format    文件格式
     * @return 属性文件兼容配置仓库
     */
    PropertiesCompatibleFileConfigRepository createPropertiesCompatibleFileConfigRepository(String namespace,
                                                                                            ConfigFileFormat format) {
        // 去除尾缀
        String actualNamespaceName = trimNamespaceFormat(namespace, format);
        // 获取属性兼容的配置文件
        PropertiesCompatibleConfigFile configFile =
                (PropertiesCompatibleConfigFile) ConfigService.getConfigFile(actualNamespaceName, format);

        // 创建属性文件兼容的配置仓库
        return new PropertiesCompatibleFileConfigRepository(configFile);
    }

    /**
     * 根据命名空间获取文件格式，如果都不匹配，则为 properties
     *
     * @param namespaceName 命名空间
     * @return 文件格式
     */
    ConfigFileFormat determineFileFormat(String namespaceName) {
        String lowerCase = namespaceName.toLowerCase();
        for (ConfigFileFormat format : ConfigFileFormat.values()) {
            if (lowerCase.endsWith("." + format.getValue())) {
                return format;
            }
        }

        return ConfigFileFormat.Properties;
    }

    /**
     * 返回去除文件尾缀的命名空间名称
     *
     * @param namespaceName 命名空间名称
     * @param format        文件格式
     * @return 去除文件尾缀的命名空间名称
     */
    String trimNamespaceFormat(String namespaceName, ConfigFileFormat format) {
        String extension = "." + format.getValue();
        if (!namespaceName.toLowerCase().endsWith(extension)) {
            return namespaceName;
        }

        return namespaceName.substring(0, namespaceName.length() - extension.length());
    }

}
