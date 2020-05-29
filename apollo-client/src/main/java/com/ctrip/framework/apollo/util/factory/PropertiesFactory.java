package com.ctrip.framework.apollo.util.factory;

import java.util.Properties;

/**
 * 属性工厂
 * Factory interface to construct Properties instances.
 *
 * @author songdragon@zts.io
 */
public interface PropertiesFactory {

    /**
     * apollo属性配置顺序启用
     * Configuration to keep properties order as same as line order in .yml/.yaml/.properties file.
     */
    String APOLLO_PROPERTY_ORDER_ENABLE = "apollo.property.order.enable";

    /**
     * 获取属性实例
     * <pre>
     * Default implementation:
     * 1. if {@link APOLLO_PROPERTY_ORDER_ENABLE} is true return a new
     * instance of {@link com.ctrip.framework.apollo.util.OrderedProperties}.
     * 2. else return a new instance of {@link Properties}
     * </pre>
     *
     * @return 属性实例
     */
    Properties getPropertiesInstance();
}
