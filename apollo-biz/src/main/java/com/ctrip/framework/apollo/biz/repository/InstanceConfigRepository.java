package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.InstanceConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 实例配置数据层
 */
public interface InstanceConfigRepository extends PagingAndSortingRepository<InstanceConfig, Long> {

    /**
     * 查找实例配置
     *
     * @param instanceId
     * @param configAppId
     * @param configNamespaceName
     * @return
     */
    InstanceConfig findByInstanceIdAndConfigAppIdAndConfigNamespaceName(long instanceId, String
            configAppId, String configNamespaceName);

    Page<InstanceConfig> findByReleaseKeyAndDataChangeLastModifiedTimeAfter(String releaseKey, Date
            validDate, Pageable pageable);

    Page<InstanceConfig> findByConfigAppIdAndConfigClusterNameAndConfigNamespaceNameAndDataChangeLastModifiedTimeAfter(
            String appId, String clusterName, String namespaceName, Date validDate, Pageable pageable);

    List<InstanceConfig> findByConfigAppIdAndConfigClusterNameAndConfigNamespaceNameAndDataChangeLastModifiedTimeAfterAndReleaseKeyNotIn(
            String appId, String clusterName, String namespaceName, Date validDate, Set<String> releaseKey);

    /**
     * 删除实例的相关配置
     */
    @Modifying
    @Query("delete from InstanceConfig  where ConfigAppId=?1 and ConfigClusterName=?2 and ConfigNamespaceName = ?3")
    int batchDelete(String appId, String clusterName, String namespaceName);

    @Query(
            value = "select b.Id from `InstanceConfig` a inner join `Instance` b on b.Id =" +
                    " a.`InstanceId` where a.`ConfigAppId` = :configAppId and a.`ConfigClusterName` = " +
                    ":clusterName and a.`ConfigNamespaceName` = :namespaceName and a.`DataChange_LastTime` " +
                    "> :validDate and b.`AppId` = :instanceAppId",
            countQuery = "select count(1) from `InstanceConfig` a inner join `Instance` b on b.id =" +
                    " a.`InstanceId` where a.`ConfigAppId` = :configAppId and a.`ConfigClusterName` = " +
                    ":clusterName and a.`ConfigNamespaceName` = :namespaceName and a.`DataChange_LastTime` " +
                    "> :validDate and b.`AppId` = :instanceAppId",
            nativeQuery = true)
    Page<Object> findInstanceIdsByNamespaceAndInstanceAppId(
            @Param("instanceAppId") String instanceAppId, @Param("configAppId") String configAppId,
            @Param("clusterName") String clusterName, @Param("namespaceName") String namespaceName,
            @Param("validDate") Date validDate, Pageable pageable);
}
