package com.ctrip.framework.apollo.spring.annotation;

import com.ctrip.framework.apollo.spring.spi.ApolloConfigRegistrarHelper;
import com.ctrip.framework.foundation.internals.ServiceBootstrap;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * java配置的apollo配置注册
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ApolloConfigRegistrar implements ImportBeanDefinitionRegistrar {

    private final ApolloConfigRegistrarHelper helper = ServiceBootstrap.loadPrimary(ApolloConfigRegistrarHelper.class);

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        helper.registerBeanDefinitions(importingClassMetadata, registry);
    }
}
