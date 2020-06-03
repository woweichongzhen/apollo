package com.ctrip.framework.apollo.spring.property;

import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.springframework.beans.factory.BeanFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * spring值注册中心
 */
public class SpringValueRegistry {

    private static final long CLEAN_INTERVAL_IN_SECONDS = 5;

    /**
     * springValue集合
     * 外层key：bean工厂
     * 内层key：占位符中key名
     * 内层value：springValue集合
     */
    private final Map<BeanFactory, Multimap<String, SpringValue>> registry = Maps.newConcurrentMap();

    /**
     * 是否初始化
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 注册同步锁
     */
    private final Object LOCK = new Object();

    /**
     * 注册占位符的key相关到集合中
     *
     * @param beanFactory bean工厂
     * @param key         占位符中的key
     * @param springValue spring值
     */
    public void register(BeanFactory beanFactory, String key, SpringValue springValue) {
        // 如果未注册过该bean工厂的值（双重自检索），执行注册
        if (!registry.containsKey(beanFactory)) {
            synchronized (LOCK) {
                if (!registry.containsKey(beanFactory)) {
                    registry.put(beanFactory,
                            Multimaps.synchronizedListMultimap(LinkedListMultimap.<String, SpringValue>create()));
                }
            }
        }

        registry.get(beanFactory).put(key, springValue);

        // 懒加载初始化，直到注册时，再执行
        if (initialized.compareAndSet(false, true)) {
            initialize();
        }
    }

    /**
     * 根据bean工厂和配置key获取是否有注入该key的StringValue
     *
     * @param beanFactory bean工厂
     * @param key         配置key
     * @return 注入该key的StringValue
     */
    public Collection<SpringValue> get(BeanFactory beanFactory, String key) {
        Multimap<String, SpringValue> beanFactorySpringValues = registry.get(beanFactory);
        if (beanFactorySpringValues == null) {
            return null;
        }
        return beanFactorySpringValues.get(key);
    }

    /**
     * 初始化
     */
    private void initialize() {
        Executors.newSingleThreadScheduledExecutor(ApolloThreadFactory.create("SpringValueRegistry", true)).scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            scanAndClean();
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                        }
                    }
                }, CLEAN_INTERVAL_IN_SECONDS, CLEAN_INTERVAL_IN_SECONDS, TimeUnit.SECONDS);
    }

    private void scanAndClean() {
        Iterator<Multimap<String, SpringValue>> iterator = registry.values().iterator();
        while (!Thread.currentThread().isInterrupted() && iterator.hasNext()) {
            Multimap<String, SpringValue> springValues = iterator.next();
            Iterator<Entry<String, SpringValue>> springValueIterator = springValues.entries().iterator();
            while (springValueIterator.hasNext()) {
                Entry<String, SpringValue> springValue = springValueIterator.next();
                if (!springValue.getValue().isTargetBeanValid()) {
                    // clear unused spring values
                    springValueIterator.remove();
                }
            }
        }
    }
}
