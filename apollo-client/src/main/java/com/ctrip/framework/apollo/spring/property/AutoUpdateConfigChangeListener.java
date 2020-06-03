package com.ctrip.framework.apollo.spring.property;

import com.ctrip.framework.apollo.ConfigChangeListener;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Set;

/**
 * 自动更新配置改变监听器
 * <p>
 * Create by zhangzheng on 2018/3/6
 */
public class AutoUpdateConfigChangeListener implements ConfigChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(AutoUpdateConfigChangeListener.class);

    /**
     * {@link TypeConverter#convertIfNecessary(Object, Class, Field)} 是否可以带上 Field 参数，因为 Spring 3.2.0+ 才有该方法
     */
    private final boolean typeConverterHasConvertIfNecessaryWithFieldParameter;

    /**
     * 环境
     */
    private final Environment environment;

    /**
     * bean工厂
     */
    private final ConfigurableBeanFactory beanFactory;

    /**
     * 类型转换器
     */
    private final TypeConverter typeConverter;

    /**
     * 占位符辅助类
     */
    private final PlaceholderHelper placeholderHelper;

    /**
     * spring值封装注册中心
     */
    private final SpringValueRegistry springValueRegistry;

    /**
     * gson序列化
     */
    private final Gson gson;

    /**
     * 构造器
     *
     * @param environment spring环境
     * @param beanFactory spring bean工厂
     */
    public AutoUpdateConfigChangeListener(Environment environment, ConfigurableListableBeanFactory beanFactory) {
        this.typeConverterHasConvertIfNecessaryWithFieldParameter =
                testTypeConverterHasConvertIfNecessaryWithFieldParameter();
        this.beanFactory = beanFactory;
        this.typeConverter = this.beanFactory.getTypeConverter();
        this.environment = environment;
        this.placeholderHelper = SpringInjector.getInstance(PlaceholderHelper.class);
        this.springValueRegistry = SpringInjector.getInstance(SpringValueRegistry.class);
        this.gson = new Gson();
    }

    @Override
    public void onChange(ConfigChangeEvent changeEvent) {
        // 触发配置改变
        Set<String> keys = changeEvent.changedKeys();
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }

        // 遍历改变的key
        for (String key : keys) {
            // 检测是否有和该key相关的 StringValue
            Collection<SpringValue> targetValues = springValueRegistry.get(beanFactory, key);
            if (targetValues == null || targetValues.isEmpty()) {
                continue;
            }

            // 如果有，调用StringValue的相关反射方法，更新
            for (SpringValue val : targetValues) {
                updateSpringValue(val);
            }
        }
    }

    /**
     * 更新spring注入值
     *
     * @param springValue spring值封装
     */
    private void updateSpringValue(SpringValue springValue) {
        try {
            // 解析值对象，并更新
            Object value = resolvePropertyValue(springValue);
            springValue.update(value);

            logger.info("Auto update apollo changed value successfully, new value: {}, {}", value,
                    springValue);
        } catch (Throwable ex) {
            logger.error("Auto update apollo changed value failed, {}", springValue.toString(), ex);
        }
    }

    /**
     * 解析spring值对象
     * 根据{@link org.springframework.beans.factory.support.DefaultListableBeanFactory}逻辑移植
     * <p>
     * Logic transplanted from DefaultListableBeanFactory
     *
     * @see org.springframework.beans.factory.support.DefaultListableBeanFactory#doResolveDependency(org.springframework.beans.factory.config.DependencyDescriptor, java.lang.String, java.util.Set, org.springframework.beans.TypeConverter)
     */
    private Object resolvePropertyValue(SpringValue springValue) {
        // value will never be null, as @Value and @ApolloJsonValue will not allow that
        // 值永远不会为null，因为@Value和@ApolloJsonValue不允许这样做
        // value 是 Object 类型，不一定符合更新 StringValue 的值类型，因此，需要经过转换
        Object value = placeholderHelper.resolvePropertyValue(
                beanFactory,
                springValue.getBeanName(),
                springValue.getPlaceholder());

        if (springValue.isJson()) {
            // 如果值是json类型的，调用gson解析
            value = this.parseJsonValue((String) value, springValue.getGenericType());
        } else {
            if (springValue.isField()) {
                if (typeConverterHasConvertIfNecessaryWithFieldParameter) {
                    // 如果是域类型，并且 spring3.2 以上的版本，可以把 Filed 传入 类型转换器中，直接解析出来
                    value = typeConverter.convertIfNecessary(
                            value,
                            springValue.getTargetType(),
                            springValue.getField());
                } else {
                    // 如果不是 3.2 以上的版本，就不能传入 Field对象
                    value = typeConverter.convertIfNecessary(value, springValue.getTargetType());
                }
            } else {
                // 方法类型的传入方法参数
                value = typeConverter.convertIfNecessary(
                        value,
                        springValue.getTargetType(),
                        springValue.getMethodParameter());
            }
        }

        return value;
    }

    /**
     * 解析json为对应的对象
     *
     * @param json       json字符串
     * @param targetType 目标类型
     * @return 对象
     */
    private Object parseJsonValue(String json, Type targetType) {
        try {
            return gson.fromJson(json, targetType);
        } catch (Throwable ex) {
            logger.error("Parsing json '{}' to type {} failed!", json, targetType, ex);
            throw ex;
        }
    }

    /**
     * 测试类型转换是否有域参数，3.2.0+才有该方法
     *
     * @return true有，false没有
     */
    private boolean testTypeConverterHasConvertIfNecessaryWithFieldParameter() {
        try {
            TypeConverter.class.getMethod("convertIfNecessary", Object.class, Class.class, Field.class);
        } catch (Throwable ex) {
            return false;
        }

        return true;
    }
}
