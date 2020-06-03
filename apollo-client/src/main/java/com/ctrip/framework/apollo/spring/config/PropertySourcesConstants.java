package com.ctrip.framework.apollo.spring.config;

/**
 * 属性源常量
 */
public interface PropertySourcesConstants {

    /**
     * apollo属性源名
     */
    String APOLLO_PROPERTY_SOURCE_NAME = "ApolloPropertySources";

    /**
     * apollo外部化配置启动（初始化）属性源名
     */
    String APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME = "ApolloBootstrapPropertySources";

    /**
     * apollo最先启动
     */
    String APOLLO_BOOTSTRAP_ENABLED = "apollo.bootstrap.enabled";

    /**
     * apollo是否急切启动
     */
    String APOLLO_BOOTSTRAP_EAGER_LOAD_ENABLED = "apollo.bootstrap.eagerLoad.enabled";

    /**
     * apollo启动阶段初始化的命名空间
     */
    String APOLLO_BOOTSTRAP_NAMESPACES = "apollo.bootstrap.namespaces";
}
