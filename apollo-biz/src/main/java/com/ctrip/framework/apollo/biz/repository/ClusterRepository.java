package com.ctrip.framework.apollo.biz.repository;


import com.ctrip.framework.apollo.biz.entity.Cluster;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * 集群数据库层
 */
public interface ClusterRepository extends PagingAndSortingRepository<Cluster, Long> {

    /**
     * 查找父集群
     *
     * @param appId           应用编号
     * @param parentClusterId 父集群id
     * @return 集群集合
     */
    List<Cluster> findByAppIdAndParentClusterId(String appId, Long parentClusterId);

    List<Cluster> findByAppId(String appId);

    /**
     * 查找指定集群
     *
     * @param appId 应用编号
     * @param name  集群名称
     * @return 集群
     */
    Cluster findByAppIdAndName(String appId, String name);

    /**
     * 查找父集群下的集群
     *
     * @param parentClusterId 父集群id
     * @return 子集群
     */
    List<Cluster> findByParentClusterId(Long parentClusterId);
}
