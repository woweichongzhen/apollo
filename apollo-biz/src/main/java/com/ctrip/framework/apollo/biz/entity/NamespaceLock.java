package com.ctrip.framework.apollo.biz.entity;

import com.ctrip.framework.apollo.common.entity.BaseEntity;
import org.hibernate.annotations.Where;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 命名空间锁
 * <p>
 * 可通过设置 ConfigDB 的 ServerConfig 的 "namespace.lock.switch" 为 "true" 开启。效果如下：
 * 一次配置修改只能是一个人
 * 一次配置发布只能是另一个人
 * <p>
 * 开启后，一次配置修改并发布，需要两个人。
 * <p>
 * 默认为 "false" ，即关闭
 */
@Entity
@Table(name = "NamespaceLock")
@Where(clause = "isDeleted = 0")
public class NamespaceLock extends BaseEntity {

    /**
     * 命名空间id
     * 唯一索引，保证并发写操作时，同一个命名空间id只能有一条命名空间锁记录
     */
    @Column(name = "NamespaceId")
    private long namespaceId;

    public long getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(long namespaceId) {
        this.namespaceId = namespaceId;
    }
}
