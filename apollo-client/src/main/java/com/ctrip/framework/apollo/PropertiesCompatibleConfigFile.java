package com.ctrip.framework.apollo;

import java.util.Properties;

/**
 * 属性兼容的配置文件
 * 比如yaml，yaml
 *
 * @since 1.3.0
 */
public interface PropertiesCompatibleConfigFile extends ConfigFile {

    /**
     * 转换为属性
     *
     * @return the properties form of the config file
     * @throws RuntimeException if the content could not be transformed to properties
     */
    Properties asProperties();
}
