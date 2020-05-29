package com.ctrip.framework.apollo.common.dto;

import com.ctrip.framework.apollo.common.utils.InputValidator;

import javax.validation.constraints.Pattern;

/**
 * 命名空间dto
 */
public class NamespaceDTO extends BaseDTO {

    /**
     * 命名空间id
     */
    private long id;

    /**
     * 应用编号
     */
    private String appId;

    /**
     * 集群名称
     */
    private String clusterName;

    /**
     * 命名空间名称
     */
    @Pattern(
            regexp = InputValidator.CLUSTER_NAMESPACE_VALIDATOR,
            message = "Invalid Namespace format: " + InputValidator.INVALID_CLUSTER_NAMESPACE_MESSAGE
    )
    private String namespaceName;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAppId() {
        return appId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
    }
}
