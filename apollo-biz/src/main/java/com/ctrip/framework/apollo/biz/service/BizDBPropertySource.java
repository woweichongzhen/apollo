package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.ServerConfig;
import com.ctrip.framework.apollo.biz.repository.ServerConfigRepository;
import com.ctrip.framework.apollo.common.config.RefreshablePropertySource;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.foundation.Foundation;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * 供adminservice和configservice使用的业务DB可刷新属性源
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Component
public class BizDBPropertySource extends RefreshablePropertySource {

    private static final Logger logger = LoggerFactory.getLogger(BizDBPropertySource.class);

    @Autowired
    private ServerConfigRepository serverConfigRepository;

    public BizDBPropertySource(String name, Map<String, Object> source) {
        super(name, source);
    }

    public BizDBPropertySource() {
        super("DBConfig", Maps.newConcurrentMap());
    }

    /**
     * 获取当前所在的数据中心
     *
     * @return 当前IDC
     */
    String getCurrentDataCenter() {
        return Foundation.server().getDataCenter();
    }

    @Override
    protected void refresh() {
        Iterable<ServerConfig> dbConfigs = serverConfigRepository.findAll();
        Map<String, Object> newConfigs = Maps.newHashMap();

        // 先保存默认集群的配置
        for (ServerConfig config : dbConfigs) {
            if (Objects.equals(ConfigConsts.CLUSTER_NAME_DEFAULT, config.getCluster())) {
                newConfigs.put(config.getKey(), config.getValue());
            }
        }

        // 再保存与IDC相同的集群的配置
        String dataCenter = this.getCurrentDataCenter();
        for (ServerConfig config : dbConfigs) {
            if (Objects.equals(dataCenter, config.getCluster())) {
                newConfigs.put(config.getKey(), config.getValue());
            }
        }

        // 从环境变量/JVM启动参数中根据 apollo.cluster 提取相应的配置
        // -Dapollo.cluster=xxxx
        if (!Strings.isNullOrEmpty(System.getProperty(ConfigConsts.APOLLO_CLUSTER_KEY))) {
            String cluster = System.getProperty(ConfigConsts.APOLLO_CLUSTER_KEY);
            for (ServerConfig config : dbConfigs) {
                if (Objects.equals(cluster, config.getCluster())) {
                    newConfigs.put(config.getKey(), config.getValue());
                }
            }
        }

        //设置到环境属性源中
        for (Map.Entry<String, Object> config : newConfigs.entrySet()) {
            String key = config.getKey();
            Object value = config.getValue();

            if (source.get(key) == null) {
                logger.info("Load config from DB : {} = {}", key, value);
            } else if (!Objects.equals(source.get(key), value)) {
                logger.info("Load config from DB : {} = {}. Old value = {}",
                        key, value, source.get(key));
            }

            source.put(key, value);

        }

    }

}
