package com.ctrip.framework.apollo.spring.annotation;

import java.lang.annotation.*;

/**
 * 注入json属性，和 @Value 支持同样的格式
 * <p>
 * 将 Apollo 任意格式的 Namespace 的一个 Item 配置项，解析成对应类型的对象，注入到 @ApolloJsonValue 的对象中
 * <p>
 * Use this annotation to inject json property from Apollo, support the same format as Spring @Value.
 *
 * <p>Usage example:</p>
 * <pre class="code">
 * // Inject the json property value for type SomeObject.
 * // Suppose SomeObject has 2 properties, someString and someInt, then the possible config
 * // in Apollo is someJsonPropertyKey={"someString":"someValue", "someInt":10}.
 * &#064;ApolloJsonValue("${someJsonPropertyKey:someDefaultValue}")
 * private SomeObject someObject;
 * </pre>
 * <p>
 * Create by zhangzheng on 2018/3/6
 *
 * @see org.springframework.beans.factory.annotation.Value
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@Documented
public @interface ApolloJsonValue {

    /**
     * 实际的value表达式
     * The actual value expression: e.g. "${someJsonPropertyKey:someDefaultValue}".
     */
    String value();
}
