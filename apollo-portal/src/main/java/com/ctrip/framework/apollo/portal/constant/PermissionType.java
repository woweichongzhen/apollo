package com.ctrip.framework.apollo.portal.constant;

/**
 * 权限类型
 * 包含应用权限，集群权限，命名空间权限，用户权限
 */
public interface PermissionType {

    /**
     * 创建应用权限
     */
    String CREATE_APPLICATION = "CreateApplication";

    /**
     * 管理应用的master权限
     */
    String MANAGE_APP_MASTER = "ManageAppMaster";

    /**
     * 创建集群的权限
     */
    String CREATE_CLUSTER = "CreateCluster";

    /**
     * 创建命名空间的权限
     */
    String CREATE_NAMESPACE = "CreateNamespace";

    /**
     * 修改命名空间权限
     */
    String MODIFY_NAMESPACE = "ModifyNamespace";

    /**
     * 发布命名空间权限
     */
    String RELEASE_NAMESPACE = "ReleaseNamespace";

    /**
     * 分配用户权限
     */
    String ASSIGN_ROLE = "AssignRole";

}
