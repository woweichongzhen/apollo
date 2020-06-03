package com.ctrip.framework.apollo.openapi.service;

import com.ctrip.framework.apollo.openapi.entity.ConsumerRole;
import com.ctrip.framework.apollo.openapi.repository.ConsumerRoleRepository;
import com.ctrip.framework.apollo.portal.entity.po.Permission;
import com.ctrip.framework.apollo.portal.entity.po.RolePermission;
import com.ctrip.framework.apollo.portal.repository.PermissionRepository;
import com.ctrip.framework.apollo.portal.repository.RolePermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 第三方应用角色权限服务
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Service
public class ConsumerRolePermissionService {

    private final PermissionRepository permissionRepository;

    private final ConsumerRoleRepository consumerRoleRepository;

    private final RolePermissionRepository rolePermissionRepository;

    public ConsumerRolePermissionService(
            final PermissionRepository permissionRepository,
            final ConsumerRoleRepository consumerRoleRepository,
            final RolePermissionRepository rolePermissionRepository) {
        this.permissionRepository = permissionRepository;
        this.consumerRoleRepository = consumerRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    /**
     * 判断第三方应用是否有某种角色
     *
     * @param consumerId     第三方应用id
     * @param permissionType 权限类型
     * @param targetId       目标id
     * @return true有，false没有
     */
    public boolean consumerHasPermission(long consumerId, String permissionType, String targetId) {
        // 查找指定的权限
        Permission permission = permissionRepository.findTopByPermissionTypeAndTargetId(permissionType, targetId);
        if (permission == null) {
            return false;
        }

        // 查找指定的第三方应用角色中间表
        List<ConsumerRole> consumerRoles = consumerRoleRepository.findByConsumerId(consumerId);
        if (CollectionUtils.isEmpty(consumerRoles)) {
            return false;
        }

        // 获取第三方角色拥有的角色权限
        Set<Long> roleIds = consumerRoles.stream()
                .map(ConsumerRole::getRoleId)
                .collect(Collectors.toSet());
        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleIdIn(roleIds);
        if (CollectionUtils.isEmpty(rolePermissions)) {
            return false;
        }

        // 判断指定的权限是否包含
        for (RolePermission rolePermission : rolePermissions) {
            if (rolePermission.getPermissionId() == permission.getId()) {
                return true;
            }
        }

        return false;
    }
}
