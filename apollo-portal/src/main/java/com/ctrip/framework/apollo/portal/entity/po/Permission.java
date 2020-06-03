package com.ctrip.framework.apollo.portal.entity.po;

import com.ctrip.framework.apollo.common.entity.BaseEntity;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 权限
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Entity
@Table(name = "Permission")
@SQLDelete(sql = "Update Permission set isDeleted = 1 where id = ?")
@Where(clause = "isDeleted = 0")
public class Permission extends BaseEntity {

    /**
     * 权限类型
     * {@link com.ctrip.framework.apollo.portal.constant.PermissionType}
     */
    @Column(name = "PermissionType", nullable = false)
    private String permissionType;

    /**
     * 目标id
     * <p>
     * App 级别时，targetId 指向 "App 编号"。
     * Namespace 级别时，targetId 指向 "App 编号 + Namespace 名字"。
     * 为什么不是 Namespace 的编号？ Namespace 级别，是所有环境 + 所有集群都有权限，所以不能具体某个 Namespace 。
     */
    @Column(name = "TargetId", nullable = false)
    private String targetId;

    public String getPermissionType() {
        return permissionType;
    }

    public void setPermissionType(String permissionType) {
        this.permissionType = permissionType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }
}
