package com.ctrip.framework.apollo.spring.annotation;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.spring.property.*;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

/**
 * spring value处理器
 * 处理用@Value 和 xml 中的占位符的 域 和 方法
 * <p>
 * Spring value processor of field or method which has @Value and xml config placeholders.
 *
 * @author github.com/zhegexiaohuozi  seimimaster@gmail.com
 * @since 2017/12/20.
 */
public class SpringValueProcessor extends ApolloProcessor implements BeanFactoryPostProcessor, BeanFactoryAware {

    private static final Logger logger = LoggerFactory.getLogger(SpringValueProcessor.class);

    private final ConfigUtil configUtil;
    private final PlaceholderHelper placeholderHelper;
    private final SpringValueRegistry springValueRegistry;

    private BeanFactory beanFactory;

    /**
     * spring 值 占位符定义集合
     * key：bean名
     * value：list
     */
    private Multimap<String, SpringValueDefinition> beanName2SpringValueDefinitions;

    public SpringValueProcessor() {
        configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        placeholderHelper = SpringInjector.getInstance(PlaceholderHelper.class);
        springValueRegistry = SpringInjector.getInstance(SpringValueRegistry.class);
        beanName2SpringValueDefinitions = LinkedListMultimap.create();
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 如果开启了自动更新，并且此bean工厂的实现类继承了bean定义注册中心接口，则从值定义处理器中获取已经处理好的对象集合
        if (configUtil.isAutoUpdateInjectedSpringPropertiesEnabled()
                && beanFactory instanceof BeanDefinitionRegistry) {
            beanName2SpringValueDefinitions = SpringValueDefinitionProcessor
                    .getBeanName2SpringValueDefinitions((BeanDefinitionRegistry) beanFactory);
        }
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // 如果开启了自动更新，调用父类的bean定义处理初始化前处理 @Value注解的域和方法（添加到自行实现的bean缓存组件中），然后处理bean属性中的值
        if (configUtil.isAutoUpdateInjectedSpringPropertiesEnabled()) {
            super.postProcessBeforeInitialization(bean, beanName);
            processBeanPropertyValues(bean, beanName);
        }
        return bean;
    }

    @Override
    protected void processField(Object bean, String beanName, Field field) {
        // 获取域上的 @Value 注解
        Value value = field.getAnnotation(Value.class);
        if (value == null) {
            return;
        }

        // 获取注解 value 中的占位符key
        Set<String> keys = placeholderHelper.extractPlaceholderKeys(value.value());

        if (keys.isEmpty()) {
            return;
        }

        // 遍历key，生成注解中的 spring @Value 中相关数据对象，注册到注册中心中（自己实现）
        for (String key : keys) {
            SpringValue springValue = new SpringValue(key, value.value(), bean, beanName, field, false);
            springValueRegistry.register(beanFactory, key, springValue);
            logger.debug("Monitoring {}", springValue);
        }
    }

    @Override
    protected void processMethod(Object bean, String beanName, Method method) {
        // 处理 @Value 注解标注的方法
        Value value = method.getAnnotation(Value.class);
        if (value == null) {
            return;
        }

        // 跳过配置类中 @Bean 注解标注的方法
        if (method.getAnnotation(Bean.class) != null) {
            return;
        }

        // 如果方法参数有多个或者没有（不等于1），即有问题，异常日志+直接返回
        if (method.getParameterTypes().length != 1) {
            logger.error("Ignore @Value setter {}.{}, expecting 1 parameter, actual {} parameters",
                    bean.getClass().getName(), method.getName(), method.getParameterTypes().length);
            return;
        }

        // 解析 value 中的 占位符 key
        Set<String> keys = placeholderHelper.extractPlaceholderKeys(value.value());

        if (keys.isEmpty()) {
            return;
        }

        // 遍历key，生成注解中的 spring @Value 中相关数据对象，注册到注册中心中（自己实现）
        for (String key : keys) {
            SpringValue springValue = new SpringValue(key, value.value(), bean, beanName, method, false);
            springValueRegistry.register(beanFactory, key, springValue);
            logger.info("Monitoring {}", springValue);
        }
    }

    /**
     * 处理bean属性中的值
     *
     * @param bean     bean实例
     * @param beanName bean名
     */
    private void processBeanPropertyValues(Object bean, String beanName) {
        // 获取对应bean下的spring值定义集合
        Collection<SpringValueDefinition> propertySpringValues = beanName2SpringValueDefinitions.get(beanName);
        if (propertySpringValues == null || propertySpringValues.isEmpty()) {
            return;
        }

        // 遍历spring 值定义
        for (SpringValueDefinition definition : propertySpringValues) {
            try {
                // 获取指定属性的描述符
                PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(
                        bean.getClass(),
                        definition.getPropertyName());
                // 处理write方法
                Method method = pd.getWriteMethod();
                if (method == null) {
                    continue;
                }
                // 如果该方法存在，形成spring值，注册到中心中
                SpringValue springValue = new SpringValue(definition.getKey(), definition.getPlaceholder(),
                        bean, beanName, method, false);
                springValueRegistry.register(beanFactory, definition.getKey(), springValue);
                logger.debug("Monitoring {}", springValue);
            } catch (Throwable ex) {
                logger.error("Failed to enable auto update feature for {}.{}", bean.getClass(),
                        definition.getPropertyName());
            }
        }

        // 清空bean值定义缓存
        beanName2SpringValueDefinitions.removeAll(beanName);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
