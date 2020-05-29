package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.Namespace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * 命名空间数据层
 * 提供 Namespace 的数据访问 给 Admin Service 和 Config Service
 */
public interface NamespaceRepository extends PagingAndSortingRepository<Namespace, Long> {

    /**
     * 获取集群的命名空间
     */
    List<Namespace> findByAppIdAndClusterNameOrderByIdAsc(String appId, String clusterName);

    /**
     * 查找命名空间
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @return 命名空间
     */
    Namespace findByAppIdAndClusterNameAndNamespaceName(String appId, String clusterName, String namespaceName);

    @Modifying
    @Query("update Namespace set isdeleted=1,DataChange_LastModifiedBy = ?3 where appId=?1 and clusterName=?2")
    int batchDelete(String appId, String clusterName, String operator);

    /**
     * 查找命名空间
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @return 命名空间集合
     */
    List<Namespace> findByAppIdAndNamespaceNameOrderByIdAsc(String appId, String namespaceName);

    List<Namespace> findByNamespaceName(String namespaceName, Pageable page);

    int countByNamespaceNameAndAppIdNot(String namespaceName, String appId);

}
