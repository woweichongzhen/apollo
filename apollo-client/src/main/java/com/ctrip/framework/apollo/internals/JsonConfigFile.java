package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;

/**
 * json配置文件
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class JsonConfigFile extends PlainTextConfigFile {
    public JsonConfigFile(String namespace,
                          ConfigRepository configRepository) {
        super(namespace, configRepository);
    }

    @Override
    public ConfigFileFormat getConfigFileFormat() {
        return ConfigFileFormat.JSON;
    }
}
