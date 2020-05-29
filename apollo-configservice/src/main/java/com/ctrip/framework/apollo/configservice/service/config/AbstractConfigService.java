package com.ctrip.framework.apollo.configservice.service.config;

import com.ctrip.framework.apollo.biz.entity.Release;
import com.ctrip.framework.apollo.biz.grayReleaseRule.GrayReleaseRulesHolder;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.dto.ApolloNotificationMessages;
import com.google.common.base.Strings;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

/**
 * 配置服务
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public abstract class AbstractConfigService implements ConfigService {

    @Autowired
    private GrayReleaseRulesHolder grayReleaseRulesHolder;

    @Override
    public Release loadConfig(String clientAppId, String clientIp, String configAppId, String configClusterName,
                              String configNamespace, String dataCenter, ApolloNotificationMessages clientMessages) {
        // load from specified cluster fist
        // 从指定集群中加载
        if (!Objects.equals(ConfigConsts.CLUSTER_NAME_DEFAULT, configClusterName)) {
            Release clusterRelease = findRelease(clientAppId, clientIp, configAppId, configClusterName, configNamespace,
                    clientMessages);

            if (!Objects.isNull(clusterRelease)) {
                return clusterRelease;
            }
        }

        // try to load via data center
        // 尝试加载数据中心 IDC 的集群发布配置
        if (!Strings.isNullOrEmpty(dataCenter) && !Objects.equals(dataCenter, configClusterName)) {
            Release dataCenterRelease = findRelease(clientAppId, clientIp, configAppId, dataCenter, configNamespace,
                    clientMessages);
            if (!Objects.isNull(dataCenterRelease)) {
                return dataCenterRelease;
            }
        }

        // fallback to default release
        // 都没有就使用默认的集群配置
        return findRelease(clientAppId, clientIp, configAppId, ConfigConsts.CLUSTER_NAME_DEFAULT, configNamespace,
                clientMessages);
    }

    /**
     * 查找指定的集群的命名空间发布配置
     * <p>
     * Find release
     *
     * @param clientAppId       the client's app id
     * @param clientIp          the client ip
     * @param configAppId       the requested config's app id
     * @param configClusterName the requested config's cluster name
     * @param configNamespace   the requested config's namespace name
     * @param clientMessages    the messages received in client side
     * @return the release
     */
    private Release findRelease(String clientAppId, String clientIp, String configAppId, String configClusterName,
                                String configNamespace, ApolloNotificationMessages clientMessages) {
        // 读取灰度发布编号
        Long grayReleaseId = grayReleaseRulesHolder.findReleaseIdFromGrayReleaseRule(
                clientAppId,
                clientIp,
                configAppId,
                configClusterName,
                configNamespace);

        Release release = null;

        // 获取灰度激活的发布版本
        if (grayReleaseId != null) {
            release = findActiveOne(grayReleaseId, clientMessages);
        }

        // 获取最新的有效的发布版本
        if (release == null) {
            release = findLatestActiveRelease(configAppId, configClusterName, configNamespace, clientMessages);
        }

        return release;
    }

    /**
     * 获得指定编号，并且有效的 Release 对象
     * <p>
     * Find active release by id
     */
    protected abstract Release findActiveOne(long id, ApolloNotificationMessages clientMessages);

    /**
     * 查找最近激活的发布版本
     * <p>
     * Find active release by app id, cluster name and namespace name
     */
    protected abstract Release findLatestActiveRelease(String configAppId, String configClusterName,
                                                       String configNamespaceName,
                                                       ApolloNotificationMessages clientMessages);
}
