package com.ctrip.framework.apollo.spring.config;

import com.ctrip.framework.apollo.spring.spi.ConfigPropertySourcesProcessorHelper;
import com.ctrip.framework.foundation.internals.ServiceBootstrap;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

/**
 * apollo xml 应用的属性源处理器
 * <p>
 * Apollo Property Sources processor for Spring XML Based Application
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConfigPropertySourcesProcessor extends PropertySourcesProcessor implements BeanDefinitionRegistryPostProcessor {

    private final ConfigPropertySourcesProcessorHelper helper =
            ServiceBootstrap.loadPrimary(ConfigPropertySourcesProcessorHelper.class);

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // 交给辅助类来 前置处理 bean定义注册
        helper.postProcessBeanDefinitionRegistry(registry);
    }
}
