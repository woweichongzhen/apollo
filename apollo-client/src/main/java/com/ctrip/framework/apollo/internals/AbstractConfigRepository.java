package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.ctrip.framework.apollo.util.factory.PropertiesFactory;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

/**
 * 抽象的配置读取仓库
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public abstract class AbstractConfigRepository implements ConfigRepository {

    private static final Logger logger = LoggerFactory.getLogger(AbstractConfigRepository.class);

    /**
     * 仓库改变监听器
     */
    private final List<RepositoryChangeListener> mListeners = Lists.newCopyOnWriteArrayList();

    /**
     * 属性生产仓库
     */
    protected PropertiesFactory propertiesFactory = ApolloInjector.getInstance(PropertiesFactory.class);

    /**
     * 尝试同步配置
     *
     * @return true同步成功，false同步失败
     */
    protected boolean trySync() {
        try {
            sync();
            return true;
        } catch (Throwable ex) {
            Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
            logger
                    .warn("Sync config failed, will retry. Repository {}, reason: {}", this.getClass(), ExceptionUtil
                            .getDetailMessage(ex));
        }
        return false;
    }

    /**
     * 同步数据
     */
    protected abstract void sync();

    @Override
    public void addChangeListener(RepositoryChangeListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    @Override
    public void removeChangeListener(RepositoryChangeListener listener) {
        mListeners.remove(listener);
    }

    /**
     * 触发监听器改变
     *
     * @param namespace     命名空间
     * @param newProperties 新的属性
     */
    protected void fireRepositoryChange(String namespace, Properties newProperties) {
        for (RepositoryChangeListener listener : mListeners) {
            try {
                // 触发监听器改变
                listener.onRepositoryChange(namespace, newProperties);
            } catch (Throwable ex) {
                Tracer.logError(ex);
                logger.error("Failed to invoke repository change listener {}", listener.getClass(), ex);
            }
        }
    }
}
