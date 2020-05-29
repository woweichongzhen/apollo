package com.ctrip.framework.apollo.portal.entity.model;

import com.ctrip.framework.apollo.common.utils.InputValidator;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.util.Set;

/**
 * 应用模型
 */
public class AppModel {

    /**
     * 应用名称
     */
    @NotBlank(message = "name cannot be blank")
    private String name;

    /**
     * 应用编号
     */
    @NotBlank(message = "appId cannot be blank")
    @Pattern(
            regexp = InputValidator.CLUSTER_NAMESPACE_VALIDATOR,
            message = "Invalid AppId format: " + InputValidator.INVALID_CLUSTER_NAMESPACE_MESSAGE
    )
    private String appId;

    /**
     * 部门id
     */
    @NotBlank(message = "orgId cannot be blank")
    private String orgId;

    /**
     * 部门名称
     */
    @NotBlank(message = "orgName cannot be blank")
    private String orgName;

    /**
     * 拥有者名称
     */
    @NotBlank(message = "ownerName cannot be blank")
    private String ownerName;

    /**
     * 需要授权的应用管理员
     */
    private Set<String> admins;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Set<String> getAdmins() {
        return admins;
    }

    public void setAdmins(Set<String> admins) {
        this.admins = admins;
    }
}
