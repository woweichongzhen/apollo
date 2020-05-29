package com.ctrip.framework.apollo.core;

/**
 * 配置常量
 */
public interface ConfigConsts {

    /**
     * 默认命名空间application
     */
    String NAMESPACE_APPLICATION = "application";

    /**
     * 默认集群名
     */
    String CLUSTER_NAME_DEFAULT = "default";

    /**
     * +号分割
     */
    String CLUSTER_NAMESPACE_SEPARATOR = "+";
    String APOLLO_CLUSTER_KEY = "apollo.cluster";
    String APOLLO_META_KEY = "apollo.meta";

    /**
     * yaml、yml、json、xml 格式的内容id，只有一条配置
     */
    String CONFIG_FILE_CONTENT_KEY = "content";

    /**
     * apollo无应用编号替代者
     */
    String NO_APPID_PLACEHOLDER = "ApolloNoAppIdPlaceHolder";

    /**
     * 通知id替代符号
     */
    long NOTIFICATION_ID_PLACEHOLDER = -1;
}
