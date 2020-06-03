package com.ctrip.framework.apollo.spring.annotation;

import com.ctrip.framework.apollo.core.ConfigConsts;

import java.lang.annotation.*;

/**
 * apollo配置注入到 Config 实例中，为了某个命名空间
 * <p>
 * Use this annotation to inject Apollo Config Instance.
 *
 * <p>Usage example:</p>
 * <pre class="code">
 * //Inject the config for "someNamespace"
 * &#064;ApolloConfig("someNamespace")
 * private Config config;
 * </pre>
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface ApolloConfig {
    /**
     * 该配置的命名空间
     * Apollo namespace for the config, if not specified then default to application
     */
    String value() default ConfigConsts.NAMESPACE_APPLICATION;
}
