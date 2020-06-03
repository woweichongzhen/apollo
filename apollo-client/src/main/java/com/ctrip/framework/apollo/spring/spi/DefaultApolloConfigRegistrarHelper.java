package com.ctrip.framework.apollo.spring.spi;

import com.ctrip.framework.apollo.core.spi.Ordered;
import com.ctrip.framework.apollo.spring.annotation.ApolloAnnotationProcessor;
import com.ctrip.framework.apollo.spring.annotation.ApolloJsonValueProcessor;
import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import com.ctrip.framework.apollo.spring.annotation.SpringValueProcessor;
import com.ctrip.framework.apollo.spring.config.PropertySourcesProcessor;
import com.ctrip.framework.apollo.spring.property.SpringValueDefinitionProcessor;
import com.ctrip.framework.apollo.spring.util.BeanRegistrationUtil;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import java.util.HashMap;
import java.util.Map;

/**
 * java配置的默认的apollo配置注册辅助类
 * {@link EnableApolloConfig} 注解处理器
 */
public class DefaultApolloConfigRegistrarHelper implements ApolloConfigRegistrarHelper {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 获取该注解的属性包装对象
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableApolloConfig.class.getName()));

        // 获取value值，即命名空间集合
        String[] namespaces = attributes.getStringArray("value");
        // 获取顺序
        int order = attributes.getNumber("order");

        // 添加对应命名空间和相应的顺序的缓存
        PropertySourcesProcessor.addNamespaces(Lists.newArrayList(namespaces), order);

        Map<String, Object> propertySourcesPlaceholderPropertyValues = new HashMap<>();
        // 确保默认的 PropertySourcesPlaceholderConfigurer 的优先级高于 PropertyPlaceholderConfigurer
        propertySourcesPlaceholderPropertyValues.put("order", 0);

        // 替换 PlaceHolder 为对应的属性值
        BeanRegistrationUtil.registerBeanDefinitionIfNotExists(
                registry,
                PropertySourcesPlaceholderConfigurer.class.getName(),
                PropertySourcesPlaceholderConfigurer.class,
                propertySourcesPlaceholderPropertyValues);

        // 属性源处理器，用于 占位符自动更新机制处理
        BeanRegistrationUtil.registerBeanDefinitionIfNotExists(
                registry,
                PropertySourcesProcessor.class.getName(),
                PropertySourcesProcessor.class);

        // 解析 @ApolloConfig 和 @ApolloConfigChangeListener 注解
        BeanRegistrationUtil.registerBeanDefinitionIfNotExists(
                registry,
                ApolloAnnotationProcessor.class.getName(),
                ApolloAnnotationProcessor.class);

        // 用于 StringValue 的 PlaceHolder 的 自动更新机制
        BeanRegistrationUtil.registerBeanDefinitionIfNotExists(
                registry,
                SpringValueProcessor.class.getName(),
                SpringValueProcessor.class);

        // springValue的定义处理器，用于处理xml的定义
        BeanRegistrationUtil.registerBeanDefinitionIfNotExists(
                registry,
                SpringValueDefinitionProcessor.class.getName(),
                SpringValueDefinitionProcessor.class);

        // 解析 @ApolloJsonValue 注解ConfigPropertySourcesProcessor
        BeanRegistrationUtil.registerBeanDefinitionIfNotExists(
                registry,
                ApolloJsonValueProcessor.class.getName(),
                ApolloJsonValueProcessor.class);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
