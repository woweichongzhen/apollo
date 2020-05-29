package com.ctrip.framework.apollo.portal.spi.defaultimpl;

import com.ctrip.framework.apollo.openapi.repository.ConsumerRoleRepository;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.po.Permission;
import com.ctrip.framework.apollo.portal.entity.po.Role;
import com.ctrip.framework.apollo.portal.entity.po.RolePermission;
import com.ctrip.framework.apollo.portal.entity.po.UserRole;
import com.ctrip.framework.apollo.portal.repository.PermissionRepository;
import com.ctrip.framework.apollo.portal.repository.RolePermissionRepository;
import com.ctrip.framework.apollo.portal.repository.RoleRepository;
import com.ctrip.framework.apollo.portal.repository.UserRoleRepository;
import com.ctrip.framework.apollo.portal.service.RolePermissionService;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 角色权限服务实现类
 * <p>
 * Created by timothy on 2017/4/26.
 */
public class DefaultRolePermissionService implements RolePermissionService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PortalConfig portalConfig;

    @Autowired
    private ConsumerRoleRepository consumerRoleRepository;

    @Transactional
    @Override
    public Role createRoleWithPermissions(Role role, Set<Long> permissionIds) {
        Role current = findRoleByRoleName(role.getRoleName());
        Preconditions.checkState(current == null, "Role %s already exists!", role.getRoleName());

        // 保存角色
        Role createdRole = roleRepository.save(role);

        // 创建中间表
        if (!CollectionUtils.isEmpty(permissionIds)) {
            Iterable<RolePermission> rolePermissions = permissionIds.stream().map(permissionId -> {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(createdRole.getId());
                rolePermission.setPermissionId(permissionId);
                rolePermission.setDataChangeCreatedBy(createdRole.getDataChangeCreatedBy());
                rolePermission.setDataChangeLastModifiedBy(createdRole.getDataChangeLastModifiedBy());
                return rolePermission;
            }).collect(Collectors.toList());
            rolePermissionRepository.saveAll(rolePermissions);
        }

        return createdRole;
    }

    @Override
    @Transactional
    public Set<String> assignRoleToUsers(String roleName, Set<String> userIds,
                                         String operatorUserId) {
        // 查找角色
        Role role = this.findRoleByRoleName(roleName);
        // 数据不能为空
        Preconditions.checkState(role != null, "Role %s doesn't exist!", roleName);

        // 查找已存在的用户角色
        List<UserRole> existedUserRoles = userRoleRepository.findByUserIdInAndRoleId(userIds, role.getId());
        // 已拥有该角色的用户id集合
        Set<String> existedUserIds = existedUserRoles.stream()
                .map(UserRole::getUserId)
                .collect(Collectors.toSet());

        // 去除已拥有的角色，获取要添加的用户id
        Set<String> toAssignUserIds = Sets.difference(userIds, existedUserIds);

        // 要创建的用户角色集合
        Iterable<UserRole> toCreate = toAssignUserIds.stream()
                .map(userId -> {
                    UserRole userRole = new UserRole();
                    userRole.setRoleId(role.getId());
                    userRole.setUserId(userId);
                    userRole.setDataChangeCreatedBy(operatorUserId);
                    userRole.setDataChangeLastModifiedBy(operatorUserId);
                    return userRole;
                }).collect(Collectors.toList());
        userRoleRepository.saveAll(toCreate);

        // 返回本次添加的用户id
        return toAssignUserIds;
    }

    /**
     * Remove role from users
     */
    @Transactional
    public void removeRoleFromUsers(String roleName, Set<String> userIds, String operatorUserId) {
        Role role = findRoleByRoleName(roleName);
        Preconditions.checkState(role != null, "Role %s doesn't exist!", roleName);

        List<UserRole> existedUserRoles =
                userRoleRepository.findByUserIdInAndRoleId(userIds, role.getId());

        for (UserRole userRole : existedUserRoles) {
            userRole.setDeleted(true);
            userRole.setDataChangeLastModifiedTime(new Date());
            userRole.setDataChangeLastModifiedBy(operatorUserId);
        }

        userRoleRepository.saveAll(existedUserRoles);
    }

    /**
     * Query users with role
     */
    public Set<UserInfo> queryUsersWithRole(String roleName) {
        Role role = findRoleByRoleName(roleName);

        if (role == null) {
            return Collections.emptySet();
        }

        List<UserRole> userRoles = userRoleRepository.findByRoleId(role.getId());

        Set<UserInfo> users = userRoles.stream().map(userRole -> {
            UserInfo userInfo = new UserInfo();
            userInfo.setUserId(userRole.getUserId());
            return userInfo;
        }).collect(Collectors.toSet());

        return users;
    }

    @Override
    public Role findRoleByRoleName(String roleName) {
        return roleRepository.findTopByRoleName(roleName);
    }

    @Override
    public boolean userHasPermission(String userId, String permissionType, String targetId) {
        // 通过权限类型和目标id查找权限
        Permission permission =
                permissionRepository.findTopByPermissionTypeAndTargetId(permissionType, targetId);
        // 未查找到权限
        if (permission == null) {
            return false;
        }

        // 超级用户
        if (isSuperAdmin(userId)) {
            return true;
        }

        // 查找用户角色，如果用户无角色，则未查找到
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (CollectionUtils.isEmpty(userRoles)) {
            return false;
        }

        // 查找角色对应的权限
        Set<Long> roleIds =
                userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleIdIn(roleIds);
        if (CollectionUtils.isEmpty(rolePermissions)) {
            return false;
        }

        // 遍历判断角色拥有的权限，是否和要查找的权限id相符合
        for (RolePermission rolePermission : rolePermissions) {
            if (rolePermission.getPermissionId() == permission.getId()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<Role> findUserRoles(String userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (CollectionUtils.isEmpty(userRoles)) {
            return Collections.emptyList();
        }

        Set<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());

        return Lists.newLinkedList(roleRepository.findAllById(roleIds));
    }

    @Override
    public boolean isSuperAdmin(String userId) {
        return portalConfig.superAdmins().contains(userId);
    }

    @Transactional
    @Override
    public Permission createPermission(Permission permission) {
        String permissionType = permission.getPermissionType();
        String targetId = permission.getTargetId();
        Permission current =
                permissionRepository.findTopByPermissionTypeAndTargetId(permissionType, targetId);
        Preconditions.checkState(current == null,
                "Permission with permissionType %s targetId %s already exists!", permissionType, targetId);

        return permissionRepository.save(permission);
    }

    @Transactional
    @Override
    public Set<Permission> createPermissions(Set<Permission> permissions) {
        Multimap<String, String> targetIdPermissionTypes = HashMultimap.create();
        for (Permission permission : permissions) {
            targetIdPermissionTypes.put(permission.getTargetId(), permission.getPermissionType());
        }

        // 目标id拥有的权限查询，如果不存在，直接抛出异常
        for (String targetId : targetIdPermissionTypes.keySet()) {
            Collection<String> permissionTypes = targetIdPermissionTypes.get(targetId);
            List<Permission> current =
                    permissionRepository.findByPermissionTypeInAndTargetId(permissionTypes, targetId);
            Preconditions.checkState(CollectionUtils.isEmpty(current),
                    "Permission with permissionType %s targetId %s already exists!", permissionTypes,
                    targetId);
        }

        // 保存所有权限
        Iterable<Permission> results = permissionRepository.saveAll(permissions);
        return StreamSupport.stream(results.spliterator(), false).collect(Collectors.toSet());
    }

    @Transactional
    @Override
    public void deleteRolePermissionsByAppId(String appId, String operator) {
        List<Long> permissionIds = permissionRepository.findPermissionIdsByAppId(appId);

        if (!permissionIds.isEmpty()) {
            // 1. delete Permission
            permissionRepository.batchDelete(permissionIds, operator);

            // 2. delete Role Permission
            rolePermissionRepository.batchDeleteByPermissionIds(permissionIds, operator);
        }

        List<Long> roleIds = roleRepository.findRoleIdsByAppId(appId);

        if (!roleIds.isEmpty()) {
            // 3. delete Role
            roleRepository.batchDelete(roleIds, operator);

            // 4. delete User Role
            userRoleRepository.batchDeleteByRoleIds(roleIds, operator);

            // 5. delete Consumer Role
            consumerRoleRepository.batchDeleteByRoleIds(roleIds, operator);
        }
    }

    @Transactional
    @Override
    public void deleteRolePermissionsByAppIdAndNamespace(String appId, String namespaceName, String operator) {
        List<Long> permissionIds = permissionRepository.findPermissionIdsByAppIdAndNamespace(appId, namespaceName);

        if (!permissionIds.isEmpty()) {
            // 1. delete Permission
            permissionRepository.batchDelete(permissionIds, operator);

            // 2. delete Role Permission
            rolePermissionRepository.batchDeleteByPermissionIds(permissionIds, operator);
        }

        List<Long> roleIds = roleRepository.findRoleIdsByAppIdAndNamespace(appId, namespaceName);

        if (!roleIds.isEmpty()) {
            // 3. delete Role
            roleRepository.batchDelete(roleIds, operator);

            // 4. delete User Role
            userRoleRepository.batchDeleteByRoleIds(roleIds, operator);

            // 5. delete Consumer Role
            consumerRoleRepository.batchDeleteByRoleIds(roleIds, operator);
        }
    }
}
