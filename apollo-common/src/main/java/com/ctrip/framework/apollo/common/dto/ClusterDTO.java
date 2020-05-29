package com.ctrip.framework.apollo.common.dto;

import com.ctrip.framework.apollo.common.utils.InputValidator;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 集群信息
 */
public class ClusterDTO extends BaseDTO {

    /**
     * 自增id
     */
    private long id;

    /**
     * 集群名称
     */
    @NotBlank(message = "cluster name cannot be blank")
    @Pattern(
            regexp = InputValidator.CLUSTER_NAMESPACE_VALIDATOR,
            message = "Invalid Cluster format: " + InputValidator.INVALID_CLUSTER_NAMESPACE_MESSAGE
    )
    private String name;

    /**
     * 应用编号
     */
    @NotBlank(message = "appId cannot be blank")
    private String appId;

    /**
     * 父集群id
     */
    private long parentClusterId;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

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

    public long getParentClusterId() {
        return parentClusterId;
    }

    public void setParentClusterId(long parentClusterId) {
        this.parentClusterId = parentClusterId;
    }
}
