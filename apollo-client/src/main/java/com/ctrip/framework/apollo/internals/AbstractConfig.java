package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigChangeListener;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.enums.PropertyChangeType;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.factory.PropertiesFactory;
import com.ctrip.framework.apollo.util.function.Functions;
import com.ctrip.framework.apollo.util.parser.Parsers;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 抽象的配置类
 * <p>
 * 实现了：
 * 缓存读取属性值
 * 异步通知监听器
 * 计算属性变化
 * 等等特性
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public abstract class AbstractConfig implements Config {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractConfig.class);

    /**
     * 状态变化异步通知线程池
     */
    private static final ExecutorService executorService;

    /**
     * 状态变化监听器
     */
    private final List<ConfigChangeListener> listeners = Lists.newCopyOnWriteArrayList();

    /**
     * 每个状态改变监听器感兴趣的key
     */
    private final Map<ConfigChangeListener, Set<String>> interestedKeys = Maps.newConcurrentMap();

    /**
     * 每个状态改变监听器感兴趣的key前缀
     */
    private final Map<ConfigChangeListener, Set<String>> interestedKeyPrefixes = Maps.newConcurrentMap();

    /**
     * 配置工具类
     */
    private final ConfigUtil configUtil;

    /**
     * 8大基本类型和Date的缓存
     */


    private volatile Cache<String, Integer> m_integerCache;
    private volatile Cache<String, Long> m_longCache;
    private volatile Cache<String, Short> m_shortCache;
    private volatile Cache<String, Float> m_floatCache;
    private volatile Cache<String, Double> m_doubleCache;
    private volatile Cache<String, Byte> m_byteCache;
    private volatile Cache<String, Boolean> m_booleanCache;
    private volatile Cache<String, Date> m_dateCache;
    private volatile Cache<String, Long> m_durationCache;

    /**
     * 配置缓存
     * 外层key：分隔符
     * 内层key：属性键
     */
    private final Map<String, Cache<String, String[]>> arrayCache;

    /**
     * 配置缓存的集合
     */
    private final List<Cache> allCaches;

    /**
     * 配置缓存的版本
     * <p>
     * 用于解决更新缓存可能存在的并发问题
     * 参考{@link #getValueAndStoreToCache(String, Function, Cache, Object)}
     */
    private final AtomicLong configVersion;

    protected PropertiesFactory propertiesFactory;

    /*
     * 静态方式初始化线程池
     */
    static {
        executorService = Executors.newCachedThreadPool(
                ApolloThreadFactory.create("Config", true));
    }

    public AbstractConfig() {
        configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        configVersion = new AtomicLong();
        arrayCache = Maps.newConcurrentMap();
        allCaches = Lists.newArrayList();
        propertiesFactory = ApolloInjector.getInstance(PropertiesFactory.class);
    }

    @Override
    public void addChangeListener(ConfigChangeListener listener) {
        addChangeListener(listener, null);
    }

    @Override
    public void addChangeListener(ConfigChangeListener listener, Set<String> interestedKeys) {
        addChangeListener(listener, interestedKeys, null);
    }

    @Override
    public void addChangeListener(ConfigChangeListener listener, Set<String> interestedKeys,
                                  Set<String> interestedKeyPrefixes) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);

            // 添加感兴趣的key
            if (interestedKeys != null && !interestedKeys.isEmpty()) {
                this.interestedKeys.put(listener, Sets.newHashSet(interestedKeys));
            }

            // 添加感兴趣的key前缀
            if (interestedKeyPrefixes != null && !interestedKeyPrefixes.isEmpty()) {
                this.interestedKeyPrefixes.put(listener, Sets.newHashSet(interestedKeyPrefixes));
            }
        }
    }

    @Override
    public boolean removeChangeListener(ConfigChangeListener listener) {
        interestedKeys.remove(listener);
        interestedKeyPrefixes.remove(listener);
        return listeners.remove(listener);
    }

    @Override
    public Integer getIntProperty(String key, Integer defaultValue) {
        try {
            if (m_integerCache == null) {
                synchronized (this) {
                    if (m_integerCache == null) {
                        m_integerCache = newCache();
                    }
                }
            }

            return getValueFromCache(key, Functions.TO_INT_FUNCTION, m_integerCache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getIntProperty for %s failed, return default value %d", key,
                            defaultValue), ex));
        }
        return defaultValue;
    }

    @Override
    public Long getLongProperty(String key, Long defaultValue) {
        try {
            if (m_longCache == null) {
                synchronized (this) {
                    if (m_longCache == null) {
                        m_longCache = newCache();
                    }
                }
            }

            return getValueFromCache(key, Functions.TO_LONG_FUNCTION, m_longCache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getLongProperty for %s failed, return default value %d", key,
                            defaultValue), ex));
        }
        return defaultValue;
    }

    @Override
    public Short getShortProperty(String key, Short defaultValue) {
        try {
            if (m_shortCache == null) {
                synchronized (this) {
                    if (m_shortCache == null) {
                        m_shortCache = newCache();
                    }
                }
            }

            return getValueFromCache(key, Functions.TO_SHORT_FUNCTION, m_shortCache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getShortProperty for %s failed, return default value %d", key,
                            defaultValue), ex));
        }
        return defaultValue;
    }

    @Override
    public Float getFloatProperty(String key, Float defaultValue) {
        try {
            if (m_floatCache == null) {
                synchronized (this) {
                    if (m_floatCache == null) {
                        m_floatCache = newCache();
                    }
                }
            }

            return getValueFromCache(key, Functions.TO_FLOAT_FUNCTION, m_floatCache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getFloatProperty for %s failed, return default value %f", key,
                            defaultValue), ex));
        }
        return defaultValue;
    }

    @Override
    public Double getDoubleProperty(String key, Double defaultValue) {
        try {
            if (m_doubleCache == null) {
                synchronized (this) {
                    if (m_doubleCache == null) {
                        m_doubleCache = newCache();
                    }
                }
            }

            return getValueFromCache(key, Functions.TO_DOUBLE_FUNCTION, m_doubleCache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getDoubleProperty for %s failed, return default value %f", key,
                            defaultValue), ex));
        }
        return defaultValue;
    }

    @Override
    public Byte getByteProperty(String key, Byte defaultValue) {
        try {
            if (m_byteCache == null) {
                synchronized (this) {
                    if (m_byteCache == null) {
                        m_byteCache = newCache();
                    }
                }
            }

            return getValueFromCache(key, Functions.TO_BYTE_FUNCTION, m_byteCache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getByteProperty for %s failed, return default value %d", key,
                            defaultValue), ex));
        }
        return defaultValue;
    }

    @Override
    public Boolean getBooleanProperty(String key, Boolean defaultValue) {
        try {
            if (m_booleanCache == null) {
                synchronized (this) {
                    if (m_booleanCache == null) {
                        m_booleanCache = newCache();
                    }
                }
            }

            return getValueFromCache(key, Functions.TO_BOOLEAN_FUNCTION, m_booleanCache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getBooleanProperty for %s failed, return default value %b", key,
                            defaultValue), ex));
        }
        return defaultValue;
    }

    @Override
    public String[] getArrayProperty(String key, final String delimiter, String[] defaultValue) {
        try {
            if (!arrayCache.containsKey(delimiter)) {
                synchronized (this) {
                    if (!arrayCache.containsKey(delimiter)) {
                        arrayCache.put(delimiter, this.<String[]>newCache());
                    }
                }
            }

            Cache<String, String[]> cache = arrayCache.get(delimiter);
            String[] result = cache.getIfPresent(key);

            if (result != null) {
                return result;
            }

            return getValueAndStoreToCache(key, new Function<String, String[]>() {
                @Override
                public String[] apply(String input) {
                    return input.split(delimiter);
                }
            }, cache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getArrayProperty for %s failed, return default value", key), ex));
        }
        return defaultValue;
    }

    @Override
    public <T extends Enum<T>> T getEnumProperty(String key, Class<T> enumType, T defaultValue) {
        try {
            String value = getProperty(key, null);

            if (value != null) {
                return Enum.valueOf(enumType, value);
            }
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getEnumProperty for %s failed, return default value %s", key,
                            defaultValue), ex));
        }

        return defaultValue;
    }

    @Override
    public Date getDateProperty(String key, Date defaultValue) {
        try {
            if (m_dateCache == null) {
                synchronized (this) {
                    if (m_dateCache == null) {
                        m_dateCache = newCache();
                    }
                }
            }

            return getValueFromCache(key, Functions.TO_DATE_FUNCTION, m_dateCache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getDateProperty for %s failed, return default value %s", key,
                            defaultValue), ex));
        }

        return defaultValue;
    }

    @Override
    public Date getDateProperty(String key, String format, Date defaultValue) {
        try {
            String value = getProperty(key, null);

            if (value != null) {
                return Parsers.forDate().parse(value, format);
            }
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getDateProperty for %s failed, return default value %s", key,
                            defaultValue), ex));
        }

        return defaultValue;
    }

    @Override
    public Date getDateProperty(String key, String format, Locale locale, Date defaultValue) {
        try {
            String value = getProperty(key, null);

            if (value != null) {
                return Parsers.forDate().parse(value, format, locale);
            }
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getDateProperty for %s failed, return default value %s", key,
                            defaultValue), ex));
        }

        return defaultValue;
    }

    @Override
    public long getDurationProperty(String key, long defaultValue) {
        try {
            if (m_durationCache == null) {
                synchronized (this) {
                    if (m_durationCache == null) {
                        m_durationCache = newCache();
                    }
                }
            }

            return getValueFromCache(key, Functions.TO_DURATION_FUNCTION, m_durationCache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getDurationProperty for %s failed, return default value %d", key,
                            defaultValue), ex));
        }

        return defaultValue;
    }

    @Override
    public <T> T getProperty(String key, Function<String, T> function, T defaultValue) {
        try {
            // 获取属性String值
            String value = getProperty(key, null);

            // 解析string为对应类型
            if (value != null) {
                return function.apply(value);
            }
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getProperty for %s failed, return default value %s", key,
                            defaultValue), ex));
        }

        return defaultValue;
    }

    /**
     * 从缓存中获取值
     */
    private <T> T getValueFromCache(String key, Function<String, T> parser, Cache<String, T> cache, T defaultValue) {
        T result = cache.getIfPresent(key);

        if (result != null) {
            return result;
        }

        // 如果不存在则获取并存到缓存中
        return getValueAndStoreToCache(key, parser, cache, defaultValue);
    }

    /**
     * 获取值并且存到缓存中
     */
    private <T> T getValueAndStoreToCache(String key, Function<String, T> parser, Cache<String, T> cache,
                                          T defaultValue) {
        // 获取当前版本号
        long currentConfigVersion = configVersion.get();

        // 获取属性并解析
        String value = getProperty(key, null);

        if (value != null) {
            T result = parser.apply(value);

            // 如果版本号发生变更，则使用默认值
            if (result != null) {
                synchronized (this) {
                    if (configVersion.get() == currentConfigVersion) {
                        cache.put(key, result);
                    }
                }
                return result;
            }
        }

        return defaultValue;
    }

    /**
     * 创建新缓存
     */
    private <T> Cache<String, T> newCache() {
        Cache<String, T> cache = CacheBuilder.newBuilder()
                // 最大缓存的key
                .maximumSize(configUtil.getMaxConfigCacheSize())
                // 缓存过期时间，1分钟
                .expireAfterAccess(configUtil.getConfigCacheExpireTime(), configUtil.getConfigCacheExpireTimeUnit())
                .build();
        allCaches.add(cache);
        return cache;
    }

    /**
     * 清空所有配置缓存，版本号加一
     */
    protected void clearConfigCache() {
        // 和更新互斥，避免并发修改arrayList
        synchronized (this) {
            for (Cache c : allCaches) {
                if (c != null) {
                    c.invalidateAll();
                }
            }
            configVersion.incrementAndGet();
        }
    }

    /**
     * 触发状态改变事件
     *
     * @param changeEvent 改变事件
     */
    protected void fireConfigChange(final ConfigChangeEvent changeEvent) {
        // 遍历判断状态改变监听器
        for (final ConfigChangeListener listener : listeners) {
            // 判断此状态改变监听器是否关系此事件
            if (!isConfigChangeListenerInterested(listener, changeEvent)) {
                continue;
            }

            // 如果关心，异步通知
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    String listenerName = listener.getClass().getName();
                    Transaction transaction = Tracer.newTransaction("Apollo.ConfigChangeListener", listenerName);
                    try {
                        listener.onChange(changeEvent);
                        transaction.setStatus(Transaction.SUCCESS);
                    } catch (Throwable ex) {
                        transaction.setStatus(ex);
                        Tracer.logError(ex);
                        LOGGER.error("Failed to invoke config change listener {}", listenerName, ex);
                    } finally {
                        transaction.complete();
                    }
                }
            });
        }
    }

    /**
     * 判断此状态改变监听器是否关系此事件
     */
    private boolean isConfigChangeListenerInterested(ConfigChangeListener configChangeListener,
                                                     ConfigChangeEvent configChangeEvent) {
        // 获取当前监听器感兴趣的key和key前缀
        Set<String> interestedKeys = this.interestedKeys.get(configChangeListener);
        Set<String> interestedKeyPrefixes = this.interestedKeyPrefixes.get(configChangeListener);

        // 都为空，则对所有都感兴趣
        if ((interestedKeys == null || interestedKeys.isEmpty())
                && (interestedKeyPrefixes == null || interestedKeyPrefixes.isEmpty())) {
            return true; // no interested keys means interested in all keys
        }

        // 精确匹配
        if (interestedKeys != null) {
            for (String interestedKey : interestedKeys) {
                if (configChangeEvent.isChanged(interestedKey)) {
                    return true;
                }
            }
        }

        // 前缀匹配
        if (interestedKeyPrefixes != null) {
            for (String prefix : interestedKeyPrefixes) {
                for (final String changedKey : configChangeEvent.changedKeys()) {
                    if (changedKey.startsWith(prefix)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 计算配置改变
     *
     * @param namespace 命名空间
     * @param previous  上一个配置
     * @param current   当前配置
     * @return 配置改变集合
     */
    List<ConfigChange> calcPropertyChanges(String namespace, Properties previous,
                                           Properties current) {
        if (previous == null) {
            previous = propertiesFactory.getPropertiesInstance();
        }

        if (current == null) {
            current = propertiesFactory.getPropertiesInstance();
        }

        Set<String> previousKeys = previous.stringPropertyNames();
        Set<String> currentKeys = current.stringPropertyNames();

        // key交集
        Set<String> commonKeys = Sets.intersection(previousKeys, currentKeys);
        // 当前key独有的，即新增
        Set<String> newKeys = Sets.difference(currentKeys, commonKeys);
        // 上一个独有的key，即删除
        Set<String> removedKeys = Sets.difference(previousKeys, commonKeys);

        List<ConfigChange> changes = Lists.newArrayList();

        // 新增的改变
        for (String newKey : newKeys) {
            changes.add(new ConfigChange(namespace, newKey, null, current.getProperty(newKey),
                    PropertyChangeType.ADDED));
        }

        // 删除的改变
        for (String removedKey : removedKeys) {
            changes.add(new ConfigChange(namespace, removedKey, previous.getProperty(removedKey), null,
                    PropertyChangeType.DELETED));
        }

        // 公共的改变
        for (String commonKey : commonKeys) {
            String previousValue = previous.getProperty(commonKey);
            String currentValue = current.getProperty(commonKey);
            if (Objects.equal(previousValue, currentValue)) {
                continue;
            }
            changes.add(new ConfigChange(namespace, commonKey, previousValue, currentValue,
                    PropertyChangeType.MODIFIED));
        }

        return changes;
    }
}
