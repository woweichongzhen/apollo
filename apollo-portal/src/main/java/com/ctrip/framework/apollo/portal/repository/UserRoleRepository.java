package com.ctrip.framework.apollo.portal.repository;

import com.ctrip.framework.apollo.portal.entity.po.UserRole;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Collection;
import java.util.List;

/**
 * 用户角色查询
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface UserRoleRepository extends PagingAndSortingRepository<UserRole, Long> {

    /**
     * 查找用户拥有的角色
     *
     * @param userId 用户id
     * @return 用户角色信息
     */
    List<UserRole> findByUserId(String userId);

    /**
     * find user roles by roleId
     */
    List<UserRole> findByRoleId(long roleId);

    /**
     * 查找用户拥有的角色
     *
     * @param userId 用户id
     * @param roleId 角色id
     * @return 用户角色信息
     */
    List<UserRole> findByUserIdInAndRoleId(Collection<String> userId, long roleId);

    /**
     * 假删除用户角色中间表
     *
     * @param roleIds  角色id集合
     * @param operator 操作者
     * @return 修改的条数
     */
    @Modifying
    @Query("UPDATE UserRole SET IsDeleted=1, DataChange_LastModifiedBy = ?2 WHERE RoleId in ?1")
    Integer batchDeleteByRoleIds(List<Long> roleIds, String operator);

}
