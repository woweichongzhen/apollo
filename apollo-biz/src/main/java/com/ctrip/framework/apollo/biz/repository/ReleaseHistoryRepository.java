package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.ReleaseHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Set;

/**
 * 发布历史数据层
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ReleaseHistoryRepository extends PagingAndSortingRepository<ReleaseHistory, Long> {
    Page<ReleaseHistory> findByAppIdAndClusterNameAndNamespaceNameOrderByIdDesc(String appId, String
            clusterName, String namespaceName, Pageable pageable);

    Page<ReleaseHistory> findByReleaseIdAndOperationOrderByIdDesc(long releaseId, int operation, Pageable pageable);

    Page<ReleaseHistory> findByPreviousReleaseIdAndOperationOrderByIdDesc(long previousReleaseId, int operation,
                                                                          Pageable pageable);

    /**
     * 获取发布历史
     *
     * @param releaseId  发布id
     * @param operations 操作
     * @param pageable   分页
     * @return 发布历史
     */
    Page<ReleaseHistory> findByReleaseIdAndOperationInOrderByIdDesc(long releaseId, Set<Integer> operations,
                                                                    Pageable pageable);

    /**
     * 删除发布历史
     */
    @Modifying
    @Query("update ReleaseHistory set isdeleted=1,DataChange_LastModifiedBy = ?4 where appId=?1 and clusterName=?2 " +
            "and namespaceName = ?3")
    int batchDelete(String appId, String clusterName, String namespaceName, String operator);

}
