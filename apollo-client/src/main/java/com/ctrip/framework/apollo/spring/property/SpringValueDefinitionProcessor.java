package com.ctrip.framework.apollo.spring.property;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * spring值定义处理器，用于处理xml配置的占位符
 * <p>
 * 例如：
 * <bean class="com.ctrip.framework.apollo.demo.spring.xmlConfigDemo.bean.XmlBean">
 * <property name="timeout" value="${timeout:200}"/>
 * <property name="batch" value="${batch:100}"/>
 * </bean>
 * 每个 <property /> 都会被解析成一个 StringValueDefinition 对象。
 * <p>
 * To process xml config placeholders, e.g.
 *
 * <pre>
 *  &lt;bean class=&quot;com.ctrip.framework.apollo.demo.spring.xmlConfigDemo.bean.XmlBean&quot;&gt;
 *    &lt;property name=&quot;timeout&quot; value=&quot;${timeout:200}&quot;/&gt;
 *    &lt;property name=&quot;batch&quot; value=&quot;${batch:100}&quot;/&gt;
 *  &lt;/bean&gt;
 * </pre>
 */
public class SpringValueDefinitionProcessor implements BeanDefinitionRegistryPostProcessor {

    /**
     * bean名转换为spring值定义
     * key：bean定义注册中心
     * 第二层key：bean名
     * 第二层value：该bean中可替换的值定义集合
     */
    private static final Map<BeanDefinitionRegistry, Multimap<String, SpringValueDefinition>> BEAN_NAME_2_SPRING_VALUE_DEFINITIONS = Maps.newConcurrentMap();

    /**
     * 属性值处理bean工厂，添加进来的说明已经处理完该注册中心（spring上下文）中的bean定义
     */
    private static final Set<BeanDefinitionRegistry> PROPERTY_VALUES_PROCESSED_BEAN_FACTORIES =
            Sets.newConcurrentHashSet();

    private final ConfigUtil configUtil;

    /**
     * 占位符辅助工具类，guice注入的spring依赖
     */
    private final PlaceholderHelper placeholderHelper;

    public SpringValueDefinitionProcessor() {
        configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        placeholderHelper = SpringInjector.getInstance(PlaceholderHelper.class);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // 是否开启自动更新功能，因为 SpringValueDefinitionProcessor 就是为了这个功能编写的
        if (configUtil.isAutoUpdateInjectedSpringPropertiesEnabled()) {
            processPropertyValues(registry);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    /**
     * 获取bean的属性的值处理定义集合
     *
     * @param registry bean注册中心
     * @return bean的属性的值处理定义集合
     */
    public static Multimap<String, SpringValueDefinition> getBeanName2SpringValueDefinitions(BeanDefinitionRegistry registry) {
        Multimap<String, SpringValueDefinition> springValueDefinitions =
                BEAN_NAME_2_SPRING_VALUE_DEFINITIONS.get(registry);
        if (springValueDefinitions == null) {
            springValueDefinitions = LinkedListMultimap.create();
        }

        return springValueDefinitions;
    }

    /**
     * 处理属性值
     *
     * @param beanRegistry bean定义注册中心
     */
    private void processPropertyValues(BeanDefinitionRegistry beanRegistry) {
        // 如果缓存中已经有该bean注册中心，说明已经处理过了
        if (!PROPERTY_VALUES_PROCESSED_BEAN_FACTORIES.add(beanRegistry)) {
            return;
        }

        // 如果缓存中还没包含对应的注册中心的处理，初始化一个listmap进去
        if (!BEAN_NAME_2_SPRING_VALUE_DEFINITIONS.containsKey(beanRegistry)) {
            BEAN_NAME_2_SPRING_VALUE_DEFINITIONS.put(
                    beanRegistry,
                    LinkedListMultimap.<String, SpringValueDefinition>create());
        }

        Multimap<String, SpringValueDefinition> springValueDefinitions =
                BEAN_NAME_2_SPRING_VALUE_DEFINITIONS.get(beanRegistry);

        // 循环bean定义集合
        String[] beanNames = beanRegistry.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = beanRegistry.getBeanDefinition(beanName);
            // 获取bean定义的属性集合，对属性值进行遍历
            MutablePropertyValues mutablePropertyValues = beanDefinition.getPropertyValues();
            List<PropertyValue> propertyValues = mutablePropertyValues.getPropertyValueList();
            for (PropertyValue propertyValue : propertyValues) {
                // 如果值不是 spring 占位符类型的，跳过
                Object value = propertyValue.getValue();
                if (!(value instanceof TypedStringValue)) {
                    continue;
                }
                // 否则获取占位符，提取占位符的key集合
                String placeholder = ((TypedStringValue) value).getValue();
                Set<String> keys = placeholderHelper.extractPlaceholderKeys(placeholder);

                // 如果不存在key，跳过
                if (keys.isEmpty()) {
                    continue;
                }

                // 否则遍历key，创建对应的spring值对象，并添加到缓存中
                for (String key : keys) {
                    springValueDefinitions.put(beanName,
                            new SpringValueDefinition(key, placeholder, propertyValue.getName()));
                }
            }
        }
    }
}
