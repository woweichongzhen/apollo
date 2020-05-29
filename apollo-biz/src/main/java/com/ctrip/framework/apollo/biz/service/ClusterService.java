package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.Audit;
import com.ctrip.framework.apollo.biz.entity.Cluster;
import com.ctrip.framework.apollo.biz.repository.ClusterRepository;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.exception.ServiceException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.google.common.base.Strings;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 集群服务
 */
@Service
public class ClusterService {

    private final ClusterRepository clusterRepository;
    private final AuditService auditService;
    private final NamespaceService namespaceService;

    public ClusterService(
            final ClusterRepository clusterRepository,
            final AuditService auditService,
            final @Lazy NamespaceService namespaceService) {
        this.clusterRepository = clusterRepository;
        this.auditService = auditService;
        this.namespaceService = namespaceService;
    }

    /**
     * 校验集群名称唯一性
     *
     * @param appId       应用编号
     * @param clusterName 集群名称
     * @return true唯一，false不唯一
     */
    public boolean isClusterNameUnique(String appId, String clusterName) {
        Objects.requireNonNull(appId, "AppId must not be null");
        Objects.requireNonNull(clusterName, "ClusterName must not be null");
        return Objects.isNull(clusterRepository.findByAppIdAndName(appId, clusterName));
    }

    /**
     * 查找指定集群
     *
     * @param appId 应用编号
     * @param name  集群名称
     * @return 集群
     */
    public Cluster findOne(String appId, String name) {
        return clusterRepository.findByAppIdAndName(appId, name);
    }

    /**
     * 查找指定集群
     *
     * @param clusterId 集群id
     * @return 集群，查找不到返回null
     */
    public Cluster findOne(long clusterId) {
        return clusterRepository.findById(clusterId).orElse(null);
    }

    /**
     * 查找所有的父集群
     *
     * @param appId 应用编号
     * @return 集群集合
     */
    public List<Cluster> findParentClusters(String appId) {
        if (Strings.isNullOrEmpty(appId)) {
            return Collections.emptyList();
        }

        // 查找所有第一层的父集群
        List<Cluster> clusters = clusterRepository.findByAppIdAndParentClusterId(appId, 0L);
        if (clusters == null) {
            return Collections.emptyList();
        }

        Collections.sort(clusters);

        return clusters;
    }

    /**
     * 创建集群，并根据应用命名空间，创建cluster的命名空间
     */
    @Transactional
    public Cluster saveWithInstanceOfAppNamespaces(Cluster entity) {
        // 创建集群
        Cluster savedCluster = saveWithoutInstanceOfAppNamespaces(entity);
        // 根据应用命名空间，创建cluster的命名空间
        namespaceService.instanceOfAppNamespaces(savedCluster.getAppId(), savedCluster.getName(),
                savedCluster.getDataChangeCreatedBy());

        return savedCluster;
    }

    /**
     * 创建集群而不实例化应用命名空间
     */
    @Transactional
    public Cluster saveWithoutInstanceOfAppNamespaces(Cluster entity) {
        // 校验唯一
        if (!isClusterNameUnique(entity.getAppId(), entity.getName())) {
            throw new BadRequestException("cluster not unique");
        }
        // 保存集群
        entity.setId(0);
        Cluster cluster = clusterRepository.save(entity);
        // 插入审批
        auditService.audit(Cluster.class.getSimpleName(), cluster.getId(), Audit.OP.INSERT,
                cluster.getDataChangeCreatedBy());

        return cluster;
    }

    /**
     * 删除集群
     *
     * @param id 集群id，没有400
     */
    @Transactional
    public void delete(long id, String operator) {
        Cluster cluster = clusterRepository.findById(id).orElse(null);
        if (cluster == null) {
            throw new BadRequestException("cluster not exist");
        }

        // 删除集群下链接的命名空间
        namespaceService.deleteByAppIdAndClusterName(cluster.getAppId(), cluster.getName(), operator);

        cluster.setDeleted(true);
        cluster.setDataChangeLastModifiedBy(operator);
        clusterRepository.save(cluster);

        auditService.audit(Cluster.class.getSimpleName(), id, Audit.OP.DELETE, operator);
    }

    @Transactional
    public Cluster update(Cluster cluster) {
        Cluster managedCluster =
                clusterRepository.findByAppIdAndName(cluster.getAppId(), cluster.getName());
        BeanUtils.copyEntityProperties(cluster, managedCluster);
        managedCluster = clusterRepository.save(managedCluster);

        auditService.audit(Cluster.class.getSimpleName(), managedCluster.getId(), Audit.OP.UPDATE,
                managedCluster.getDataChangeLastModifiedBy());

        return managedCluster;
    }

    /**
     * 创建默认的集群
     *
     * @param appId    应用编号
     * @param createBy 创建者
     */
    @Transactional
    public void createDefaultCluster(String appId, String createBy) {
        // 校验集群名称唯一性
        if (!isClusterNameUnique(appId, ConfigConsts.CLUSTER_NAME_DEFAULT)) {
            throw new ServiceException("cluster not unique");
        }

        // 保存集群
        Cluster cluster = new Cluster();
        cluster.setName(ConfigConsts.CLUSTER_NAME_DEFAULT);
        cluster.setAppId(appId);
        cluster.setDataChangeCreatedBy(createBy);
        cluster.setDataChangeLastModifiedBy(createBy);
        clusterRepository.save(cluster);

        // 审计集群创建
        auditService.audit(Cluster.class.getSimpleName(), cluster.getId(), Audit.OP.INSERT, createBy);
    }

    /**
     * 查找子集群
     *
     * @param appId             应用编号
     * @param parentClusterName 父集群名称
     * @return 集群列表
     */
    public List<Cluster> findChildClusters(String appId, String parentClusterName) {
        // 父集群不存在400
        Cluster parentCluster = findOne(appId, parentClusterName);
        if (parentCluster == null) {
            throw new BadRequestException("parent cluster not exist");
        }

        return clusterRepository.findByParentClusterId(parentCluster.getId());
    }

    public List<Cluster> findClusters(String appId) {
        List<Cluster> clusters = clusterRepository.findByAppId(appId);

        if (clusters == null) {
            return Collections.emptyList();
        }

        // to make sure parent cluster is ahead of branch cluster
        Collections.sort(clusters);

        return clusters;
    }
}
