package com.ctrip.framework.apollo.internals;

/**
 * 注入接口
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface Injector {

    /**
     * 获取指定类型的实例
     * Returns the appropriate instance for the given injection type
     */
    <T> T getInstance(Class<T> clazz);

    /**
     * 获取指定类型的实例
     * Returns the appropriate instance for the given injection type and name
     */
    <T> T getInstance(Class<T> clazz, String name);
}
