package com.ctrip.framework.apollo.configservice.wrapper;

import java.util.Map;

/**
 * 委托包装，内部一个map
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class CaseInsensitiveMapWrapper<T> {
    private final Map<String, T> delegate;

    public CaseInsensitiveMapWrapper(Map<String, T> delegate) {
        this.delegate = delegate;
    }

    /**
     * 转换为小写后统一获取
     *
     * @param key 小写key
     * @return 委托对象
     */
    public T get(String key) {
        return delegate.get(key.toLowerCase());
    }

    public T put(String key, T value) {
        return delegate.put(key.toLowerCase(), value);
    }

    public T remove(String key) {
        return delegate.remove(key.toLowerCase());
    }
}
