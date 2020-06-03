package com.ctrip.framework.apollo.build;

import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.internals.Injector;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.foundation.internals.ServiceBootstrap;

/**
 * apollo 实例注入工具
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ApolloInjector {

    /**
     * 注入接口
     */
    private static volatile Injector injector;

    /**
     * 注入锁
     */
    private static final Object LOCK = new Object();

    /**
     * volatile+synchronized获取注入实例
     *
     * @return 注入实例
     */
    private static Injector getInjector() {
        if (injector == null) {
            synchronized (LOCK) {
                if (injector == null) {
                    try {
                        // 基于 JDK SPI 加载对应的 Injector 实现对象
                        injector = ServiceBootstrap.loadFirst(Injector.class);
                    } catch (Throwable ex) {
                        ApolloConfigException exception = new ApolloConfigException("Unable to initialize Apollo " +
                                "Injector!", ex);
                        Tracer.logError(exception);
                        throw exception;
                    }
                }
            }
        }

        return injector;
    }

    /**
     * 获取实例
     */
    public static <T> T getInstance(Class<T> clazz) {
        try {
            return getInjector().getInstance(clazz);
        } catch (Throwable ex) {
            Tracer.logError(ex);
            throw new ApolloConfigException(String.format("Unable to load instance for type %s!", clazz.getName()), ex);
        }
    }

    /**
     * 获取实例
     */
    public static <T> T getInstance(Class<T> clazz, String name) {
        try {
            return getInjector().getInstance(clazz, name);
        } catch (Throwable ex) {
            Tracer.logError(ex);
            throw new ApolloConfigException(
                    String.format("Unable to load instance for type %s and name %s !", clazz.getName(), name), ex);
        }
    }
}
