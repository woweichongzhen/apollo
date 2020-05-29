package com.ctrip.framework.apollo.common.dto;

/**
 * 发布dto
 */
public class ReleaseDTO extends BaseDTO {

    /**
     * 发布id
     */
    private long id;

    /**
     * 发布key
     */
    private String releaseKey;

    /**
     * 发布名称
     */
    private String name;

    /**
     * 应用编号
     */
    private String appId;

    /**
     * 集群名称
     */
    private String clusterName;

    /**
     * 命名空间名称
     */
    private String namespaceName;

    /**
     * 所有配置map的json
     */
    private String configurations;

    private String comment;

    /**
     * 是否放弃
     */
    private boolean isAbandoned;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getReleaseKey() {
        return releaseKey;
    }

    public void setReleaseKey(String releaseKey) {
        this.releaseKey = releaseKey;
    }

    public String getAppId() {
        return appId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getComment() {
        return comment;
    }

    public String getConfigurations() {
        return configurations;
    }

    public String getName() {
        return name;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setConfigurations(String configurations) {
        this.configurations = configurations;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
    }

    public boolean isAbandoned() {
        return isAbandoned;
    }

    public void setAbandoned(boolean abandoned) {
        isAbandoned = abandoned;
    }
}
