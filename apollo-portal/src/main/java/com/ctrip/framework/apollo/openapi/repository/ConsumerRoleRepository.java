package com.ctrip.framework.apollo.openapi.repository;

import com.ctrip.framework.apollo.openapi.entity.ConsumerRole;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * 第三方角色数据层
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConsumerRoleRepository extends PagingAndSortingRepository<ConsumerRole, Long> {
    /**
     * find consumer roles by userId
     *
     * @param consumerId consumer id
     */
    List<ConsumerRole> findByConsumerId(long consumerId);

    /**
     * find consumer roles by roleId
     */
    List<ConsumerRole> findByRoleId(long roleId);

    /**
     * 查找已有第三方应用角色
     *
     * @param consumerId 第三方应用id
     * @param roleId     角色id
     * @return 第三方应用角色
     */
    ConsumerRole findByConsumerIdAndRoleId(long consumerId, long roleId);

    /**
     * 假删除第三方角色
     *
     * @param roleIds  角色id集合
     * @param operator 操作者
     * @return 修改的条数
     */
    @Modifying
    @Query("UPDATE ConsumerRole SET IsDeleted=1, DataChange_LastModifiedBy = ?2 WHERE RoleId in ?1")
    Integer batchDeleteByRoleIds(List<Long> roleIds, String operator);
}
