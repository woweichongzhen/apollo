package com.ctrip.framework.apollo.openapi.auth;

import com.ctrip.framework.apollo.openapi.service.ConsumerRolePermissionService;
import com.ctrip.framework.apollo.openapi.util.ConsumerAuthUtil;
import com.ctrip.framework.apollo.portal.constant.PermissionType;
import com.ctrip.framework.apollo.portal.util.RoleUtils;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 第三方权限校验器
 */
@Component
public class ConsumerPermissionValidator {

    private final ConsumerRolePermissionService permissionService;
    private final ConsumerAuthUtil consumerAuthUtil;

    public ConsumerPermissionValidator(final ConsumerRolePermissionService permissionService,
                                       final ConsumerAuthUtil consumerAuthUtil) {
        this.permissionService = permissionService;
        this.consumerAuthUtil = consumerAuthUtil;
    }

    /**
     * 是否拥有修改命名空间权限（创建，或者修改，或者某个环境的修改）
     */
    public boolean hasModifyNamespacePermission(HttpServletRequest request, String appId,
                                                String namespaceName, String env) {
        if (hasCreateNamespacePermission(request, appId)) {
            return true;
        }
        return permissionService.consumerHasPermission(
                consumerAuthUtil.retrieveConsumerId(request),
                PermissionType.MODIFY_NAMESPACE,
                RoleUtils.buildNamespaceTargetId(appId, namespaceName))
                || permissionService.consumerHasPermission(
                consumerAuthUtil.retrieveConsumerId(request),
                PermissionType.MODIFY_NAMESPACE,
                RoleUtils.buildNamespaceTargetId(appId, namespaceName, env));

    }

    /**
     * 是否拥有发布命名空间的权限（创建，发布，或者某个环境的发布）
     */
    public boolean hasReleaseNamespacePermission(HttpServletRequest request, String appId,
                                                 String namespaceName, String env) {
        if (hasCreateNamespacePermission(request, appId)) {
            return true;
        }
        return permissionService.consumerHasPermission(
                consumerAuthUtil.retrieveConsumerId(request),
                PermissionType.RELEASE_NAMESPACE,
                RoleUtils.buildNamespaceTargetId(appId, namespaceName))
                || permissionService.consumerHasPermission(
                consumerAuthUtil.retrieveConsumerId(request),
                PermissionType.RELEASE_NAMESPACE,
                RoleUtils.buildNamespaceTargetId(appId, namespaceName, env));

    }

    /**
     * 是否拥有创建命名空间权限
     */
    public boolean hasCreateNamespacePermission(HttpServletRequest request, String appId) {
        return permissionService.consumerHasPermission(
                consumerAuthUtil.retrieveConsumerId(request),
                PermissionType.CREATE_NAMESPACE,
                appId);
    }

    /**
     * 是否拥有创建集群的权限
     */
    public boolean hasCreateClusterPermission(HttpServletRequest request, String appId) {
        return permissionService.consumerHasPermission(
                consumerAuthUtil.retrieveConsumerId(request),
                PermissionType.CREATE_CLUSTER,
                appId);
    }
}
