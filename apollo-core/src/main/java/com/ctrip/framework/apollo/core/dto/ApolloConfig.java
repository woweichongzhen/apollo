package com.ctrip.framework.apollo.core.dto;

import java.util.Map;

/**
 * 客户端获取的全量apollo配置
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ApolloConfig {

    /**
     * 应用编号
     */
    private String appId;

    /**
     * 集群名称
     */
    private String cluster;

    /**
     * 命名空间名称
     */
    private String namespaceName;

    /**
     * 配置map
     */
    private Map<String, String> configurations;

    /**
     * 发布key
     * 如果 {@link #configurations} 是多个 Release ，那 Release Key 是多个 Release.releaseKey 拼接，使用 '+' 拼接
     */
    private String releaseKey;

    public ApolloConfig() {
    }

    public ApolloConfig(String appId,
                        String cluster,
                        String namespaceName,
                        String releaseKey) {
        this.appId = appId;
        this.cluster = cluster;
        this.namespaceName = namespaceName;
        this.releaseKey = releaseKey;
    }

    public String getAppId() {
        return appId;
    }

    public String getCluster() {
        return cluster;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public String getReleaseKey() {
        return releaseKey;
    }

    public Map<String, String> getConfigurations() {
        return configurations;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
    }

    public void setReleaseKey(String releaseKey) {
        this.releaseKey = releaseKey;
    }

    public void setConfigurations(Map<String, String> configurations) {
        this.configurations = configurations;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ApolloConfig{");
        sb.append("appId='").append(appId).append('\'');
        sb.append(", cluster='").append(cluster).append('\'');
        sb.append(", namespaceName='").append(namespaceName).append('\'');
        sb.append(", configurations=").append(configurations);
        sb.append(", releaseKey='").append(releaseKey).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
