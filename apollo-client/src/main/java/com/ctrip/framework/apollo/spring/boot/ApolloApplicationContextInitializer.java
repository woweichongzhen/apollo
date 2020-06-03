package com.ctrip.framework.apollo.spring.boot;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.spring.config.ConfigPropertySourceFactory;
import com.ctrip.framework.apollo.spring.config.PropertySourcesConstants;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.ctrip.framework.apollo.util.factory.PropertiesFactory;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.List;

/**
 * 初始化apollo系统属性，并注入apollo配置到 spring boot 启动阶段
 * <p>
 * Initialize apollo system properties and inject the Apollo config in Spring Boot bootstrap phase
 *
 * <p>Configuration example:</p>
 * <pre class="code">
 *   # set app.id
 *   app.id = 100004458
 *   # enable apollo bootstrap config and inject 'application' namespace in bootstrap phase
 *   apollo.bootstrap.enabled = true
 * </pre>
 * <p>
 * or
 *
 * <pre class="code">
 *   # set app.id
 *   app.id = 100004458
 *   # enable apollo bootstrap config
 *   apollo.bootstrap.enabled = true
 *   # will inject 'application' and 'FX.apollo' namespaces in bootstrap phase
 *   apollo.bootstrap.namespaces = application,FX.apollo
 * </pre>
 * <p>
 * <p>
 * If you want to load Apollo configurations even before Logging System Initialization Phase,
 * add
 * <pre class="code">
 *   # set apollo.bootstrap.eagerLoad.enabled
 *   apollo.bootstrap.eagerLoad.enabled = true
 * </pre>
 * <p>
 * This would be very helpful when your logging configurations is set by Apollo.
 * <p>
 * for example, you have defined logback-spring.xml in your project, and you want to inject some attributes into
 * logback-spring.xml.
 */
public class ApolloApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext>, EnvironmentPostProcessor, Ordered {

    public static final int DEFAULT_ORDER = 0;

    private static final Logger LOGGER = LoggerFactory.getLogger(ApolloApplicationContextInitializer.class);

    private static final Splitter NAMESPACE_SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();

    private static final String[] APOLLO_SYSTEM_PROPERTIES = {
            "app.id",
            ConfigConsts.APOLLO_CLUSTER_KEY,
            "apollo.cacheDir",
            "apollo.accesskey.secret",
            ConfigConsts.APOLLO_META_KEY,
            PropertiesFactory.APOLLO_PROPERTY_ORDER_ENABLE};

    private final ConfigPropertySourceFactory configPropertySourceFactory =
            SpringInjector.getInstance(ConfigPropertySourceFactory.class);

    private int order = DEFAULT_ORDER;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();

        // 获取apollo是否在启动阶段开启
        if (!environment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED, Boolean.class, false)) {
            LOGGER.debug("Apollo bootstrap config is not enabled for context {}, see property: ${{}}", context,
                    PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED);
            return;
        }
        LOGGER.debug("Apollo bootstrap config is enabled for context {}", context);

        // 初始化apollo配置，在环境准备好后
        this.initialize(environment);
    }


    /**
     * 初始化apollo配置，在环境准备好后
     *
     * @param environment 环境
     */
    protected void initialize(ConfigurableEnvironment environment) {
        // 如果包含初始化属性源名，则已经初始化过
        if (environment.getPropertySources().contains(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
            return;
        }

        // 获取启动阶段要初始化的命名空间
        String namespaces = environment.getProperty(
                PropertySourcesConstants.APOLLO_BOOTSTRAP_NAMESPACES,
                ConfigConsts.NAMESPACE_APPLICATION);
        LOGGER.debug("Apollo bootstrap namespaces: {}", namespaces);
        List<String> namespaceList = NAMESPACE_SPLITTER.splitToList(namespaces);

        // 组装这些命名空间的属性源，添加到属性工厂中（并缓存）
        CompositePropertySource composite =
                new CompositePropertySource(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME);
        for (String namespace : namespaceList) {
            Config config = ConfigService.getConfig(namespace);

            composite.addPropertySource(configPropertySourceFactory.getConfigPropertySource(namespace, config));
        }

        // 添加为第一个属性源
        environment.getPropertySources().addFirst(composite);
    }

    /**
     * 前置处理环境
     * <p>
     * 为了早在Spring加载日志记录系统阶段之前就加载Apollo配置，
     * 在ConfigFileApplicationListener成功之后，直接加载EnvironmentPostProcessor。
     * <p>
     * 处理顺序如下：
     * 加载Bootstrap属性和应用程序属性----->加载Apollo配置属性----> 初始化日志记录系统
     * <p>
     * In order to load Apollo configurations as early as even before Spring loading logging system phase,
     * this EnvironmentPostProcessor can be called Just After ConfigFileApplicationListener has succeeded.
     * <p>
     * <br />
     * The processing sequence would be like this: <br />
     * Load Bootstrap properties and application properties -----> load Apollo configuration properties ---->
     * Initialize Logging systems
     *
     * @param configurableEnvironment 环境变量
     * @param springApplication       spring应用
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment configurableEnvironment,
                                       SpringApplication springApplication) {
        // 应该总是初始化系统属性，比如 app.id 到第一个位置
        initializeSystemProperty(configurableEnvironment);

        // 是否急切启动apollo
        Boolean eagerLoadEnabled = configurableEnvironment.getProperty(
                PropertySourcesConstants.APOLLO_BOOTSTRAP_EAGER_LOAD_ENABLED,
                Boolean.class,
                false);

        // 如果不想在日志系统之前加载Apollo，则不应触发EnvironmentPostProcessor
        if (!eagerLoadEnabled) {
            return;
        }

        // 如果应用启动时初始化，则在前置处理完环境后，直接初始化apollo配置
        Boolean bootstrapEnabled = configurableEnvironment.getProperty(
                PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED,
                Boolean.class,
                false);

        if (bootstrapEnabled) {
            initialize(configurableEnvironment);
        }

    }

    /**
     * 完成系统属性配置
     */
    void initializeSystemProperty(ConfigurableEnvironment environment) {
        for (String propertyName : APOLLO_SYSTEM_PROPERTIES) {
            fillSystemPropertyFromEnvironment(environment, propertyName);
        }
    }

    /**
     * 填充系统属性
     *
     * @param environment  环境
     * @param propertyName 属性名
     */
    private void fillSystemPropertyFromEnvironment(ConfigurableEnvironment environment, String propertyName) {
        if (System.getProperty(propertyName) != null) {
            return;
        }

        String propertyValue = environment.getProperty(propertyName);

        if (Strings.isNullOrEmpty(propertyValue)) {
            return;
        }

        System.setProperty(propertyName, propertyValue);
    }

    /**
     * @since 1.3.0
     */
    @Override
    public int getOrder() {
        return order;
    }

    /**
     * @since 1.3.0
     */
    public void setOrder(int order) {
        this.order = order;
    }
}
