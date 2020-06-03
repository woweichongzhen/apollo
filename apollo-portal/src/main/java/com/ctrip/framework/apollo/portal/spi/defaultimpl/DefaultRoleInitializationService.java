package com.ctrip.framework.apollo.portal.spi.defaultimpl;

import com.ctrip.framework.apollo.common.entity.App;
import com.ctrip.framework.apollo.common.entity.BaseEntity;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.constant.PermissionType;
import com.ctrip.framework.apollo.portal.constant.RoleType;
import com.ctrip.framework.apollo.portal.entity.po.Permission;
import com.ctrip.framework.apollo.portal.entity.po.Role;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.repository.PermissionRepository;
import com.ctrip.framework.apollo.portal.service.RoleInitializationService;
import com.ctrip.framework.apollo.portal.service.RolePermissionService;
import com.ctrip.framework.apollo.portal.service.SystemRoleManagerService;
import com.ctrip.framework.apollo.portal.util.RoleUtils;
import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 默认的角色信息初始化服务
 * Created by timothy on 2017/4/26.
 */
public class DefaultRoleInitializationService implements RoleInitializationService {

    @Autowired
    private RolePermissionService rolePermissionService;

    @Autowired
    private PortalConfig portalConfig;

    @Autowired
    private PermissionRepository permissionRepository;

    @Transactional
    @Override
    public void initAppRoles(App app) {
        // 构建应用拥有者角色
        String appId = app.getAppId();
        String appMasterRoleName = RoleUtils.buildAppMasterRoleName(appId);
        // 按角色名称查找角色，请注意roleName应该是唯一的
        if (rolePermissionService.findRoleByRoleName(appMasterRoleName) != null) {
            return;
        }
        String operator = app.getDataChangeCreatedBy();

        // 创建应用角色master权限
        createAppMasterRole(appId, operator);
        // 创建管理应用的master角色
        createManageAppMasterRole(appId, operator);
        // 赋予master角色给用户
        rolePermissionService.assignRoleToUsers(
                RoleUtils.buildAppMasterRoleName(appId),
                Sets.newHashSet(app.getOwnerName()),
                operator);

        // 初始化命名空间相关角色（修改和发布）
        initNamespaceRoles(appId, ConfigConsts.NAMESPACE_APPLICATION, operator);
        // 初始化不同环境的命名空间相关角色（修改和发布）
        initNamespaceEnvRoles(appId, ConfigConsts.NAMESPACE_APPLICATION, operator);
        // 赋予修改和发布权限给用户
        rolePermissionService.assignRoleToUsers(
                RoleUtils.buildNamespaceRoleName(appId, ConfigConsts.NAMESPACE_APPLICATION, RoleType.MODIFY_NAMESPACE),
                Sets.newHashSet(operator),
                operator);
        rolePermissionService.assignRoleToUsers(
                RoleUtils.buildNamespaceRoleName(appId, ConfigConsts.NAMESPACE_APPLICATION, RoleType.RELEASE_NAMESPACE),
                Sets.newHashSet(operator),
                operator);
    }

    @Transactional
    @Override
    public void initNamespaceRoles(String appId, String namespaceName, String operator) {
        // 构建修改命名空间的角色并保存
        String modifyNamespaceRoleName = RoleUtils.buildModifyNamespaceRoleName(appId, namespaceName);
        if (rolePermissionService.findRoleByRoleName(modifyNamespaceRoleName) == null) {
            createNamespaceRole(appId, namespaceName, PermissionType.MODIFY_NAMESPACE,
                    modifyNamespaceRoleName, operator);
        }

        // 构建发布命名空间的角色并保存
        String releaseNamespaceRoleName = RoleUtils.buildReleaseNamespaceRoleName(appId, namespaceName);
        if (rolePermissionService.findRoleByRoleName(releaseNamespaceRoleName) == null) {
            createNamespaceRole(appId, namespaceName, PermissionType.RELEASE_NAMESPACE,
                    releaseNamespaceRoleName, operator);
        }
    }

    @Transactional
    @Override
    public void initNamespaceEnvRoles(String appId, String namespaceName, String operator) {
        // 获取支持的环境
        List<Env> portalEnvs = portalConfig.portalSupportedEnvs();

        // 初始化具体环境的修改和发布
        for (Env env : portalEnvs) {
            initNamespaceSpecificEnvRoles(appId, namespaceName, env.toString(), operator);
        }
    }

    @Transactional
    @Override
    public void initNamespaceSpecificEnvRoles(String appId, String namespaceName, String env, String operator) {
        // 修改角色
        String modifyNamespaceEnvRoleName = RoleUtils.buildModifyNamespaceRoleName(appId, namespaceName, env);
        if (rolePermissionService.findRoleByRoleName(modifyNamespaceEnvRoleName) == null) {
            createNamespaceEnvRole(appId, namespaceName, PermissionType.MODIFY_NAMESPACE, env,
                    modifyNamespaceEnvRoleName, operator);
        }

        // 发布角色
        String releaseNamespaceEnvRoleName = RoleUtils.buildReleaseNamespaceRoleName(appId, namespaceName, env);
        if (rolePermissionService.findRoleByRoleName(releaseNamespaceEnvRoleName) == null) {
            createNamespaceEnvRole(appId, namespaceName, PermissionType.RELEASE_NAMESPACE, env,
                    releaseNamespaceEnvRoleName, operator);
        }
    }

    @Transactional
    @Override
    public void initCreateAppRole() {
        if (rolePermissionService.findRoleByRoleName(SystemRoleManagerService.CREATE_APPLICATION_ROLE_NAME) != null) {
            return;
        }
        Permission createAppPermission =
                permissionRepository.findTopByPermissionTypeAndTargetId(PermissionType.CREATE_APPLICATION,
                        SystemRoleManagerService.SYSTEM_PERMISSION_TARGET_ID);
        if (createAppPermission == null) {
            // create application permission init
            createAppPermission = createPermission(SystemRoleManagerService.SYSTEM_PERMISSION_TARGET_ID,
                    PermissionType.CREATE_APPLICATION, "apollo");
            rolePermissionService.createPermission(createAppPermission);
        }
        //  create application role init
        Role createAppRole = createRole(SystemRoleManagerService.CREATE_APPLICATION_ROLE_NAME, "apollo");
        rolePermissionService.createRoleWithPermissions(createAppRole, Sets.newHashSet(createAppPermission.getId()));
    }

    @Transactional
    @Override
    public void initManageAppMasterRole(String appId, String operator) {
        // 主要修复历史数据
        String manageAppMasterRoleName = RoleUtils.buildAppRoleName(appId, PermissionType.MANAGE_APP_MASTER);
        if (rolePermissionService.findRoleByRoleName(manageAppMasterRoleName) != null) {
            return;
        }
        synchronized (DefaultRoleInitializationService.class) {
            createManageAppMasterRole(appId, operator);
        }
    }

    /**
     * 创建管理应用的master角色
     *
     * @param appId    应用编号
     * @param operator 操作者
     */
    private void createManageAppMasterRole(String appId, String operator) {
        // 创建权限
        Permission permission = createPermission(appId, PermissionType.MANAGE_APP_MASTER, operator);
        rolePermissionService.createPermission(permission);

        // 创建角色
        Role role = createRole(RoleUtils.buildAppRoleName(appId, PermissionType.MANAGE_APP_MASTER), operator);
        Set<Long> permissionIds = new HashSet<>();
        permissionIds.add(permission.getId());
        rolePermissionService.createRoleWithPermissions(role, permissionIds);
    }

    /**
     * 创建应用角色master权限
     *
     * @param appId    应用编号
     * @param operator 操作者
     */
    private void createAppMasterRole(String appId, String operator) {
        // 保存权限
        Set<Permission> appPermissions = Stream.of(
                PermissionType.CREATE_CLUSTER,
                PermissionType.CREATE_NAMESPACE,
                PermissionType.ASSIGN_ROLE)
                .map(permissionType ->
                        createPermission(appId, permissionType, operator))
                .collect(Collectors.toSet());
        Set<Permission> createdAppPermissions = rolePermissionService.createPermissions(appPermissions);

        // 保存master角色和中间表
        Set<Long> appPermissionIds = createdAppPermissions.stream()
                .map(BaseEntity::getId)
                .collect(Collectors.toSet());
        Role appMasterRole = createRole(RoleUtils.buildAppMasterRoleName(appId), operator);
        rolePermissionService.createRoleWithPermissions(appMasterRole, appPermissionIds);
    }

    /**
     * 创建权限实体类
     *
     * @param targetId       目标id
     * @param permissionType 权限类型
     * @param operator       操作者
     * @return 创建后的权限实体类
     */
    private Permission createPermission(String targetId, String permissionType, String operator) {
        Permission permission = new Permission();
        permission.setPermissionType(permissionType);
        permission.setTargetId(targetId);
        permission.setDataChangeCreatedBy(operator);
        permission.setDataChangeLastModifiedBy(operator);
        return permission;
    }

    /**
     * 创建角色实体
     *
     * @param roleName 角色名称
     * @param operator 操作者
     * @return 角色实体
     */
    private Role createRole(String roleName, String operator) {
        Role role = new Role();
        role.setRoleName(roleName);
        role.setDataChangeCreatedBy(operator);
        role.setDataChangeLastModifiedBy(operator);
        return role;
    }

    /**
     * 创建命名空间角色
     *
     * @param appId          应用编号
     * @param namespaceName  名称
     * @param permissionType 权限类型
     * @param roleName       角色名称
     * @param operator       操作者
     */
    private void createNamespaceRole(String appId, String namespaceName, String permissionType,
                                     String roleName, String operator) {
        // 创建权限
        Permission permission = createPermission(
                RoleUtils.buildNamespaceTargetId(appId, namespaceName),
                permissionType,
                operator);
        Permission createdPermission = rolePermissionService.createPermission(permission);

        // 保存角色和中间表
        Role role = createRole(roleName, operator);
        rolePermissionService
                .createRoleWithPermissions(role, Sets.newHashSet(createdPermission.getId()));
    }

    private void createNamespaceEnvRole(String appId, String namespaceName, String permissionType, String env,
                                        String roleName, String operator) {
        Permission permission =
                createPermission(RoleUtils.buildNamespaceTargetId(appId, namespaceName, env), permissionType, operator);
        Permission createdPermission = rolePermissionService.createPermission(permission);

        Role role = createRole(roleName, operator);
        rolePermissionService
                .createRoleWithPermissions(role, Sets.newHashSet(createdPermission.getId()));
    }
}
