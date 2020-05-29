package com.ctrip.framework.apollo.portal.constant;

/**
 * 权限类型
 */
public interface PermissionType {

    /**
     * system level permission
     */
    String CREATE_APPLICATION = "CreateApplication";

    /**
     * 管理应用的master权限
     */
    String MANAGE_APP_MASTER = "ManageAppMaster";

    /**
     * 创建命名空间的权限
     */
    String CREATE_NAMESPACE = "CreateNamespace";

    /**
     * 创建集群的权限
     */
    String CREATE_CLUSTER = "CreateCluster";

    /**
     * 分配用户权限的权限
     */
    String ASSIGN_ROLE = "AssignRole";

    /**
     * namespace level permission
     */

    String MODIFY_NAMESPACE = "ModifyNamespace";

    /**
     * 发布命名空间权限
     */
    String RELEASE_NAMESPACE = "ReleaseNamespace";


}
