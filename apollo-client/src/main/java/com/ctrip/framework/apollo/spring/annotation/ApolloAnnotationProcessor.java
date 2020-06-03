package com.ctrip.framework.apollo.spring.annotation;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigChangeListener;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * apollo注解处理器
 * 处理 @ApolloConfig 和 @ApolloConfigChangeListener 注解处理器的初始化
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ApolloAnnotationProcessor extends ApolloProcessor {

    @Override
    protected void processField(Object bean, String beanName, Field field) {
        // 处理配置注解
        ApolloConfig annotation = AnnotationUtils.getAnnotation(field, ApolloConfig.class);
        if (annotation == null) {
            return;
        }

        // 校验该注解注解的域，是否为 Config 类
        Preconditions.checkArgument(Config.class.isAssignableFrom(field.getType()),
                "Invalid type: %s for field: %s, should be Config", field.getType(), field);

        // 获取注解中的命名空间，再通过远端仓库拉取到对应的配置
        String namespace = annotation.value();
        Config config = ConfigService.getConfig(namespace);

        // 反射对应的域，注入
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, bean, config);
    }

    @Override
    protected void processMethod(final Object bean, String beanName, final Method method) {
        ApolloConfigChangeListener annotation = AnnotationUtils.findAnnotation(
                method,
                ApolloConfigChangeListener.class);
        if (annotation == null) {
            return;
        }

        // 获取该注解应用的方法参数，如果参数不为1，或者唯一的参数不为 ConfigChangeEvent 类型
        Class<?>[] parameterTypes = method.getParameterTypes();
        Preconditions.checkArgument(parameterTypes.length == 1,
                "Invalid number of parameters: %s for method: %s, should be 1", parameterTypes.length,
                method);
        Preconditions.checkArgument(ConfigChangeEvent.class.isAssignableFrom(parameterTypes[0]),
                "Invalid parameter type: %s for method: %s, should be ConfigChangeEvent", parameterTypes[0],
                method);

        // 通过反射对应的方法
        ReflectionUtils.makeAccessible(method);
        String[] namespaces = annotation.value();
        String[] annotatedInterestedKeys = annotation.interestedKeys();
        String[] annotatedInterestedKeyPrefixes = annotation.interestedKeyPrefixes();

        // 构建对应的配置改变监听器，回调对象
        ConfigChangeListener configChangeListener = new ConfigChangeListener() {
            @Override
            public void onChange(ConfigChangeEvent changeEvent) {
                ReflectionUtils.invokeMethod(method, bean, changeEvent);
            }
        };

        // 如果存在感兴趣的key或者key前缀
        Set<String> interestedKeys = annotatedInterestedKeys.length > 0
                ? Sets.newHashSet(annotatedInterestedKeys)
                : null;
        Set<String> interestedKeyPrefixes = annotatedInterestedKeyPrefixes.length > 0
                ? Sets.newHashSet(annotatedInterestedKeyPrefixes)
                : null;

        // 遍历监听的命名空间和感兴趣的key，添加通过注解构建的监听器
        for (String namespace : namespaces) {
            Config config = ConfigService.getConfig(namespace);

            if (interestedKeys == null && interestedKeyPrefixes == null) {
                config.addChangeListener(configChangeListener);
            } else {
                config.addChangeListener(configChangeListener, interestedKeys, interestedKeyPrefixes);
            }
        }
    }
}
