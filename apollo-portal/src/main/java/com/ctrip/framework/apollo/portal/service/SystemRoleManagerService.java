package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.constant.PermissionType;
import com.ctrip.framework.apollo.portal.util.RoleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * 系统角色管理
 */
@Service
public class SystemRoleManagerService {
    public static final Logger logger = LoggerFactory.getLogger(SystemRoleManagerService.class);

    public static final String SYSTEM_PERMISSION_TARGET_ID = "SystemRole";

    public static final String CREATE_APPLICATION_ROLE_NAME =
            RoleUtils.buildCreateApplicationRoleName(PermissionType.CREATE_APPLICATION, SYSTEM_PERMISSION_TARGET_ID);

    public static final String CREATE_APPLICATION_LIMIT_SWITCH_KEY = "role.create-application.enabled";
    public static final String MANAGE_APP_MASTER_LIMIT_SWITCH_KEY = "role.manage-app-master.enabled";

    private final RolePermissionService rolePermissionService;

    private final PortalConfig portalConfig;

    private final RoleInitializationService roleInitializationService;

    @Autowired
    public SystemRoleManagerService(final RolePermissionService rolePermissionService,
                                    final PortalConfig portalConfig,
                                    final RoleInitializationService roleInitializationService) {
        this.rolePermissionService = rolePermissionService;
        this.portalConfig = portalConfig;
        this.roleInitializationService = roleInitializationService;
    }

    @PostConstruct
    private void init() {
        roleInitializationService.initCreateAppRole();
    }

    /**
     * 是否允许创建应用权限 启用禁用情况，默认flase
     */
    private boolean isCreateApplicationPermissionEnabled() {
        return portalConfig.isCreateApplicationPermissionEnabled();
    }

    public boolean isManageAppMasterPermissionEnabled() {
        return portalConfig.isManageAppMasterPermissionEnabled();
    }

    /**
     * 是否拥有创建应用权限
     *
     * @param userId 用户id
     * @return true有，false没有
     */
    public boolean hasCreateApplicationPermission(String userId) {
        // 禁用中，直接返回true
        if (!isCreateApplicationPermissionEnabled()) {
            return true;
        }

        // 是否拥有创建应用的权限
        return rolePermissionService.userHasPermission(
                userId,
                PermissionType.CREATE_APPLICATION,
                SYSTEM_PERMISSION_TARGET_ID);
    }

    public boolean hasManageAppMasterPermission(String userId, String appId) {
        if (!isManageAppMasterPermissionEnabled()) {
            return true;
        }

        return rolePermissionService.userHasPermission(userId, PermissionType.MANAGE_APP_MASTER, appId);
    }
}
