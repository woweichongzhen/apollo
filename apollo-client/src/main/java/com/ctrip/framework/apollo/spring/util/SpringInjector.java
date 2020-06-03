package com.ctrip.framework.apollo.spring.util;

import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.spring.config.ConfigPropertySourceFactory;
import com.ctrip.framework.apollo.spring.property.PlaceholderHelper;
import com.ctrip.framework.apollo.spring.property.SpringValueRegistry;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;

/**
 * spring注入工具类
 */
public class SpringInjector {

    private static volatile Injector injector;

    /**
     * springspring模块的注入器
     */
    private static final Object LOCK = new Object();

    /**
     * 获取spring模块的注入器
     * volatile+双重自检索实现
     *
     * @return spring模块的注入器
     */
    private static Injector getInjector() {
        if (injector == null) {
            synchronized (LOCK) {
                if (injector == null) {
                    try {
                        injector = Guice.createInjector(new SpringModule());
                    } catch (Throwable ex) {
                        ApolloConfigException exception = new ApolloConfigException("Unable to initialize Apollo " +
                                "Spring Injector!", ex);
                        Tracer.logError(exception);
                        throw exception;
                    }
                }
            }
        }

        return injector;
    }

    /**
     * 获取实例对象
     *
     * @param clazz 目标类
     * @param <T>   目标类型
     * @return 实例对象
     */
    public static <T> T getInstance(Class<T> clazz) {
        try {
            return getInjector().getInstance(clazz);
        } catch (Throwable ex) {
            Tracer.logError(ex);
            throw new ApolloConfigException(
                    String.format("Unable to load instance for %s!", clazz.getName()), ex);
        }
    }

    /**
     * google的guice注入依赖的spring模块
     */
    private static class SpringModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(PlaceholderHelper.class).in(Singleton.class);
            bind(ConfigPropertySourceFactory.class).in(Singleton.class);
            bind(SpringValueRegistry.class).in(Singleton.class);
        }
    }
}
