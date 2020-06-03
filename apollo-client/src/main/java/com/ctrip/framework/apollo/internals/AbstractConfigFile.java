package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.ConfigFile;
import com.ctrip.framework.apollo.ConfigFileChangeListener;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.enums.ConfigSourceType;
import com.ctrip.framework.apollo.enums.PropertyChangeType;
import com.ctrip.framework.apollo.model.ConfigFileChangeEvent;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.ctrip.framework.apollo.util.factory.PropertiesFactory;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 抽象的配置文件
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public abstract class AbstractConfigFile implements ConfigFile, RepositoryChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(AbstractConfigFile.class);

    /**
     * 配置回调更改触发线程池
     */
    private static final ExecutorService EXECUTOR_SERVICE;

    /**
     * 配置仓库
     */
    protected final ConfigRepository configRepository;

    /**
     * 本配置的命名空间
     */
    protected final String namespace;

    /**
     * 配置属性原子引用
     */
    protected final AtomicReference<Properties> configProperties;

    /**
     * 配置文件变更监听器
     */
    private final List<ConfigFileChangeListener> listeners = Lists.newCopyOnWriteArrayList();

    /**
     * 属性实例生产仓库
     */
    protected final PropertiesFactory propertiesFactory;

    /**
     * 配置来源类型
     */
    private volatile ConfigSourceType sourceType = ConfigSourceType.NONE;

    static {
        EXECUTOR_SERVICE = Executors.newCachedThreadPool(
                ApolloThreadFactory.create("ConfigFile", true));
    }

    public AbstractConfigFile(String namespace, ConfigRepository configRepository) {
        this.configRepository = configRepository;
        this.namespace = namespace;
        configProperties = new AtomicReference<>();
        propertiesFactory = ApolloInjector.getInstance(PropertiesFactory.class);

        // 初始化配置文件
        this.initialize();
    }

    /**
     * 初始化配置文件
     */
    private void initialize() {
        try {
            // 加载配置，设置配置属性和来源引用
            configProperties.set(configRepository.getConfig());
            sourceType = configRepository.getSourceType();
        } catch (Throwable ex) {
            Tracer.logError(ex);
            logger.warn("Init Apollo Config File failed - namespace: {}, reason: {}.",
                    namespace, ExceptionUtil.getDetailMessage(ex));
        } finally {
            // 注册状态改变监听器
            configRepository.addChangeListener(this);
        }
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    /**
     * 更新
     *
     * @param newProperties 新属性
     */
    protected abstract void update(Properties newProperties);

    @Override
    public synchronized void onRepositoryChange(String namespace, Properties newProperties) {
        if (newProperties.equals(configProperties.get())) {
            return;
        }

        // 获取旧内容
        String oldValue = getContent();

        // 更新新属性、内容、来源类型
        Properties newConfigProperties = propertiesFactory.getPropertiesInstance();
        newConfigProperties.putAll(newProperties);
        update(newProperties);
        sourceType = configRepository.getSourceType();

        // 获取新内容
        String newValue = getContent();
        PropertyChangeType changeType = PropertyChangeType.MODIFIED;

        // 如果老内容为空，则是新增，如果新内容为空，则是删除
        if (oldValue == null) {
            changeType = PropertyChangeType.ADDED;
        } else if (newValue == null) {
            changeType = PropertyChangeType.DELETED;
        }

        // 触发配置文件改变事件
        this.fireConfigChange(new ConfigFileChangeEvent(this.namespace, oldValue, newValue, changeType));

        Tracer.logEvent("Apollo.Client.ConfigChanges", this.namespace);
    }

    @Override
    public void addChangeListener(ConfigFileChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public boolean removeChangeListener(ConfigFileChangeListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public ConfigSourceType getSourceType() {
        return sourceType;
    }

    /**
     * 触发配置文件改变
     *
     * @param changeEvent 配置文件改变事件
     */
    private void fireConfigChange(final ConfigFileChangeEvent changeEvent) {
        for (final ConfigFileChangeListener listener : listeners) {
            EXECUTOR_SERVICE.submit(new Runnable() {
                @Override
                public void run() {
                    String listenerName = listener.getClass().getName();
                    Transaction transaction = Tracer.newTransaction("Apollo.ConfigFileChangeListener", listenerName);
                    try {
                        // 异步触发改变
                        listener.onChange(changeEvent);
                        transaction.setStatus(Transaction.SUCCESS);
                    } catch (Throwable ex) {
                        transaction.setStatus(ex);
                        Tracer.logError(ex);
                        logger.error("Failed to invoke config file change listener {}", listenerName, ex);
                    } finally {
                        transaction.complete();
                    }
                }
            });
        }
    }
}
