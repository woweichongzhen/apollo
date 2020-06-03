package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.core.utils.ClassLoaderUtil;
import com.ctrip.framework.apollo.enums.ConfigSourceType;
import com.ctrip.framework.apollo.enums.PropertyChangeType;
import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 默认的配置实现
 * <p>
 * 为什么 DefaultConfig 实现 RepositoryChangeListener 接口？
 * ConfigRepository 的一个实现类 RemoteConfigRepository ，会从远程 Config Service 加载配置。
 * 但是 Config Service 的配置不是一成不变，可以在 Portal 进行修改。
 * 所以 RemoteConfigRepository 会在配置变更时，从 Admin Service 重新加载配置。
 * 为了实现 Config 监听配置的变更，所以需要将 DefaultConfig 注册为 ConfigRepository 的监听器。
 * 因此，DefaultConfig 需要实现 RepositoryChangeListener 接口
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfig extends AbstractConfig implements RepositoryChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(DefaultConfig.class);

    /**
     * 此配置的命名空间
     */
    private final String namespace;

    /**
     * 项目配置文件属性
     */
    private final Properties resourceProperties;

    /**
     * 配置属性的缓存引用，优先级大于项目配置文件的优先级
     */
    private final AtomicReference<Properties> configProperties;

    /**
     * 配置仓库
     */
    private final ConfigRepository configRepository;

    /**
     * 告警限流器
     * 当读取不到属性值，会打印告警日志。通过该限流器，避免打印过多日志。
     */
    private final RateLimiter warnLogRateLimiter;

    private volatile ConfigSourceType sourceType = ConfigSourceType.NONE;

    /**
     * 构造函数，会从远端拉取配置并缓存到本地配置
     *
     * @param namespace        the namespace of this config instance 配置实例的命名空间
     * @param configRepository the config repository for this config instance 配置实例的仓库
     */
    public DefaultConfig(String namespace, ConfigRepository configRepository) {
        this.namespace = namespace;
        resourceProperties = this.loadFromResource(this.namespace);
        this.configRepository = configRepository;
        configProperties = new AtomicReference<>();

        // 1 warning log output per minute
        warnLogRateLimiter = RateLimiter.create(0.017);

        // 初始化
        initialize();
    }

    /**
     * 初始化
     */
    private void initialize() {
        try {
            // 从远程仓库拉取配置，并更新
            updateConfig(configRepository.getConfig(), configRepository.getSourceType());
        } catch (Throwable ex) {
            Tracer.logError(ex);
            logger.warn("Init Apollo Local Config failed - namespace: {}, reason: {}.",
                    namespace, ExceptionUtil.getDetailMessage(ex));
        } finally {
            // 注册配置改变监听器，无论仓库存储是否工作，因此一旦仓库开始工作，可以得到改变通知
            configRepository.addChangeListener(this);
        }
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        // 从系统属性中获取，例如 -Dkey=value
        String value = System.getProperty(key);

        // 从本地缓存属性中读取
        if (value == null && configProperties.get() != null) {
            value = configProperties.get().getProperty(key);
        }

        /*
         * 从环境变量中读取，例如  PATH=...
         * 通常，系统环境变量使用大写字母，但是可能会有例外。
         * 因此，使用者应在正确的情况下提供key
         */
        if (value == null) {
            value = System.getenv(key);
        }

        // 从类路径属性文件中获取
        if (value == null && resourceProperties != null) {
            value = resourceProperties.getProperty(key);
        }

        // 都获取不到，打印限流警告日志，并使用默认值
        if (value == null
                && configProperties.get() == null
                && warnLogRateLimiter.tryAcquire()) {
            logger.warn("Could not load config for namespace {} from Apollo, please check whether the configs are " +
                    "released in Apollo! Return default value now!", namespace);
        }
        return value == null ? defaultValue : value;
    }

    @Override
    public Set<String> getPropertyNames() {
        // 先从本地缓存配置中获取
        Properties properties = configProperties.get();
        if (properties == null) {
            return Collections.emptySet();
        }

        // 如果本地缓存有，转变为string类型的属性的属性名为set集合
        return stringPropertyNames(properties);
    }

    @Override
    public ConfigSourceType getSourceType() {
        return sourceType;
    }

    /**
     * 转换string类型的属性，把属性名为set集合
     * jdk9以下版本 {@link Properties#enumerateStringProperties }方法存在性能问题
     * keys() + get(k) 重复迭代,
     * jdk9之后改为entrySet遍历.
     *
     * @param properties 属性
     * @return 属性名set集合
     */
    private Set<String> stringPropertyNames(Properties properties) {
        Map<String, String> h = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> e : properties.entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            if (k instanceof String
                    && v instanceof String) {
                h.put((String) k, (String) v);
            }
        }
        return h.keySet();
    }

    @Override
    public synchronized void onRepositoryChange(String namespace, Properties newProperties) {
        // 如果新属性和之前的属性对象一样，直接返回
        if (newProperties.equals(configProperties.get())) {
            return;
        }

        // 更新并计算新属性的改变
        ConfigSourceType sourceType = configRepository.getSourceType();
        Properties newConfigProperties = propertiesFactory.getPropertiesInstance();
        newConfigProperties.putAll(newProperties);

        Map<String, ConfigChange> actualChanges = updateAndCalcConfigChanges(newConfigProperties, sourceType);

        // 如果改变为空，直接返回
        if (actualChanges.isEmpty()) {
            return;
        }

        // 否则触发配置改变监听器
        this.fireConfigChange(new ConfigChangeEvent(namespace, actualChanges));

        Tracer.logEvent("Apollo.Client.ConfigChanges", this.namespace);
    }

    /**
     * 更新配置属性对象缓存
     *
     * @param newConfigProperties 新配置属性
     * @param sourceType          配置来源
     */
    private void updateConfig(Properties newConfigProperties, ConfigSourceType sourceType) {
        configProperties.set(newConfigProperties);
        this.sourceType = sourceType;
    }

    /**
     * 更新配置缓存，并计算配置改变
     *
     * @param newConfigProperties 新配置属性
     * @param sourceType          配置来源
     * @return 配置改变map
     */
    private Map<String, ConfigChange> updateAndCalcConfigChanges(Properties newConfigProperties,
                                                                 ConfigSourceType sourceType) {
        // 计算配置改变
        List<ConfigChange> configChanges = calcPropertyChanges(namespace, configProperties.get(), newConfigProperties);

        // 仔细检查，因为DefaultConfig具有多个配置源
        ImmutableMap.Builder<String, ConfigChange> actualChanges = new ImmutableMap.Builder<>();

        // 使用本地缓存的旧值替代监听事件通知的旧值
        for (ConfigChange change : configChanges) {
            change.setOldValue(this.getProperty(change.getPropertyName(), change.getOldValue()));
        }

        // 更新配置文件属性缓存
        updateConfig(newConfigProperties, sourceType);

        // 清空本配置的所有缓存
        clearConfigCache();

        // 使用本地缓存可能存在的新值去替换监听事件的新值
        for (ConfigChange change : configChanges) {
            change.setNewValue(this.getProperty(change.getPropertyName(), change.getNewValue()));
            switch (change.getChangeType()) {
                case ADDED:
                    // 相等无需动
                    if (Objects.equals(change.getOldValue(), change.getNewValue())) {
                        break;
                    }

                    // 添加变为修改
                    if (change.getOldValue() != null) {
                        change.setChangeType(PropertyChangeType.MODIFIED);
                    }

                    // 缓存
                    actualChanges.put(change.getPropertyName(), change);
                    break;
                case MODIFIED:
                    // 真实的改变，直接缓存
                    if (!Objects.equals(change.getOldValue(), change.getNewValue())) {
                        actualChanges.put(change.getPropertyName(), change);
                    }
                    break;
                case DELETED:
                    // 新旧值相等，无需动
                    if (Objects.equals(change.getOldValue(), change.getNewValue())) {
                        break;
                    }

                    // 新增不等于空，删除变为修改
                    if (change.getNewValue() != null) {
                        change.setChangeType(PropertyChangeType.MODIFIED);
                    }

                    // 缓存
                    actualChanges.put(change.getPropertyName(), change);
                    break;
                default:
                    //do nothing
                    break;
            }
        }
        return actualChanges.build();
    }

    /**
     * 根据命名空间加载资源，形成属性
     *
     * @param namespace 命名空间
     * @return 属性
     */
    private Properties loadFromResource(String namespace) {
        String name = String.format("META-INF/config/%s.properties", namespace);
        InputStream in = ClassLoaderUtil.getLoader().getResourceAsStream(name);
        Properties properties = null;

        if (in != null) {
            properties = propertiesFactory.getPropertiesInstance();

            try {
                properties.load(in);
            } catch (IOException ex) {
                Tracer.logError(ex);
                logger.error("Load resource config for namespace {} failed", namespace, ex);
            } finally {
                try {
                    in.close();
                } catch (IOException ex) {
                    // ignore
                }
            }
        }

        return properties;
    }
}
