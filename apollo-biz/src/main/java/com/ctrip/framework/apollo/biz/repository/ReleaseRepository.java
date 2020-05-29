package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.Release;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

/**
 * 发布数据层
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ReleaseRepository extends PagingAndSortingRepository<Release, Long> {

    /**
     * 查找最后的发布版本
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @return 发布版本
     */
    Release findFirstByAppIdAndClusterNameAndNamespaceNameAndIsAbandonedFalseOrderByIdDesc(
            @Param("appId") String appId,
            @Param("clusterName") String clusterName,
            @Param("namespaceName") String namespaceName);

    /**
     * 获取未抛弃的发布版本
     *
     * @param id 发布id
     * @return 发布按本
     */
    Release findByIdAndIsAbandonedFalse(long id);

    List<Release> findByAppIdAndClusterNameAndNamespaceNameOrderByIdDesc(String appId, String clusterName,
                                                                         String namespaceName, Pageable page);

    List<Release> findByAppIdAndClusterNameAndNamespaceNameAndIsAbandonedFalseOrderByIdDesc(String appId,
                                                                                            String clusterName,
                                                                                            String namespaceName,
                                                                                            Pageable page);

    List<Release> findByReleaseKeyIn(Set<String> releaseKey);

    List<Release> findByIdIn(Set<Long> releaseIds);

    /**
     * 批量删除集群下命名空间的发布版本
     */
    @Modifying
    @Query("update Release set isdeleted=1,DataChange_LastModifiedBy = ?4 where appId=?1 and clusterName=?2 and " +
            "namespaceName = ?3")
    int batchDelete(String appId, String clusterName, String namespaceName, String operator);

    // For release history conversion program, need to delete after conversion it done
    List<Release> findByAppIdAndClusterNameAndNamespaceNameOrderByIdAsc(String appId, String clusterName,
                                                                        String namespaceName);
}
