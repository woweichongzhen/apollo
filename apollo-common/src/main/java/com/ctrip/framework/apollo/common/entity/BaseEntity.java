package com.ctrip.framework.apollo.common.entity;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

import javax.persistence.*;
import java.util.Date;

/**
 * 实体继承，id，isDeleted，数据新增者，时间，数据改变时间，改变人
 * <p>
 * {@link MappedSuperclass} 将实体类的多个属性分别封装到不同的非实体类中
 * 属性都将映射到其子类的数据库表字段中
 * 不能再标注 {@link Entity}或 {@link Table} 注解，也无需实现序列化接口
 * <p>
 * {@link Inheritance} 继承策略是每个类层次结构映射一张表
 * 支持双向的一对多关联，这里不支持IDENTITY生成器策略
 * 因为存在多态查询，所以id在继承关系的表中必须是唯一的。这就意味着不能用AUTO和IDENTITY生成器
 */
@MappedSuperclass
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class BaseEntity {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private long id;

    /**
     * 是否删除，默认false
     */
    @Column(name = "IsDeleted", columnDefinition = "Bit default '0'")
    protected boolean isDeleted = false;

    /**
     * 创建者
     * 例如在 Portal 系统中，使用系统的管理员账号，即 UserPO.username 字段
     */
    @Column(name = "DataChange_CreatedBy", nullable = false)
    private String dataChangeCreatedBy;

    /**
     * 创建时间
     */
    @Column(name = "DataChange_CreatedTime", nullable = false)
    private Date dataChangeCreatedTime;

    /**
     * 最后修改人
     * 例如在 Portal 系统中，使用系统的管理员账号，即 UserPO.username 字段
     */
    @Column(name = "DataChange_LastModifiedBy")
    private String dataChangeLastModifiedBy;

    /**
     * 最后修改时间
     * 例如在 Portal 系统中，使用系统的管理员账号，即 UserPO.username 字段
     */
    @Column(name = "DataChange_LastTime")
    private Date dataChangeLastModifiedTime;

    public String getDataChangeCreatedBy() {
        return dataChangeCreatedBy;
    }

    public Date getDataChangeCreatedTime() {
        return dataChangeCreatedTime;
    }

    public String getDataChangeLastModifiedBy() {
        return dataChangeLastModifiedBy;
    }

    public Date getDataChangeLastModifiedTime() {
        return dataChangeLastModifiedTime;
    }

    public long getId() {
        return id;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDataChangeCreatedBy(String dataChangeCreatedBy) {
        this.dataChangeCreatedBy = dataChangeCreatedBy;
    }

    public void setDataChangeCreatedTime(Date dataChangeCreatedTime) {
        this.dataChangeCreatedTime = dataChangeCreatedTime;
    }

    public void setDataChangeLastModifiedBy(String dataChangeLastModifiedBy) {
        this.dataChangeLastModifiedBy = dataChangeLastModifiedBy;
    }

    public void setDataChangeLastModifiedTime(Date dataChangeLastModifiedTime) {
        this.dataChangeLastModifiedTime = dataChangeLastModifiedTime;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public void setId(long id) {
        this.id = id;
    }

    /**
     * 保存前调用，设置创建时间和最后修改时间
     */
    @PrePersist
    protected void prePersist() {
        if (this.dataChangeCreatedTime == null) {
            dataChangeCreatedTime = new Date();
        }
        if (this.dataChangeLastModifiedTime == null) {
            dataChangeLastModifiedTime = new Date();
        }
    }

    /**
     * 更新前调用，设置最后修改时间
     */
    @PreUpdate
    protected void preUpdate() {
        this.dataChangeLastModifiedTime = new Date();
    }

    /**
     * 删除前调用，设置最后修改时间
     */
    @PreRemove
    protected void preRemove() {
        this.dataChangeLastModifiedTime = new Date();
    }

    /**
     * 转换String
     *
     * @return guava辅助类
     */
    protected ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .omitNullValues()
                .add("id", id)
                .add("dataChangeCreatedBy", dataChangeCreatedBy)
                .add("dataChangeCreatedTime", dataChangeCreatedTime)
                .add("dataChangeLastModifiedBy", dataChangeLastModifiedBy)
                .add("dataChangeLastModifiedTime", dataChangeLastModifiedTime);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }
}
