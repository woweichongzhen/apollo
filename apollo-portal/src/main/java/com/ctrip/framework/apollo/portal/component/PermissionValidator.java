package com.ctrip.framework.apollo.portal.component;

import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.constant.PermissionType;
import com.ctrip.framework.apollo.portal.service.AppNamespaceService;
import com.ctrip.framework.apollo.portal.service.RolePermissionService;
import com.ctrip.framework.apollo.portal.service.SystemRoleManagerService;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.ctrip.framework.apollo.portal.util.RoleUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 权限校验
 */
@Component("permissionValidator")
public class PermissionValidator {

    private final UserInfoHolder userInfoHolder;
    private final RolePermissionService rolePermissionService;
    private final PortalConfig portalConfig;
    private final AppNamespaceService appNamespaceService;
    private final SystemRoleManagerService systemRoleManagerService;

    @Autowired
    public PermissionValidator(
            final UserInfoHolder userInfoHolder,
            final RolePermissionService rolePermissionService,
            final PortalConfig portalConfig,
            final AppNamespaceService appNamespaceService,
            final SystemRoleManagerService systemRoleManagerService) {
        this.userInfoHolder = userInfoHolder;
        this.rolePermissionService = rolePermissionService;
        this.portalConfig = portalConfig;
        this.appNamespaceService = appNamespaceService;
        this.systemRoleManagerService = systemRoleManagerService;
    }

    /**
     * 是否有修改命名空间的权限
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @return true有，fasle没有
     */
    public boolean hasModifyNamespacePermission(String appId, String namespaceName) {
        return rolePermissionService.userHasPermission(
                userInfoHolder.getUser().getUserId(),
                PermissionType.MODIFY_NAMESPACE,
                RoleUtils.buildNamespaceTargetId(appId, namespaceName));
    }

    /**
     * 是否有环境修改命名空间的权限
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @param env           环境
     * @return true有，false没有
     */
    public boolean hasModifyNamespacePermission(String appId, String namespaceName, String env) {
        // 有修改命名空间的权限，或指定环境的权限
        return hasModifyNamespacePermission(appId, namespaceName) ||
                rolePermissionService.userHasPermission(
                        userInfoHolder.getUser().getUserId(),
                        PermissionType.MODIFY_NAMESPACE,
                        RoleUtils.buildNamespaceTargetId(appId, namespaceName, env));
    }

    /**
     * 是否有发布命名空间的权限
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @return true有，false没有
     */
    public boolean hasReleaseNamespacePermission(String appId, String namespaceName) {
        return rolePermissionService.userHasPermission(
                userInfoHolder.getUser().getUserId(),
                PermissionType.RELEASE_NAMESPACE,
                RoleUtils.buildNamespaceTargetId(appId, namespaceName));
    }

    /**
     * 是否有发布命名空间的权限
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @param env           环境
     * @return true有，false没有
     */
    public boolean hasReleaseNamespacePermission(String appId, String namespaceName, String env) {
        return hasReleaseNamespacePermission(appId, namespaceName) ||
                rolePermissionService.userHasPermission(
                        userInfoHolder.getUser().getUserId(),
                        PermissionType.RELEASE_NAMESPACE,
                        RoleUtils.buildNamespaceTargetId(appId, namespaceName, env));
    }

    /**
     * 是否拥有删除命名空间的权限（分配角色或超级用户）
     *
     * @param appId 应用编号
     * @return true有，false没有
     */
    public boolean hasDeleteNamespacePermission(String appId) {
        return hasAssignRolePermission(appId) || isSuperAdmin();
    }

    /**
     * 是否拥有操作命名空间权限的权限（修改和发布）
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @return true有，false没有
     */
    public boolean hasOperateNamespacePermission(String appId, String namespaceName) {
        return hasModifyNamespacePermission(appId, namespaceName)
                || hasReleaseNamespacePermission(appId, namespaceName);
    }

    /**
     * 是否拥有操作命名空间权限的权限（修改和发布）
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @param env           环境
     * @return true有，false没有
     */
    public boolean hasOperateNamespacePermission(String appId, String namespaceName, String env) {
        return hasOperateNamespacePermission(appId, namespaceName) ||
                hasModifyNamespacePermission(appId, namespaceName, env) ||
                hasReleaseNamespacePermission(appId, namespaceName, env);
    }

    /**
     * 分配角色的权限
     *
     * @param appId 应用编号
     * @return true有，false没有
     */
    public boolean hasAssignRolePermission(String appId) {
        return rolePermissionService.userHasPermission(userInfoHolder.getUser().getUserId(),
                PermissionType.ASSIGN_ROLE,
                appId);
    }

    /**
     * 是否有创建命名空间的权限
     *
     * @param appId 应用编号
     * @return ture有，false没有
     */
    public boolean hasCreateNamespacePermission(String appId) {
        return rolePermissionService.userHasPermission(userInfoHolder.getUser().getUserId(),
                PermissionType.CREATE_NAMESPACE,
                appId);
    }

    /**
     * 是否拥有创建应用命名空间的权限
     *
     * @param appId        应用编号
     * @param appNamespace 应用命名空间
     * @return true允许创建，false不允许
     */
    public boolean hasCreateAppNamespacePermission(String appId, AppNamespace appNamespace) {
        // 是否公开的命名空间
        boolean isPublicAppNamespace = appNamespace.isPublic();
        // 应用管理员允许创建私有命名空间 或者 命名空间为公开类型
        // 再判断是否应用有这个权限
        if (portalConfig.canAppAdminCreatePrivateNamespace() || isPublicAppNamespace) {
            return hasCreateNamespacePermission(appId);
        }

        // 是否是超级用户
        return isSuperAdmin();
    }

    /**
     * 是否拥有创建集群的权限
     *
     * @param appId 应用编号
     * @return true有，false没有
     */
    public boolean hasCreateClusterPermission(String appId) {
        return rolePermissionService.userHasPermission(userInfoHolder.getUser().getUserId(),
                PermissionType.CREATE_CLUSTER,
                appId);
    }

    /**
     * 是否为应用管理员（分配角色的权限或超级用户）
     *
     * @param appId 应用编号
     * @return true有，false没有
     */
    public boolean isAppAdmin(String appId) {
        return isSuperAdmin() || hasAssignRolePermission(appId);
    }

    /**
     * 是否为超级用户
     *
     * @return true是，false不是
     */
    public boolean isSuperAdmin() {
        return rolePermissionService.isSuperAdmin(userInfoHolder.getUser().getUserId());
    }

    public boolean shouldHideConfigToCurrentUser(String appId, String env, String namespaceName) {
        // 1. check whether the current environment enables member only function
        if (!portalConfig.isConfigViewMemberOnly(env)) {
            return false;
        }

        // 2. public namespace is open to every one
        AppNamespace appNamespace = appNamespaceService.findByAppIdAndName(appId, namespaceName);
        if (appNamespace != null && appNamespace.isPublic()) {
            return false;
        }

        // 3. check app admin and operate permissions
        return !isAppAdmin(appId) && !hasOperateNamespacePermission(appId, namespaceName, env);
    }

    /**
     * 是否拥有创建应用的权限
     *
     * @return true有，false没有
     */
    public boolean hasCreateApplicationPermission() {
        return hasCreateApplicationPermission(userInfoHolder.getUser().getUserId());
    }

    /**
     * 是否拥有创建应用的权限
     *
     * @param userId 用户id
     * @return true有，false没有
     */
    public boolean hasCreateApplicationPermission(String userId) {
        return systemRoleManagerService.hasCreateApplicationPermission(userId);
    }

    /**
     * 是否拥有管理应用master权限
     *
     * @param appId 应用编号
     * @return true有，false没有
     */
    public boolean hasManageAppMasterPermission(String appId) {
        // manage app master权限可能未初始化，因此我们需要首先检查isSuperAdmin
        return isSuperAdmin() ||
                (hasAssignRolePermission(appId) &&
                        systemRoleManagerService.hasManageAppMasterPermission(
                                userInfoHolder.getUser().getUserId(),
                                appId)
                );
    }
}
