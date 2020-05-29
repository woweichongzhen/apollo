package com.ctrip.framework.apollo.configservice.service.config;

import com.ctrip.framework.apollo.biz.entity.Release;
import com.ctrip.framework.apollo.biz.message.ReleaseMessageListener;
import com.ctrip.framework.apollo.core.dto.ApolloNotificationMessages;

/**
 * 配置服务接口
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigService extends ReleaseMessageListener {

    /**
     * 加载配置
     * Load config
     *
     * @param clientAppId       the client's app id 客户端应用编号
     * @param clientIp          the client ip 客户端ip
     * @param configAppId       the requested config's app id 请求配置的应用编号
     * @param configClusterName the requested config's cluster name 集群名称
     * @param configNamespace   the requested config's namespace name 命名空间名称
     * @param dataCenter        the client data center 数据中心
     * @param clientMessages    the messages received in client side 客户端消息
     * @return the Release 最新的发布版本
     */
    Release loadConfig(String clientAppId, String clientIp, String configAppId, String
            configClusterName, String configNamespace, String dataCenter, ApolloNotificationMessages clientMessages);
}
