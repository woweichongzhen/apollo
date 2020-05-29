package com.ctrip.framework.apollo.portal.repository;

import com.ctrip.framework.apollo.portal.entity.po.RolePermission;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Collection;
import java.util.List;

/**
 * 角色权限查找
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface RolePermissionRepository extends PagingAndSortingRepository<RolePermission, Long> {

    /**
     * 查找角色应用的权限
     *
     * @param roleId 角色id
     * @return 角色权限集合
     */
    List<RolePermission> findByRoleIdIn(Collection<Long> roleId);

    @Modifying
    @Query("UPDATE RolePermission SET IsDeleted=1, DataChange_LastModifiedBy = ?2 WHERE PermissionId in ?1")
    Integer batchDeleteByPermissionIds(List<Long> permissionIds, String operator);
}
