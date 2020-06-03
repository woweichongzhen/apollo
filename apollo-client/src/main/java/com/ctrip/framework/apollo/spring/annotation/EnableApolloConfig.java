package com.ctrip.framework.apollo.spring.annotation;

import com.ctrip.framework.apollo.core.ConfigConsts;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import java.lang.annotation.*;

/**
 * 启用apollo配置，当使用Java配置时
 * Use this annotation to register Apollo property sources when using Java Config.
 *
 * <p>Configuration example:</p>
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableApolloConfig({"someNamespace","anotherNamespace"})
 * public class AppConfig {
 *
 * }
 * </pre>
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(ApolloConfigRegistrar.class)
public @interface EnableApolloConfig {
    /**
     * 启用配置的命名空间
     * Apollo namespaces to inject configuration into Spring Property Sources.
     */
    String[] value() default {ConfigConsts.NAMESPACE_APPLICATION};

    /**
     * apollo配置的优先级，如果有同样名称的属性，按优先级覆盖
     * The order of the apollo config, default is {@link Ordered#LOWEST_PRECEDENCE}, which is Integer.MAX_VALUE.
     * If there are properties with the same name in different apollo configs, the apollo config with smaller order
     * wins.
     *
     * @return 排序，越小越优先
     */
    int order() default Ordered.LOWEST_PRECEDENCE;
}
