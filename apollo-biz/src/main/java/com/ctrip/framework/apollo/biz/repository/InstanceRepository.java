package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.Instance;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 实例数据层
 */
public interface InstanceRepository extends PagingAndSortingRepository<Instance, Long> {

    /**
     * 通过唯一键查找实例
     *
     * @param appId       应用编号
     * @param clusterName 集群名称
     * @param dataCenter  数据中心
     * @param ip          ip
     * @return 实例
     */
    Instance findByAppIdAndClusterNameAndDataCenterAndIp(String appId, String clusterName, String dataCenter, String ip);
}
