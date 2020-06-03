package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.ConfigFileChangeListener;
import com.ctrip.framework.apollo.PropertiesCompatibleConfigFile;
import com.ctrip.framework.apollo.enums.ConfigSourceType;
import com.ctrip.framework.apollo.model.ConfigFileChangeEvent;
import com.google.common.base.Preconditions;

import java.util.Properties;

/**
 * 兼容属性文件的配置仓库
 */
public class PropertiesCompatibleFileConfigRepository extends AbstractConfigRepository implements ConfigFileChangeListener {

    /**
     * 兼容属性的配置文件，比如yaml，yml
     */
    private final PropertiesCompatibleConfigFile configFile;

    /**
     * 缓存的属性
     */
    private volatile Properties cachedProperties;

    public PropertiesCompatibleFileConfigRepository(PropertiesCompatibleConfigFile configFile) {
        this.configFile = configFile;
        this.configFile.addChangeListener(this);
        this.trySync();
    }

    @Override
    protected synchronized void sync() {
        // 当前配置文件转换为属性
        Properties current = configFile.asProperties();

        Preconditions.checkState(current != null, "PropertiesCompatibleConfigFile.asProperties should never return " +
                "null");

        // 并进行缓存，触发监听器改变
        if (cachedProperties != current) {
            cachedProperties = current;
            this.fireRepositoryChange(configFile.getNamespace(), cachedProperties);
        }
    }

    @Override
    public Properties getConfig() {
        if (cachedProperties == null) {
            sync();
        }
        return cachedProperties;
    }

    @Override
    public void setUpstreamRepository(ConfigRepository upstreamConfigRepository) {
    }

    @Override
    public ConfigSourceType getSourceType() {
        return configFile.getSourceType();
    }

    @Override
    public void onChange(ConfigFileChangeEvent changeEvent) {
        this.trySync();
    }
}
