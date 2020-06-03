package com.ctrip.framework.apollo.portal.entity.po;

import com.ctrip.framework.apollo.common.entity.BaseEntity;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 角色实体类
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Entity
@Table(name = "Role")
@SQLDelete(sql = "Update Role set isDeleted = 1 where id = ?")
@Where(clause = "isDeleted = 0")
public class Role extends BaseEntity {

    /**
     * 角色名称
     * <p>
     * 目前有三种类型角色：
     * App 管理员，格式为 "Master + AppId" ，例如："Master+100004458" 。
     * Namespace 修改管理员，格式为 "ModifyNamespace + AppId + NamespaceName" ，例如："ModifyNamespace+100004458+application" 。
     * Namespace 发布管理员，格式为 "ReleaseNamespace + AppId + NamespaceName" ，例如："ReleaseNamespace+100004458+application" 。
     */
    @Column(name = "RoleName", nullable = false)
    private String roleName;

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
