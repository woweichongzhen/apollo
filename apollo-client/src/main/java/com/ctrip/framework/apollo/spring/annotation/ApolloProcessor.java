package com.ctrip.framework.apollo.spring.annotation;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

/**
 * Apollo 处理器抽象类，封装了在 Spring Bean 初始化之前，处理属性和方法
 * <p>
 * Create by zhangzheng on 2018/2/6
 */
public abstract class ApolloProcessor implements BeanPostProcessor, PriorityOrdered {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class clazz = bean.getClass();
        // 遍历处理域
        for (Field field : this.findAllField(clazz)) {
            processField(bean, beanName, field);
        }
        // 遍历处理方法
        for (Method method : this.findAllMethod(clazz)) {
            processMethod(bean, beanName, method);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * 子类实现，处理域
     */
    protected abstract void processField(Object bean, String beanName, Field field);

    /**
     * 子类实现，处理方法
     */
    protected abstract void processMethod(Object bean, String beanName, Method method);


    @Override
    public int getOrder() {
        // 尽量晚的顺序
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * 获取一个类中的所有域集合
     *
     * @param clazz 类
     * @return 域集合
     */
    private List<Field> findAllField(Class clazz) {
        final List<Field> res = new LinkedList<>();
        ReflectionUtils.doWithFields(clazz, new ReflectionUtils.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException {
                res.add(field);
            }
        });
        return res;
    }

    /**
     * 获取一个类中所有的方法集合
     *
     * @param clazz 类
     * @return 方法集合
     */
    private List<Method> findAllMethod(Class clazz) {
        final List<Method> res = new LinkedList<>();
        ReflectionUtils.doWithMethods(clazz, new ReflectionUtils.MethodCallback() {
            @Override
            public void doWith(Method method) throws IllegalArgumentException {
                res.add(method);
            }
        });
        return res;
    }
}
