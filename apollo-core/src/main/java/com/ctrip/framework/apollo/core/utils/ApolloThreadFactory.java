package com.ctrip.framework.apollo.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程工厂
 */
public class ApolloThreadFactory implements ThreadFactory {

    private static final Logger log = LoggerFactory.getLogger(ApolloThreadFactory.class);

    /**
     * 线程编号
     */
    private final AtomicLong threadNumber = new AtomicLong(1);

    /**
     * 线程前缀
     */
    private final String namePrefix;

    /**
     * 是否后台线程
     */
    private final boolean daemon;

    /**
     * 线程组命名
     */
    private static final ThreadGroup THREAD_GROUP = new ThreadGroup("Apollo");

    public static ThreadGroup getThreadGroup() {
        return THREAD_GROUP;
    }

    /**
     * 创建线程工厂
     *
     * @param namePrefix 前缀
     * @param daemon     是否后台线程
     * @return 线程工厂
     */
    public static ThreadFactory create(String namePrefix, boolean daemon) {
        return new ApolloThreadFactory(namePrefix, daemon);
    }

    public static boolean waitAllShutdown(int timeoutInMillis) {
        ThreadGroup group = getThreadGroup();
        Thread[] activeThreads = new Thread[group.activeCount()];
        group.enumerate(activeThreads);
        Set<Thread> alives = new HashSet<>(Arrays.asList(activeThreads));
        Set<Thread> dies = new HashSet<>();
        log.info("Current ACTIVE thread count is: {}", alives.size());
        long expire = System.currentTimeMillis() + timeoutInMillis;
        while (System.currentTimeMillis() < expire) {
            classify(alives, dies, new ClassifyStandard<Thread>() {
                @Override
                public boolean satisfy(Thread thread) {
                    return !thread.isAlive() || thread.isInterrupted() || thread.isDaemon();
                }
            });
            if (alives.size() > 0) {
                log.info("Alive apollo threads: {}", alives);
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException ex) {
                    // ignore
                }
            } else {
                log.info("All apollo threads are shutdown.");
                return true;
            }
        }
        log.warn("Some apollo threads are still alive but expire time has reached, alive threads: {}",
                alives);
        return false;
    }

    private static interface ClassifyStandard<T> {
        boolean satisfy(T thread);
    }

    private static <T> void classify(Set<T> src, Set<T> des, ClassifyStandard<T> standard) {
        Set<T> set = new HashSet<>();
        for (T t : src) {
            if (standard.satisfy(t)) {
                set.add(t);
            }
        }
        src.removeAll(set);
        des.addAll(set);
    }

    private ApolloThreadFactory(String namePrefix, boolean daemon) {
        this.namePrefix = namePrefix;
        this.daemon = daemon;
    }

    /**
     * 创建新线程
     *
     * @param runnable 运行接口
     * @return 线程
     */
    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(
                THREAD_GROUP,
                runnable,
                THREAD_GROUP.getName() + "-" + namePrefix + "-" + threadNumber.getAndIncrement());
        thread.setDaemon(daemon);
        // 设置线程为正常优先级
        if (thread.getPriority() != Thread.NORM_PRIORITY) {
            thread.setPriority(Thread.NORM_PRIORITY);
        }
        return thread;
    }
}
