package com.ctrip.framework.apollo.biz.entity;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Date;

/**
 * 实例配置
 * instanceId + configAppId + ConfigNamespaceName 组成唯一索引，因为一个 Instance 可以使用多个 Namespace
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Entity
@Table(name = "InstanceConfig")
public class InstanceConfig {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private long id;

    /**
     * 实例id
     */
    @Column(name = "InstanceId")
    private long instanceId;

    /**
     * 配置的应用编号
     */
    @Column(name = "ConfigAppId", nullable = false)
    private String configAppId;

    /**
     * 配置的集群名称
     */
    @Column(name = "ConfigClusterName", nullable = false)
    private String configClusterName;

    /**
     * 配置的命名空间名称
     */
    @Column(name = "ConfigNamespaceName", nullable = false)
    private String configNamespaceName;

    /**
     * 发布key
     */
    @Column(name = "ReleaseKey", nullable = false)
    private String releaseKey;

    /**
     * 发布时间
     */
    @Column(name = "ReleaseDeliveryTime", nullable = false)
    private Date releaseDeliveryTime;

    /**
     * 创建时间
     */
    @Column(name = "DataChange_CreatedTime", nullable = false)
    private Date dataChangeCreatedTime;

    /**
     * 上次修改时间
     */
    @Column(name = "DataChange_LastTime")
    private Date dataChangeLastModifiedTime;

    @PrePersist
    protected void prePersist() {
        if (this.dataChangeCreatedTime == null) {
            dataChangeCreatedTime = new Date();
        }
        if (this.dataChangeLastModifiedTime == null) {
            dataChangeLastModifiedTime = dataChangeCreatedTime;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.dataChangeLastModifiedTime = new Date();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(long instanceId) {
        this.instanceId = instanceId;
    }

    public String getConfigAppId() {
        return configAppId;
    }

    public void setConfigAppId(String configAppId) {
        this.configAppId = configAppId;
    }

    public String getConfigNamespaceName() {
        return configNamespaceName;
    }

    public void setConfigNamespaceName(String configNamespaceName) {
        this.configNamespaceName = configNamespaceName;
    }

    public String getReleaseKey() {
        return releaseKey;
    }

    public void setReleaseKey(String releaseKey) {
        this.releaseKey = releaseKey;
    }

    public Date getDataChangeCreatedTime() {
        return dataChangeCreatedTime;
    }

    public void setDataChangeCreatedTime(Date dataChangeCreatedTime) {
        this.dataChangeCreatedTime = dataChangeCreatedTime;
    }

    public Date getDataChangeLastModifiedTime() {
        return dataChangeLastModifiedTime;
    }

    public void setDataChangeLastModifiedTime(Date dataChangeLastModifiedTime) {
        this.dataChangeLastModifiedTime = dataChangeLastModifiedTime;
    }

    public String getConfigClusterName() {
        return configClusterName;
    }

    public void setConfigClusterName(String configClusterName) {
        this.configClusterName = configClusterName;
    }

    public Date getReleaseDeliveryTime() {
        return releaseDeliveryTime;
    }

    public void setReleaseDeliveryTime(Date releaseDeliveryTime) {
        this.releaseDeliveryTime = releaseDeliveryTime;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .omitNullValues()
                .add("id", id)
                .add("configAppId", configAppId)
                .add("configClusterName", configClusterName)
                .add("configNamespaceName", configNamespaceName)
                .add("releaseKey", releaseKey)
                .add("dataChangeCreatedTime", dataChangeCreatedTime)
                .add("dataChangeLastModifiedTime", dataChangeLastModifiedTime)
                .toString();
    }
}
