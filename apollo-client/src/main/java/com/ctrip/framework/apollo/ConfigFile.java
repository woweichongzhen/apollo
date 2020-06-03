package com.ctrip.framework.apollo;

import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.enums.ConfigSourceType;

/**
 * 配置文件接口
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigFile {
    /**
     * 获取命名空间的文件内容
     * Get file content of the namespace
     *
     * @return file content, {@code null} if there is no content
     */
    String getContent();

    /**
     * 文件是否包含内容
     * Whether the config file has any content
     *
     * @return true if it has content, false otherwise.
     */
    boolean hasContent();

    /**
     * 获取命名空间
     * Get the namespace of this config file instance
     *
     * @return the namespace
     */
    String getNamespace();

    /**
     * 获取文件格式
     * Get the file format of this config file instance
     *
     * @return the config file format enum
     */
    ConfigFileFormat getConfigFileFormat();

    /**
     * 添加配置文件状态改变监听器
     * Add change listener to this config file instance.
     *
     * @param listener the config file change listener
     */
    void addChangeListener(ConfigFileChangeListener listener);

    /**
     * 移除配置文件状态改变监听器
     * Remove the change listener
     *
     * @param listener the specific config change listener to remove
     * @return true if the specific config change listener is found and removed
     */
    boolean removeChangeListener(ConfigFileChangeListener listener);

    /**
     * 配置来源类型
     * Return the config's source type, i.e. where is the config loaded from
     *
     * @return the config's source type
     */
    ConfigSourceType getSourceType();
}
