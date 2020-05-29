package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.Commit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * 变更记录数据层
 * 提供 Commit 的数据访问 给 Admin Service 和 Config Service
 */
public interface CommitRepository extends PagingAndSortingRepository<Commit, Long> {

    List<Commit> findByAppIdAndClusterNameAndNamespaceNameOrderByIdDesc(String appId, String clusterName,
                                                                        String namespaceName, Pageable pageable);

    /**
     * 批量删除指定集群的命名空间的变更记录
     */
    @Modifying
    @Query("update Commit set isdeleted=1,DataChange_LastModifiedBy = ?4 where appId=?1 and clusterName=?2 and " +
            "namespaceName = ?3")
    int batchDelete(String appId, String clusterName, String namespaceName, String operator);

}
