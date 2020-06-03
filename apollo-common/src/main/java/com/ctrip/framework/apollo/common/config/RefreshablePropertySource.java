package com.ctrip.framework.apollo.common.config;

import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * 抽象的属性源，实现刷新方法
 */
public abstract class RefreshablePropertySource extends MapPropertySource {

    public RefreshablePropertySource(String name, Map<String, Object> source) {
        super(name, source);
    }

    @Override
    public Object getProperty(String name) {
        return source.get(name);
    }

    /**
     * 用于重写刷新属性方法
     * refresh property
     */
    protected abstract void refresh();

}
