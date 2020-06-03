package com.ctrip.framework.apollo.util.factory;

import java.util.Properties;

/**
 * 属性工厂，用于构造属性实例
 *
 * @author songdragon@zts.io
 */
public interface PropertiesFactory {

    /**
     * apollo属性配置顺序启用
     * 配置以使属性顺序与 .yml / .yaml / .properties 文件中的行顺序相同
     */
    String APOLLO_PROPERTY_ORDER_ENABLE = "apollo.property.order.enable";

    /**
     * 获取属性实例
     * 如果开始属性顺序，使用自定义的顺序属性类
     * 否则使用JDK自带的属性
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
