package com.ctrip.framework.apollo.spring.spi;

import com.ctrip.framework.apollo.core.spi.Ordered;
import com.ctrip.framework.apollo.spring.annotation.ApolloAnnotationProcessor;
import com.ctrip.framework.apollo.spring.annotation.ApolloJsonValueProcessor;
import com.ctrip.framework.apollo.spring.annotation.SpringValueProcessor;
import com.ctrip.framework.apollo.spring.property.SpringValueDefinitionProcessor;
import com.ctrip.framework.apollo.spring.util.BeanRegistrationUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import java.util.HashMap;
import java.util.Map;

/**
 * xml配置的默认配置属性源工具类
 * <p>
 * Apollo 为了实现自动更新机制，做了很多处理，
 * 重点在于找到 XML 和注解配置的 PlaceHolder，
 * 全部以 StringValue 的形式，注册到 SpringValueRegistry 中，
 * 从而让 AutoUpdateConfigChangeListener 监听到 Apollo 配置变更后，
 * 能够从 SpringValueRegistry 中找到发生属性值变更的属性对应的 StringValue ，进行修改
 */
public class DefaultConfigPropertySourcesProcessorHelper implements ConfigPropertySourcesProcessorHelper {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // 属性源占位符
        Map<String, Object> propertySourcesPlaceholderPropertyValues = new HashMap<>();
        // 确保默认的PropertySourcesPlaceholderConfigurer的优先级高于 PropertyPlaceholderConfigurer
        propertySourcesPlaceholderPropertyValues.put("order", 0);

        // 注册属性源占位符配置，替换 PlaceHolder 为对应的属性值
        BeanRegistrationUtil.registerBeanDefinitionIfNotExists(
                registry,
                PropertySourcesPlaceholderConfigurer.class.getName(),
                PropertySourcesPlaceholderConfigurer.class,
                propertySourcesPlaceholderPropertyValues);

        // 注册 @ApolloConfig 和 @ApolloConfigChangeListener 注解的处理器
        BeanRegistrationUtil.registerBeanDefinitionIfNotExists(
                registry,
                ApolloAnnotationProcessor.class.getName(),
                ApolloAnnotationProcessor.class);

        // 注册 StringValue 处理器，用于 PlaceHolder 自动更新机制
        BeanRegistrationUtil.registerBeanDefinitionIfNotExists(
                registry,
                SpringValueProcessor.class.getName(),
                SpringValueProcessor.class);

        // 注册 @ApolloJsonValue 注解处理器
        BeanRegistrationUtil.registerBeanDefinitionIfNotExists(
                registry,
                ApolloJsonValueProcessor.class.getName(),
                ApolloJsonValueProcessor.class);

        // 处理spring值定义
        processSpringValueDefinition(registry);
    }

    /**
     * 处理 XML 配置的 Spring PlaceHolder 。这块的目的，也是用于 PlaceHolder 自动更新机制
     * <p>
     * 对于Spring 3.x版本，在 postProcessBeanDefinitionRegistry 阶段，
     * 实现了 BeanDefinitionRegistryPostProcessor 接口的 SpringValueDefinitionProcessor 无法被实例化
     * <p>
     * 因此我们必须在此处手动创建 SpringValueDefinitionProcessor 对象，并调用它的 postProcessBeanDefinitionRegistry 方法
     * <p>
     * For Spring 3.x versions, the BeanDefinitionRegistryPostProcessor would not be instantiated if
     * it is added in postProcessBeanDefinitionRegistry phase, so we have to manually call the
     * postProcessBeanDefinitionRegistry method of SpringValueDefinitionProcessor here...
     */
    private void processSpringValueDefinition(BeanDefinitionRegistry registry) {
        SpringValueDefinitionProcessor springValueDefinitionProcessor = new SpringValueDefinitionProcessor();
        // 处理xml配置的spring 占位符
        springValueDefinitionProcessor.postProcessBeanDefinitionRegistry(registry);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
