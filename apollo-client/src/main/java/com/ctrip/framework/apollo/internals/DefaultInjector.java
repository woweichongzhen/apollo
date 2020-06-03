package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.spi.*;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.factory.DefaultPropertiesFactory;
import com.ctrip.framework.apollo.util.factory.PropertiesFactory;
import com.ctrip.framework.apollo.util.http.HttpUtil;
import com.ctrip.framework.apollo.util.yaml.YamlParser;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Singleton;

/**
 * 默认注入器，可通过spi配置
 * guice的注入方法
 * <p>
 * Guice injector
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultInjector implements Injector {

    /**
     * 指定了接口和实现类的注入类
     */
    private final com.google.inject.Injector injector;

    public DefaultInjector() {
        try {
            injector = Guice.createInjector(new ApolloModule());
        } catch (Throwable ex) {
            ApolloConfigException exception = new ApolloConfigException("Unable to initialize Guice Injector!", ex);
            Tracer.logError(exception);
            throw exception;
        }
    }

    @Override
    public <T> T getInstance(Class<T> clazz) {
        try {
            return injector.getInstance(clazz);
        } catch (Throwable ex) {
            Tracer.logError(ex);
            throw new ApolloConfigException(
                    String.format("Unable to load instance for %s!", clazz.getName()), ex);
        }
    }

    @Override
    public <T> T getInstance(Class<T> clazz, String name) {
        //Guice does not support get instance by type and name
        return null;
    }

    /**
     * 需要guice注入的apoolo模块
     * 配置默认实现类，单例还是其他类型
     */
    private static class ApolloModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(ConfigManager.class).to(DefaultConfigManager.class).in(Singleton.class);
            bind(ConfigFactoryManager.class).to(DefaultConfigFactoryManager.class).in(Singleton.class);
            bind(ConfigRegistry.class).to(DefaultConfigRegistry.class).in(Singleton.class);
            bind(ConfigFactory.class).to(DefaultConfigFactory.class).in(Singleton.class);
            bind(ConfigUtil.class).in(Singleton.class);
            bind(HttpUtil.class).in(Singleton.class);
            bind(ConfigServiceLocator.class).in(Singleton.class);
            bind(RemoteConfigLongPollService.class).in(Singleton.class);
            bind(YamlParser.class).in(Singleton.class);
            bind(PropertiesFactory.class).to(DefaultPropertiesFactory.class).in(Singleton.class);
        }
    }
}
