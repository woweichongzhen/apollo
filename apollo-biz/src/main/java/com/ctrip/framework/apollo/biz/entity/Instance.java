package com.ctrip.framework.apollo.biz.entity;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Date;

/**
 * 实例信息
 * appId + clusterName + dataCenter + ip 组成唯一索引，通过这四个字段唯一一个客户端实例。
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Entity
@Table(name = "Instance")
public class Instance {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private long id;

    /**
     * 应用编号
     */
    @Column(name = "AppId", nullable = false)
    private String appId;

    /**
     * 集群名称
     */
    @Column(name = "ClusterName", nullable = false)
    private String clusterName;

    /**
     * 数据中心IDC
     */
    @Column(name = "DataCenter", nullable = false)
    private String dataCenter;

    /**
     * 实例ip（客户端）
     */
    @Column(name = "Ip", nullable = false)
    private String ip;

    /**
     * 数据创建时间
     */
    @Column(name = "DataChange_CreatedTime", nullable = false)
    private Date dataChangeCreatedTime;

    /**
     * 数据最后修改时间
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

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getDataCenter() {
        return dataCenter;
    }

    public void setDataCenter(String dataCenter) {
        this.dataCenter = dataCenter;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
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

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .omitNullValues()
                .add("id", id)
                .add("appId", appId)
                .add("clusterName", clusterName)
                .add("dataCenter", dataCenter)
                .add("ip", ip)
                .add("dataChangeCreatedTime", dataChangeCreatedTime)
                .add("dataChangeLastModifiedTime", dataChangeLastModifiedTime)
                .toString();
    }
}
