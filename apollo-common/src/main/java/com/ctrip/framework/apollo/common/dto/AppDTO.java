package com.ctrip.framework.apollo.common.dto;

import com.ctrip.framework.apollo.common.utils.InputValidator;

import javax.validation.constraints.Pattern;

/**
 * 应用信息
 */
public class AppDTO extends BaseDTO {

    /**
     * 主键
     */
    private long id;

    /**
     * 应用名称
     */
    private String name;

    /**
     * 应用编号
     */
    @Pattern(
            regexp = InputValidator.CLUSTER_NAMESPACE_VALIDATOR,
            message = "Invalid AppId format: " + InputValidator.INVALID_CLUSTER_NAMESPACE_MESSAGE
    )
    private String appId;

    /**
     * 部门id
     */
    private String orgId;

    /**
     * 部门名称
     */
    private String orgName;

    /**
     * 拥有者名称
     */
    private String ownerName;

    /**
     * 拥有者邮箱
     */
    private String ownerEmail;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAppId() {
        return appId;
    }

    public String getName() {
        return name;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

}
