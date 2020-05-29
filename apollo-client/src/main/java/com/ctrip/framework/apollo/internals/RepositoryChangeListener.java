package com.ctrip.framework.apollo.internals;

import java.util.Properties;

/**
 * 仓库改变监听器
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface RepositoryChangeListener {

    /**
     * 当仓库配置改变时，回调
     * Invoked when config repository changes.
     *
     * @param namespace     the namespace of this repository change
     * @param newProperties the properties after change
     */
    void onRepositoryChange(String namespace, Properties newProperties);
}
