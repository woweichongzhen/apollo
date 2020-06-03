package com.ctrip.framework.apollo.portal.repository;

import com.ctrip.framework.apollo.portal.entity.po.Permission;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Collection;
import java.util.List;

/**
 * 权限查询
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface PermissionRepository extends PagingAndSortingRepository<Permission, Long> {

    /**
     * 按权限类型和targetId查找权限
     *
     * @param permissionType 权限类型
     * @param targetId       目标id
     * @return 权限
     */
    Permission findTopByPermissionTypeAndTargetId(String permissionType, String targetId);

    /**
     * 查找权限
     *
     * @param permissionTypes 权限类型
     * @param targetId        目标id
     * @return 权限
     */
    List<Permission> findByPermissionTypeInAndTargetId(Collection<String> permissionTypes,
                                                       String targetId);

    /**
     * 查找权限id
     *
     * @param appId like appId+%
     * @return 权限id集合
     */
    @Query("SELECT p.id from Permission p where p.targetId = ?1 or p.targetId like CONCAT(?1, '+%')")
    List<Long> findPermissionIdsByAppId(String appId);

    /**
     * 查找权限id
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @return 权限id集合
     */
    @Query("SELECT p.id from Permission p where p.targetId = CONCAT(?1, '+', ?2)")
    List<Long> findPermissionIdsByAppIdAndNamespace(String appId, String namespaceName);

    /**
     * 假删除权限id
     *
     * @param permissionIds 权限id集合
     * @param operator      操作者
     * @return 删除成功的条数
     */
    @Modifying
    @Query("UPDATE Permission SET IsDeleted=1, DataChange_LastModifiedBy = ?2 WHERE Id in ?1")
    Integer batchDelete(List<Long> permissionIds, String operator);
}
