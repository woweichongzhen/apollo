package com.ctrip.framework.apollo.spring.config;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigChangeListener;
import org.springframework.core.env.EnumerablePropertySource;

import java.util.Set;

/**
 * 配置属性源包裹配置
 * <p>
 * 下面的 source 对象就是{@link Config}
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConfigPropertySource extends EnumerablePropertySource<Config> {

    /**
     * 属性源名空数组
     */
    private static final String[] EMPTY_ARRAY = new String[0];

    /**
     * @param name   命名空间
     * @param source 配置对象
     */
    ConfigPropertySource(String name, Config source) {
        super(name, source);
    }

    @Override
    public String[] getPropertyNames() {
        // 获取该配置/属性源的属性名
        Set<String> propertyNames = source.getPropertyNames();
        if (propertyNames.isEmpty()) {
            return EMPTY_ARRAY;
        }
        return propertyNames.toArray(new String[propertyNames.size()]);
    }

    @Override
    public Object getProperty(String name) {
        // 获取该属性源的某个属性
        return source.getProperty(name, null);
    }

    /**
     * 添加属性源的配置改变监听器
     *
     * @param listener 配置改变监听器
     */
    public void addChangeListener(ConfigChangeListener listener) {
        source.addChangeListener(listener);
    }
}
