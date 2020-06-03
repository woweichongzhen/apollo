package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigFile;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;

/**
 * 配置管理器
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigManager {

    /**
     * 获取配置实例
     * <p>
     * Get the config instance for the namespace specified.
     *
     * @param namespace the namespace 命名空间
     * @return the config instance for the namespace 指定命名空间的配置实例
     */
    Config getConfig(String namespace);

    /**
     * 获取配置文件实例
     * Get the config file instance for the namespace specified.
     *
     * @param namespace        the namespace 命名空间
     * @param configFileFormat the config file format 配置文件格式
     * @return the config file instance for the namespace 配置文件实例
     */
    ConfigFile getConfigFile(String namespace, ConfigFileFormat configFileFormat);
}
