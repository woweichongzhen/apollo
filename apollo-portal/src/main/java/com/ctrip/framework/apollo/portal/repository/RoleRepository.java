package com.ctrip.framework.apollo.portal.repository;

import com.ctrip.framework.apollo.portal.entity.po.Role;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * 角色查询
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface RoleRepository extends PagingAndSortingRepository<Role, Long> {

    /**
     * 通过角色名查找角色
     *
     * @param roleName 角色名称
     * @return 橘色信息
     */
    Role findTopByRoleName(String roleName);

    /**
     * 通过应用编号查找角色
     *
     * @param appId 应用编号，通过前缀+%
     * @return 角色id集合
     */
    @Query("SELECT r.id from Role r where (r.roleName = CONCAT('Master+', ?1) "
            + "OR r.roleName like CONCAT('ModifyNamespace+', ?1, '+%') "
            + "OR r.roleName like CONCAT('ReleaseNamespace+', ?1, '+%')  "
            + "OR r.roleName = CONCAT('ManageAppMaster+', ?1))")
    List<Long> findRoleIdsByAppId(String appId);

    /**
     * 查找角色id
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间
     * @return 角色id
     */
    @Query("SELECT r.id from Role r where (r.roleName = CONCAT('ModifyNamespace+', ?1, '+', ?2) "
            + "OR r.roleName = CONCAT('ReleaseNamespace+', ?1, '+', ?2))")
    List<Long> findRoleIdsByAppIdAndNamespace(String appId, String namespaceName);

    /**
     * 假删除角色id
     *
     * @param roleIds  角色id集合
     * @param operator 操作者
     * @return 修改的条数
     */
    @Modifying
    @Query("UPDATE Role SET IsDeleted=1, DataChange_LastModifiedBy = ?2 WHERE Id in ?1")
    Integer batchDelete(List<Long> roleIds, String operator);
}
