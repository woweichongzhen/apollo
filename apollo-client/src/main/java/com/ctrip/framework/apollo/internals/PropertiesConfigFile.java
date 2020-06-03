package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.core.utils.PropertiesUtil;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.util.ExceptionUtil;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 属性配置文件
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class PropertiesConfigFile extends AbstractConfigFile {

    /**
     * 文件内容字符串引用
     */
    protected AtomicReference<String> contentCache;

    public PropertiesConfigFile(String namespace,
                                ConfigRepository configRepository) {
        super(namespace, configRepository);
        contentCache = new AtomicReference<>();
    }

    @Override
    protected void update(Properties newProperties) {
        configProperties.set(newProperties);
        contentCache.set(null);
    }

    @Override
    public String getContent() {
        // 获取时再加载缓存
        if (contentCache.get() == null) {
            contentCache.set(doGetContent());
        }
        return contentCache.get();
    }

    /**
     * 获取内容
     *
     * @return 属性文件内容字符串
     */
    String doGetContent() {
        // 无内容，即无属性
        if (!this.hasContent()) {
            return null;
        }

        try {
            // 去除第一行注释，转换属性内容为字符串
            return PropertiesUtil.toString(configProperties.get());
        } catch (Throwable ex) {
            ApolloConfigException exception =
                    new ApolloConfigException(String
                            .format("Parse properties file content failed for namespace: %s, cause: %s",
                                    namespace, ExceptionUtil.getDetailMessage(ex)));
            Tracer.logError(exception);
            throw exception;
        }
    }

    @Override
    public boolean hasContent() {
        return configProperties.get() != null
                && !configProperties.get().isEmpty();
    }

    @Override
    public ConfigFileFormat getConfigFileFormat() {
        return ConfigFileFormat.Properties;
    }

}
