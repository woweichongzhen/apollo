package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.common.config.RefreshablePropertySource;
import com.ctrip.framework.apollo.portal.entity.po.ServerConfig;
import com.ctrip.framework.apollo.portal.repository.ServerConfigRepository;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * 基于portalDB实现刷新属性源
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Component
public class PortalDBPropertySource extends RefreshablePropertySource {

    private static final Logger logger = LoggerFactory.getLogger(PortalDBPropertySource.class);

    @Autowired
    private ServerConfigRepository serverConfigRepository;

    public PortalDBPropertySource(String name, Map<String, Object> source) {
        super(name, source);
    }

    public PortalDBPropertySource() {
        super("DBConfig", Maps.newConcurrentMap());
    }

    @Override
    protected void refresh() {
        // 遍历所有服务配置
        Iterable<ServerConfig> dbConfigs = serverConfigRepository.findAll();
        for (ServerConfig config : dbConfigs) {
            String key = config.getKey();
            Object value = config.getValue();

            if (source.isEmpty()) {
                logger.info("Load config from DB : {} = {}", key, value);
            } else if (!Objects.equals(source.get(key), value)) {
                logger.info("Load config from DB : {} = {}. Old value = {}",
                        key, value, source.get(key));
            }

            // 把数据库配置加载到属性源中，并打印相关日志
            source.put(key, value);
        }
    }


}
