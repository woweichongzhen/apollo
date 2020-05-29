package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.po.Permission;
import com.ctrip.framework.apollo.portal.entity.po.Role;

import java.util.List;
import java.util.Set;

/**
 * 角色权限服务
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface RolePermissionService {

    /**
     * 创建具有权限的角色，请注意角色名称应唯一
     *
     * @param role          角色
     * @param permissionIds 权限id
     * @return 创建后的角色
     */
    Role createRoleWithPermissions(Role role, Set<Long> permissionIds);

    /**
     * 给用户分配角色
     * Assign role to users
     *
     * @param roleName       要分配的角色名称，比如 Master+appId
     * @param userIds        要分配的用户id
     * @param operatorUserId 操作的用户id
     * @return the users assigned roles 添加的的用户id
     */
    Set<String> assignRoleToUsers(String roleName, Set<String> userIds,
                                  String operatorUserId);

    /**
     * Remove role from users
     */
    public void removeRoleFromUsers(String roleName, Set<String> userIds, String operatorUserId);

    /**
     * Query users with role
     */
    public Set<UserInfo> queryUsersWithRole(String roleName);

    /**
     * 按角色名称查找角色，请注意roleName应该是唯一的
     *
     * @param roleName 角色名称
     * @return 角色
     */
    Role findRoleByRoleName(String roleName);

    /**
     * 检测用户是否有某种权限
     *
     * @param userId         用户id
     * @param permissionType 权限类型
     * @param targetId       目标id，比如应用编号
     * @return true有，false没有
     */
    boolean userHasPermission(String userId, String permissionType, String targetId);

    /**
     * Find the user's roles
     */
    public List<Role> findUserRoles(String userId);

    /**
     * 判断用户是否为超级用户
     *
     * @param userId 用户id
     * @return true超级用户，false不是
     */
    boolean isSuperAdmin(String userId);

    /**
     * 创建权限，请注意，PermissionType + targetId应该是唯一的
     *
     * @param permission 权限
     * @return 创建后的权限
     */
    Permission createPermission(Permission permission);

    /**
     * 创建权限，注意permissionType+targetId应该是唯一的
     *
     * @param permissions 要创建的权限
     * @return 创建完成的权限
     */
    Set<Permission> createPermissions(Set<Permission> permissions);

    /**
     * delete permissions when delete app.
     */
    public void deleteRolePermissionsByAppId(String appId, String operator);

    /**
     * delete permissions when delete app namespace.
     */
    public void deleteRolePermissionsByAppIdAndNamespace(String appId, String namespaceName, String operator);
}
