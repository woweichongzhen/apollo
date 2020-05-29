package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.enums.ConfigSourceType;

import java.util.Properties;

/**
 * 配置仓库接口，用于获取配置
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigRepository {

    /**
     * 读取配置
     * Get the config from this repository.
     *
     * @return config
     */
    Properties getConfig();

    /**
     * 设置负载均衡存储库，即后备存储库
     * Set the fallback repo for this repository.
     *
     * @param upstreamConfigRepository the upstream repo
     */
    void setUpstreamRepository(ConfigRepository upstreamConfigRepository);

    /**
     * 添加改变监听
     * Add change listener.
     *
     * @param listener the listener to observe the changes
     */
    void addChangeListener(RepositoryChangeListener listener);

    /**
     * 移除改变监听
     * Remove change listener.
     *
     * @param listener the listener to remove
     */
    void removeChangeListener(RepositoryChangeListener listener);

    /**
     * 获取配置源类型，即配置从哪里来
     * Return the config's source type, i.e. where is the config loaded from
     *
     * @return the config's source type
     */
    ConfigSourceType getSourceType();
}
