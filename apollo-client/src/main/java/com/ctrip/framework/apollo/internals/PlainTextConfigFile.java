package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.core.ConfigConsts;

import java.util.Properties;

/**
 * 纯文本配置文件
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public abstract class PlainTextConfigFile extends AbstractConfigFile {

    public PlainTextConfigFile(String namespace, ConfigRepository configRepository) {
        super(namespace, configRepository);
    }

    @Override
    public String getContent() {
        if (!this.hasContent()) {
            return null;
        }
        return configProperties.get().getProperty(ConfigConsts.CONFIG_FILE_CONTENT_KEY);
    }

    @Override
    public boolean hasContent() {
        if (configProperties.get() == null) {
            return false;
        }
        return configProperties.get().containsKey(ConfigConsts.CONFIG_FILE_CONTENT_KEY);
    }

    @Override
    protected void update(Properties newProperties) {
        configProperties.set(newProperties);
    }
}
