package com.ctrip.framework.apollo.spring.spi;

import com.ctrip.framework.apollo.core.spi.Ordered;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * xml配置的属性源处理器辅助类
 */
public interface ConfigPropertySourcesProcessorHelper extends Ordered {

    /**
     * 前置处理bean定义注册
     *
     * @param registry bean定义注册中心
     * @throws BeansException bean处理异常
     */
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException;
}
