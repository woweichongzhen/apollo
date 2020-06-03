package com.ctrip.framework.apollo.spring.util;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.util.Map;
import java.util.Objects;

/**
 * bean注册工具类
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class BeanRegistrationUtil {

    /**
     * 注册bean定义如果bean不存在
     *
     * @param registry  bean定义注册中心
     * @param beanName  bean名
     * @param beanClass bean类型
     * @return true注册成功，false注册失败（包括已注册）
     */
    public static boolean registerBeanDefinitionIfNotExists(BeanDefinitionRegistry registry, String beanName,
                                                            Class<?> beanClass) {
        return registerBeanDefinitionIfNotExists(registry, beanName, beanClass, null);
    }

    /**
     * 如果bean定义不存在，执行注册
     *
     * @param registry            bean定义注册中心
     * @param beanName            bean名
     * @param beanClass           bean类型
     * @param extraPropertyValues 额外的属性值
     * @return true注册成功，false注册失败（包括已注册）
     */
    public static boolean registerBeanDefinitionIfNotExists(BeanDefinitionRegistry registry, String beanName,
                                                            Class<?> beanClass,
                                                            Map<String, Object> extraPropertyValues) {
        // 已注册，直接返回失败
        if (registry.containsBeanDefinition(beanName)) {
            return false;
        }

        // 如果bean定义名重复，返回失败
        String[] candidates = registry.getBeanDefinitionNames();
        for (String candidate : candidates) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(candidate);
            if (Objects.equals(beanDefinition.getBeanClassName(), beanClass.getName())) {
                return false;
            }
        }

        // 生成指定类型的bean定义
        BeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(beanClass).getBeanDefinition();

        // 如果存在额外参数属性，添加到bean定义中
        if (extraPropertyValues != null) {
            for (Map.Entry<String, Object> entry : extraPropertyValues.entrySet()) {
                beanDefinition.getPropertyValues().add(entry.getKey(), entry.getValue());
            }
        }

        // 注册bean定义
        registry.registerBeanDefinition(beanName, beanDefinition);

        return true;
    }


}
