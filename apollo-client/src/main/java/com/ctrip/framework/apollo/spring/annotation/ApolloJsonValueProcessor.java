package com.ctrip.framework.apollo.spring.annotation;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.spring.property.PlaceholderHelper;
import com.ctrip.framework.apollo.spring.property.SpringValue;
import com.ctrip.framework.apollo.spring.property.SpringValueRegistry;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * apollo {@link ApolloJsonValue} 注解处理器
 * Create by zhangzheng on 2018/2/6
 */
public class ApolloJsonValueProcessor extends ApolloProcessor implements BeanFactoryAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApolloJsonValueProcessor.class);

    private static final Gson GSON = new Gson();

    private final ConfigUtil configUtil;

    private final PlaceholderHelper placeholderHelper;

    private final SpringValueRegistry springValueRegistry;

    private ConfigurableBeanFactory beanFactory;

    public ApolloJsonValueProcessor() {
        configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        placeholderHelper = SpringInjector.getInstance(PlaceholderHelper.class);
        springValueRegistry = SpringInjector.getInstance(SpringValueRegistry.class);
    }

    @Override
    protected void processField(Object bean, String beanName, Field field) {
        ApolloJsonValue apolloJsonValue = AnnotationUtils.getAnnotation(field, ApolloJsonValue.class);
        if (apolloJsonValue == null) {
            return;
        }
        // 获取注解占位符，再解析占位符中的值
        String placeholder = apolloJsonValue.value();
        Object propertyValue = placeholderHelper.resolvePropertyValue(beanFactory, beanName, placeholder);

        // 注解保证了值不会为空，判断是否为 String 类型
        if (!(propertyValue instanceof String)) {
            return;
        }

        // 设置值到域中
        boolean accessible = field.isAccessible();
        field.setAccessible(true);
        ReflectionUtils.setField(field, bean, this.parseJsonValue((String) propertyValue, field.getGenericType()));
        field.setAccessible(accessible);

        // 如果开启了自动更新机制，解析占位符额外的key，注册对应的 SpringValue
        if (configUtil.isAutoUpdateInjectedSpringPropertiesEnabled()) {
            Set<String> keys = placeholderHelper.extractPlaceholderKeys(placeholder);
            for (String key : keys) {
                SpringValue springValue = new SpringValue(key, placeholder, bean, beanName, field, true);
                springValueRegistry.register(beanFactory, key, springValue);
                LOGGER.debug("Monitoring {}", springValue);
            }
        }
    }

    @Override
    protected void processMethod(Object bean, String beanName, Method method) {
        ApolloJsonValue apolloJsonValue = AnnotationUtils.getAnnotation(method, ApolloJsonValue.class);
        if (apolloJsonValue == null) {
            return;
        }

        // 获取注解中value的属性值
        String placeHolder = apolloJsonValue.value();
        Object propertyValue = placeholderHelper.resolvePropertyValue(beanFactory, beanName, placeHolder);

        // 忽略非String
        if (!(propertyValue instanceof String)) {
            return;
        }

        // 获取此方法的参数类型，如果不等于1，异常
        Type[] types = method.getGenericParameterTypes();
        Preconditions.checkArgument(types.length == 1,
                "Ignore @Value setter {}.{}, expecting 1 parameter, actual {} parameters",
                bean.getClass().getName(), method.getName(), method.getParameterTypes().length);

        // 反射调用方法，设置值
        boolean accessible = method.isAccessible();
        method.setAccessible(true);
        ReflectionUtils.invokeMethod(method, bean, parseJsonValue((String) propertyValue, types[0]));
        method.setAccessible(accessible);

        // 如果开启了自动更新，解析额外的key，注册对应的 StringValue
        if (configUtil.isAutoUpdateInjectedSpringPropertiesEnabled()) {
            Set<String> keys = placeholderHelper.extractPlaceholderKeys(placeHolder);
            for (String key : keys) {
                SpringValue springValue = new SpringValue(key, apolloJsonValue.value(), bean, beanName,
                        method, true);
                springValueRegistry.register(beanFactory, key, springValue);
                LOGGER.debug("Monitoring {}", springValue);
            }
        }
    }

    /**
     * 解析json字符串到对象
     *
     * @param json       json字符串
     * @param targetType 目标类型
     * @return 对象
     */
    private Object parseJsonValue(String json, Type targetType) {
        try {
            return GSON.fromJson(json, targetType);
        } catch (Throwable ex) {
            LOGGER.error("Parsing json '{}' to type {} failed!", json, targetType, ex);
            throw ex;
        }
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableBeanFactory) beanFactory;
    }
}
