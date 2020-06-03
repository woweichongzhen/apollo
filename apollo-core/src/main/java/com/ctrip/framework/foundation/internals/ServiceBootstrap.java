package com.ctrip.framework.foundation.internals;

import com.ctrip.framework.apollo.core.spi.Ordered;
import com.google.common.collect.Lists;

import java.util.*;

/**
 * 服务启动调用
 */
public class ServiceBootstrap {

    /**
     * 加载指定服务的首个对象
     */
    public static <S> S loadFirst(Class<S> clazz) {
        Iterator<S> iterator = loadAll(clazz);
        if (!iterator.hasNext()) {
            throw new IllegalStateException(String.format(
                    "No implementation defined in /META-INF/services/%s, please check whether the file exists and has" +
                            " the right implementation class!",
                    clazz.getName()));
        }
        return iterator.next();
    }

    /**
     * 加载所有相关的类型
     */
    public static <S> Iterator<S> loadAll(Class<S> clazz) {
        ServiceLoader<S> loader = ServiceLoader.load(clazz);
        return loader.iterator();
    }

    /**
     * 按排序加载所有的类
     */
    public static <S extends Ordered> List<S> loadAllOrdered(Class<S> clazz) {
        Iterator<S> iterator = loadAll(clazz);

        if (!iterator.hasNext()) {
            throw new IllegalStateException(String.format(
                    "No implementation defined in /META-INF/services/%s, please check whether the file exists and has" +
                            " the right implementation class!",
                    clazz.getName()));
        }

        List<S> candidates = Lists.newArrayList(iterator);
        Collections.sort(candidates, new Comparator<S>() {
            @Override
            public int compare(S o1, S o2) {
                // the smaller order has higher priority
                return Integer.compare(o1.getOrder(), o2.getOrder());
            }
        });

        return candidates;
    }

    /**
     * 加载第一优先级的类
     */
    public static <S extends Ordered> S loadPrimary(Class<S> clazz) {
        List<S> candidates = loadAllOrdered(clazz);

        return candidates.get(0);
    }
}
