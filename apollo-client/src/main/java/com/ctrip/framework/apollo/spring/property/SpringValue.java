package com.ctrip.framework.apollo.spring.property;

import org.springframework.core.MethodParameter;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * spring @Value 注解中的方法信息
 *
 * @author github.com/zhegexiaohuozi  seimimaster@gmail.com
 * @since 2018/2/6.
 */
public class SpringValue {

    /**
     * Spring 方法参数封装
     */
    private MethodParameter methodParameter;

    /**
     * 域
     */
    private Field field;

    /**
     * bean定义弱引用
     */
    private WeakReference<Object> beanRef;

    /**
     * bean名
     */
    private String beanName;

    /**
     * 占位符中的key，也是Config中的key
     */
    private String key;

    /**
     * 占位符
     */
    private String placeholder;

    /**
     * 目标类型，值类型
     */
    private Class<?> targetType;

    /**
     * 泛型。当是 JSON 类型时，使用
     */
    private Type genericType;

    /**
     * 是否为json
     */
    private boolean isJson;

    /**
     * 域封装
     *
     * @param key         占位符中的key
     * @param placeholder 占位符
     * @param bean        bean实例
     * @param beanName    bean名
     * @param field       对应的域
     * @param isJson      是否为json
     */
    public SpringValue(String key, String placeholder, Object bean, String beanName, Field field, boolean isJson) {
        this.beanRef = new WeakReference<>(bean);
        this.beanName = beanName;
        this.field = field;
        this.key = key;
        this.placeholder = placeholder;
        this.targetType = field.getType();
        this.isJson = isJson;
        if (isJson) {
            this.genericType = field.getGenericType();
        }
    }

    /**
     * 方法分主干
     *
     * @param key         占位符中的key
     * @param placeholder 占位符
     * @param bean        bean实例
     * @param beanName    bean名
     * @param method      对应的方法
     * @param isJson      是否为json
     */
    public SpringValue(String key, String placeholder, Object bean, String beanName, Method method, boolean isJson) {
        this.beanRef = new WeakReference<>(bean);
        this.beanName = beanName;
        this.methodParameter = new MethodParameter(method, 0);
        this.key = key;
        this.placeholder = placeholder;
        Class<?>[] paramTps = method.getParameterTypes();
        this.targetType = paramTps[0];
        this.isJson = isJson;
        if (isJson) {
            this.genericType = method.getGenericParameterTypes()[0];
        }
    }

    /**
     * 更新值
     *
     * @param newVal 新值
     */
    public void update(Object newVal) throws IllegalAccessException, InvocationTargetException {
        if (isField()) {
            // 注入域中的新值
            injectField(newVal);
        } else {
            // 注入方法中的新值
            injectMethod(newVal);
        }
    }

    /**
     * 新值注入域中
     *
     * @param newVal 新值
     */
    private void injectField(Object newVal) throws IllegalAccessException {
        // 获取bean实例对象
        Object bean = beanRef.get();
        if (bean == null) {
            return;
        }

        // 设置域为可见，执行赋值，再回复域的可见性
        boolean accessible = field.isAccessible();
        field.setAccessible(true);
        field.set(bean, newVal);
        field.setAccessible(accessible);
    }

    /**
     * 新值注入方法中
     *
     * @param newVal 新值
     */
    private void injectMethod(Object newVal) throws InvocationTargetException, IllegalAccessException {
        // 获取bean实例对象
        Object bean = beanRef.get();
        if (bean == null) {
            return;
        }
        // 获取对应的方法，反射执行方法
        methodParameter.getMethod().invoke(bean, newVal);
    }

    public String getBeanName() {
        return beanName;
    }

    public Class<?> getTargetType() {
        return targetType;
    }

    public String getPlaceholder() {
        return this.placeholder;
    }

    public MethodParameter getMethodParameter() {
        return methodParameter;
    }

    /**
     * 是否为域
     */
    public boolean isField() {
        return this.field != null;
    }

    public Field getField() {
        return field;
    }

    public Type getGenericType() {
        return genericType;
    }

    public boolean isJson() {
        return isJson;
    }

    boolean isTargetBeanValid() {
        return beanRef.get() != null;
    }

    @Override
    public String toString() {
        Object bean = beanRef.get();
        if (bean == null) {
            return "";
        }
        if (isField()) {
            return String
                    .format("key: %s, beanName: %s, field: %s.%s", key, beanName, bean.getClass().getName(),
                            field.getName());
        }
        return String.format("key: %s, beanName: %s, method: %s.%s", key, beanName, bean.getClass().getName(),
                methodParameter.getMethod().getName());
    }
}
