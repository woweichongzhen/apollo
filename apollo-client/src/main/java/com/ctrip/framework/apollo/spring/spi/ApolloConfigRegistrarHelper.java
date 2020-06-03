package com.ctrip.framework.apollo.spring.spi;

import com.ctrip.framework.apollo.core.spi.Ordered;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.type.AnnotationMetadata;

/**
 * java配置的apollo配置注册辅助类
 */
public interface ApolloConfigRegistrarHelper extends Ordered {

    /**
     * 注册bean定义
     *
     * @param importingClassMetadata 导入的类注解元数据
     * @param registry               bean定义注册中心
     */
    void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry);
}
