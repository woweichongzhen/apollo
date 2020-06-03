package com.ctrip.framework.apollo.spring.property;

/**
 * spring xml属性定义封装
 * <p>
 * 比如对应下面一行
 * <property name="batch" value="${batch:100}"/>
 */
public class SpringValueDefinition {

    /**
     * xml中的属性key
     * 比如 ${batch:100} 中的 batch
     */
    private final String key;

    /**
     * xml中的占位符
     * 比如 ${batch:100} 中的 batch:100
     */
    private final String placeholder;

    /**
     * xml中的属性名
     * 比如 name="batch" 中的 batch
     */
    private final String propertyName;

    public SpringValueDefinition(String key, String placeholder, String propertyName) {
        this.key = key;
        this.placeholder = placeholder;
        this.propertyName = propertyName;
    }

    public String getKey() {
        return key;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
