package com.ctrip.framework.apollo.spi;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigFile;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;

/**
 * 配置工厂接口
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigFactory {
    /**
     * 创建指定命名空间的配置
     * Create the config instance for the namespace.
     *
     * @param namespace the namespace
     * @return the newly created config instance
     */
    Config create(String namespace);

    /**
     * 创建指定命名空间的配置文件
     * Create the config file instance for the namespace
     *
     * @param namespace the namespace
     * @return the newly created config file instance
     */
    ConfigFile createConfigFile(String namespace, ConfigFileFormat configFileFormat);
}
