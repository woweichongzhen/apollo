package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.Instance;
import com.ctrip.framework.apollo.biz.entity.InstanceConfig;
import com.ctrip.framework.apollo.biz.repository.InstanceConfigRepository;
import com.ctrip.framework.apollo.biz.repository.InstanceRepository;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
@Service
public class InstanceService {
    private final InstanceRepository instanceRepository;
    private final InstanceConfigRepository instanceConfigRepository;

    public InstanceService(
            final InstanceRepository instanceRepository,
            final InstanceConfigRepository instanceConfigRepository) {
        this.instanceRepository = instanceRepository;
        this.instanceConfigRepository = instanceConfigRepository;
    }

    /**
     * 查找实例
     *
     * @param appId       应用编号
     * @param clusterName 集群名称
     * @param dataCenter  数据中心
     * @param ip          ip地址
     * @return 实例
     */
    public Instance findInstance(String appId, String clusterName, String dataCenter, String ip) {
        return instanceRepository.findByAppIdAndClusterNameAndDataCenterAndIp(appId, clusterName,
                dataCenter, ip);
    }

    public List<Instance> findInstancesByIds(Set<Long> instanceIds) {
        Iterable<Instance> instances = instanceRepository.findAllById(instanceIds);
        if (instances == null) {
            return Collections.emptyList();
        }
        return Lists.newArrayList(instances);
    }

    /**
     * 创建实例
     *
     * @param instance 实例
     * @return 创建后的实例
     */
    @Transactional
    public Instance createInstance(Instance instance) {
        instance.setId(0); //protection

        return instanceRepository.save(instance);
    }

    /**
     * 查找实例配置
     *
     * @param instanceId          是id
     * @param configAppId         配置应用编号
     * @param configNamespaceName 配置命名空间名称
     * @return 实例配置
     */
    public InstanceConfig findInstanceConfig(long instanceId, String configAppId, String
            configNamespaceName) {
        return instanceConfigRepository.findByInstanceIdAndConfigAppIdAndConfigNamespaceName(
                instanceId, configAppId, configNamespaceName);
    }

    public Page<InstanceConfig> findActiveInstanceConfigsByReleaseKey(String releaseKey, Pageable
            pageable) {
        Page<InstanceConfig> instanceConfigs = instanceConfigRepository
                .findByReleaseKeyAndDataChangeLastModifiedTimeAfter(releaseKey,
                        getValidInstanceConfigDate(), pageable);
        return instanceConfigs;
    }

    public Page<Instance> findInstancesByNamespace(String appId, String clusterName, String
            namespaceName, Pageable pageable) {
        Page<InstanceConfig> instanceConfigs = instanceConfigRepository.
                findByConfigAppIdAndConfigClusterNameAndConfigNamespaceNameAndDataChangeLastModifiedTimeAfter(appId,
                        clusterName,
                        namespaceName, getValidInstanceConfigDate(), pageable);

        List<Instance> instances = Collections.emptyList();
        if (instanceConfigs.hasContent()) {
            Set<Long> instanceIds = instanceConfigs.getContent().stream().map
                    (InstanceConfig::getInstanceId).collect(Collectors.toSet());
            instances = findInstancesByIds(instanceIds);
        }

        return new PageImpl<>(instances, pageable, instanceConfigs.getTotalElements());
    }

    public Page<Instance> findInstancesByNamespaceAndInstanceAppId(String instanceAppId, String
            appId, String clusterName, String
                                                                           namespaceName, Pageable
                                                                           pageable) {
        Page<Object> instanceIdResult = instanceConfigRepository
                .findInstanceIdsByNamespaceAndInstanceAppId(instanceAppId, appId, clusterName,
                        namespaceName, getValidInstanceConfigDate(), pageable);

        List<Instance> instances = Collections.emptyList();
        if (instanceIdResult.hasContent()) {
            Set<Long> instanceIds = instanceIdResult.getContent().stream().map((Object o) -> {
                if (o == null) {
                    return null;
                }

                if (o instanceof Integer) {
                    return ((Integer) o).longValue();
                }

                if (o instanceof Long) {
                    return (Long) o;
                }

                //for h2 test
                if (o instanceof BigInteger) {
                    return ((BigInteger) o).longValue();
                }

                return null;
            }).filter(Objects::nonNull).collect(Collectors.toSet());
            instances = findInstancesByIds(instanceIds);
        }

        return new PageImpl<>(instances, pageable, instanceIdResult.getTotalElements());
    }

    public List<InstanceConfig> findInstanceConfigsByNamespaceWithReleaseKeysNotIn(String appId,
                                                                                   String clusterName,
                                                                                   String
                                                                                           namespaceName,
                                                                                   Set<String>
                                                                                           releaseKeysNotIn) {
        List<InstanceConfig> instanceConfigs = instanceConfigRepository.
                findByConfigAppIdAndConfigClusterNameAndConfigNamespaceNameAndDataChangeLastModifiedTimeAfterAndReleaseKeyNotIn(appId, clusterName,
                        namespaceName, getValidInstanceConfigDate(), releaseKeysNotIn);

        if (CollectionUtils.isEmpty(instanceConfigs)) {
            return Collections.emptyList();
        }

        return instanceConfigs;
    }

    /**
     * Currently the instance config is expired by 1 day, add one more hour to avoid possible time
     * difference
     */
    private Date getValidInstanceConfigDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        cal.add(Calendar.HOUR, -1);
        return cal.getTime();
    }

    /**
     * 创建实例配置
     *
     * @param instanceConfig 实例配置
     * @return 创建后的实例配置
     */
    @Transactional
    public InstanceConfig createInstanceConfig(InstanceConfig instanceConfig) {
        instanceConfig.setId(0); //protection

        return instanceConfigRepository.save(instanceConfig);
    }

    /**
     * 更新实例配置
     *
     * @param instanceConfig 要更新的实例配置
     * @return 实例配置
     */
    @Transactional
    public InstanceConfig updateInstanceConfig(InstanceConfig instanceConfig) {
        // 校验实例是否存在
        InstanceConfig existedInstanceConfig = instanceConfigRepository.findById(instanceConfig.getId()).orElse(null);
        Preconditions.checkArgument(existedInstanceConfig != null, String.format(
                "Instance config %d doesn't exist", instanceConfig.getId()));

        // 更新
        existedInstanceConfig.setConfigClusterName(instanceConfig.getConfigClusterName());
        existedInstanceConfig.setReleaseKey(instanceConfig.getReleaseKey());
        existedInstanceConfig.setReleaseDeliveryTime(instanceConfig.getReleaseDeliveryTime());
        existedInstanceConfig.setDataChangeLastModifiedTime(instanceConfig
                .getDataChangeLastModifiedTime());

        return instanceConfigRepository.save(existedInstanceConfig);
    }

    /**
     * 删除实例的相关配置
     */
    @Transactional
    public int batchDeleteInstanceConfig(String configAppId, String configClusterName, String configNamespaceName) {
        return instanceConfigRepository.batchDelete(configAppId, configClusterName, configNamespaceName);
    }
}
