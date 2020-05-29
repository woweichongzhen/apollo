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
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfigFactory implements ConfigFactory {
    private static final Logger logger = LoggerFactory.getLogger(DefaultConfigFactory.class);
    private ConfigUtil m_configUtil;

    public DefaultConfigFactory() {
        m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
    }

    @Override
    public Config create(String namespace) {
        ConfigFileFormat format = determineFileFormat(namespace);
        if (ConfigFileFormat.isPropertiesCompatible(format)) {
            return new DefaultConfig(namespace, createPropertiesCompatibleFileConfigRepository(namespace, format));
        }
        return new DefaultConfig(namespace, createLocalConfigRepository(namespace));
    }

    @Override
    public ConfigFile createConfigFile(String namespace, ConfigFileFormat configFileFormat) {
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
        // 如果开启仅本地模式，只创建一个
        if (m_configUtil.isInLocalMode()) {
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

    PropertiesCompatibleFileConfigRepository createPropertiesCompatibleFileConfigRepository(String namespace,
                                                                                            ConfigFileFormat format) {
        String actualNamespaceName = trimNamespaceFormat(namespace, format);
        PropertiesCompatibleConfigFile configFile = (PropertiesCompatibleConfigFile) ConfigService
                .getConfigFile(actualNamespaceName, format);

        return new PropertiesCompatibleFileConfigRepository(configFile);
    }

    // for namespaces whose format are not properties, the file extension must be present, e.g. application.yaml
    ConfigFileFormat determineFileFormat(String namespaceName) {
        String lowerCase = namespaceName.toLowerCase();
        for (ConfigFileFormat format : ConfigFileFormat.values()) {
            if (lowerCase.endsWith("." + format.getValue())) {
                return format;
            }
        }

        return ConfigFileFormat.Properties;
    }

    String trimNamespaceFormat(String namespaceName, ConfigFileFormat format) {
        String extension = "." + format.getValue();
        if (!namespaceName.toLowerCase().endsWith(extension)) {
            return namespaceName;
        }

        return namespaceName.substring(0, namespaceName.length() - extension.length());
    }

}
