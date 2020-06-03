package com.ctrip.framework.apollo.biz.entity;

import com.ctrip.framework.apollo.common.entity.BaseEntity;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 服务配置
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Entity
@Table(name = "ServerConfig")
@SQLDelete(sql = "Update ServerConfig set isDeleted = 1 where id = ?")
@Where(clause = "isDeleted = 0")
public class ServerConfig extends BaseEntity {

    /**
     * 键
     */
    @Column(name = "Key", nullable = false)
    private String key;

    /**
     * 集群名
     * 用于多机房部署，希望configservice和adminservice只向同机房部署，所以需要该字段，
     * configservice和adminservice会读取所在机器的
     * /opt/settings/server.properties（Mac/Linux）
     * 或
     * C:\opt\settings\server.properties（Windows）
     * 中的 idc 属性，如果该 idc 有对应的eureka.service.url 配置，那么就会向该机房的 eureka 注册 。
     * 默认情况下，使用 "default" 集群
     */
    @Column(name = "Cluster", nullable = false)
    private String cluster;

    /**
     * 值
     */
    @Column(name = "Value", nullable = false)
    private String value;

    /**
     * 备注
     */
    @Column(name = "Comment", nullable = false)
    private String comment;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    @Override
    public String toString() {
        return toStringHelper().add("key", key).add("value", value).add("comment", comment).toString();
    }
}
