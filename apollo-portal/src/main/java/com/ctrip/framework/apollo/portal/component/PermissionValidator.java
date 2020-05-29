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
     * 是否有修改命名空间的权限
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

    public boolean hasDeleteNamespacePermission(String appId) {
        return hasAssignRolePermission(appId) || isSuperAdmin();
    }

    public boolean hasOperateNamespacePermission(String appId, String namespaceName) {
        return hasModifyNamespacePermission(appId, namespaceName) || hasReleaseNamespacePermission(appId,
                namespaceName);
    }

    public boolean hasOperateNamespacePermission(String appId, String namespaceName, String env) {
        return hasOperateNamespacePermission(appId, namespaceName) ||
                hasModifyNamespacePermission(appId, namespaceName, env) ||
                hasReleaseNamespacePermission(appId, namespaceName, env);
    }

    public boolean hasAssignRolePermission(String appId) {
        return rolePermissionService.userHasPermission(userInfoHolder.getUser().getUserId(),
                PermissionType.ASSIGN_ROLE,
                appId);
    }

    /**
     * 判断应用是否有创建命名空间的权限
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
        // 应用admin允许创建私有命名空间 或 创建公开类型的命名空间
        // 再判断是否应用有这个权限
        if (portalConfig.canAppAdminCreatePrivateNamespace() || isPublicAppNamespace) {
            return hasCreateNamespacePermission(appId);
        }

        // 是否是超级用户
        return isSuperAdmin();
    }

    public boolean hasCreateClusterPermission(String appId) {
        return rolePermissionService.userHasPermission(userInfoHolder.getUser().getUserId(),
                PermissionType.CREATE_CLUSTER,
                appId);
    }

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

    public boolean hasCreateApplicationPermission() {
        return hasCreateApplicationPermission(userInfoHolder.getUser().getUserId());
    }

    public boolean hasCreateApplicationPermission(String userId) {
        return systemRoleManagerService.hasCreateApplicationPermission(userId);
    }

    public boolean hasManageAppMasterPermission(String appId) {
        // the manage app master permission might not be initialized, so we need to check isSuperAdmin first
        return isSuperAdmin() ||
                (hasAssignRolePermission(appId) &&
                        systemRoleManagerService.hasManageAppMasterPermission(userInfoHolder.getUser().getUserId(),
                                appId)
                );
    }
}
