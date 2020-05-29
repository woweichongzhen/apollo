package com.ctrip.framework.apollo.common.dto;

import java.util.Date;

/**
 * dto对象父类
 */
public class BaseDTO {

    /**
     * 创建者
     */
    protected String dataChangeCreatedBy;

    /**
     * 上次修改者
     */
    protected String dataChangeLastModifiedBy;

    /**
     * 创建时间
     */
    protected Date dataChangeCreatedTime;

    /**
     * 上次修改时间
     */
    protected Date dataChangeLastModifiedTime;

    public String getDataChangeCreatedBy() {
        return dataChangeCreatedBy;
    }

    public void setDataChangeCreatedBy(String dataChangeCreatedBy) {
        this.dataChangeCreatedBy = dataChangeCreatedBy;
    }

    public String getDataChangeLastModifiedBy() {
        return dataChangeLastModifiedBy;
    }

    public void setDataChangeLastModifiedBy(String dataChangeLastModifiedBy) {
        this.dataChangeLastModifiedBy = dataChangeLastModifiedBy;
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
}
