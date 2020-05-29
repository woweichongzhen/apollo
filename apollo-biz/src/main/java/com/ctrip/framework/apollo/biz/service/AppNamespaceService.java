package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.Audit;
import com.ctrip.framework.apollo.biz.entity.Cluster;
import com.ctrip.framework.apollo.biz.entity.Namespace;
import com.ctrip.framework.apollo.biz.repository.AppNamespaceRepository;
import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.common.exception.ServiceException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 应用命名空间服务
 */
@Service
public class AppNamespaceService {

    private static final Logger logger = LoggerFactory.getLogger(AppNamespaceService.class);

    private final AppNamespaceRepository appNamespaceRepository;
    private final NamespaceService namespaceService;
    private final ClusterService clusterService;
    private final AuditService auditService;

    public AppNamespaceService(
            final AppNamespaceRepository appNamespaceRepository,
            final @Lazy NamespaceService namespaceService,
            final @Lazy ClusterService clusterService,
            final AuditService auditService) {
        this.appNamespaceRepository = appNamespaceRepository;
        this.namespaceService = namespaceService;
        this.clusterService = clusterService;
        this.auditService = auditService;
    }

    /**
     * 校验应用命名空间是否唯一
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @return true唯一，false 不唯一
     */
    public boolean isAppNamespaceNameUnique(String appId, String namespaceName) {
        Objects.requireNonNull(appId, "AppId must not be null");
        Objects.requireNonNull(namespaceName, "Namespace must not be null");
        return Objects.isNull(appNamespaceRepository.findByAppIdAndName(appId, namespaceName));
    }

    public AppNamespace findPublicNamespaceByName(String namespaceName) {
        Preconditions.checkArgument(namespaceName != null, "Namespace must not be null");
        return appNamespaceRepository.findByNameAndIsPublicTrue(namespaceName);
    }

    /**
     * 获取应用命名空间
     *
     * @param appId 应用编号
     * @return 应用命名空间
     */
    public List<AppNamespace> findByAppId(String appId) {
        return appNamespaceRepository.findByAppId(appId);
    }

    public List<AppNamespace> findPublicNamespacesByNames(Set<String> namespaceNames) {
        if (namespaceNames == null || namespaceNames.isEmpty()) {
            return Collections.emptyList();
        }

        return appNamespaceRepository.findByNameInAndIsPublicTrue(namespaceNames);
    }

    public List<AppNamespace> findPrivateAppNamespace(String appId) {
        return appNamespaceRepository.findByAppIdAndIsPublic(appId, false);
    }

    /**
     * 查找应用命名空间
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @return 应用命名空间
     */
    public AppNamespace findOne(String appId, String namespaceName) {
        Preconditions.checkArgument(
                !StringUtils.isContainEmpty(appId, namespaceName),
                "appId or Namespace must not be null");
        return appNamespaceRepository.findByAppIdAndName(appId, namespaceName);
    }

    public List<AppNamespace> findByAppIdAndNamespaces(String appId, Set<String> namespaceNames) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(appId), "appId must not be null");
        if (namespaceNames == null || namespaceNames.isEmpty()) {
            return Collections.emptyList();
        }
        return appNamespaceRepository.findByAppIdAndNameIn(appId, namespaceNames);
    }

    /**
     * 创建默认的应用命名空间
     *
     * @param appId    应用编号
     * @param createBy 创建者
     */
    @Transactional
    public void createDefaultAppNamespace(String appId, String createBy) {
        // 校验命名空间唯一性
        if (!isAppNamespaceNameUnique(appId, ConfigConsts.NAMESPACE_APPLICATION)) {
            throw new ServiceException("appnamespace not unique");
        }

        AppNamespace appNs = new AppNamespace();
        appNs.setAppId(appId);
        appNs.setName(ConfigConsts.NAMESPACE_APPLICATION);
        appNs.setComment("default app namespace");
        appNs.setFormat(ConfigFileFormat.Properties.getValue());
        appNs.setDataChangeCreatedBy(createBy);
        appNs.setDataChangeLastModifiedBy(createBy);
        appNamespaceRepository.save(appNs);

        // 审计默认的应用命名空间创建
        auditService.audit(AppNamespace.class.getSimpleName(), appNs.getId(), Audit.OP.INSERT,
                createBy);
    }

    /**
     * 创建应用命名空间
     *
     * @param appNamespace 应用命名空间
     * @return 创建后的应用命名空间
     */
    @Transactional
    public AppNamespace createAppNamespace(AppNamespace appNamespace) {
        // 校验唯一性
        if (!isAppNamespaceNameUnique(appNamespace.getAppId(), appNamespace.getName())) {
            throw new ServiceException("appnamespace not unique");
        }

        // 保存
        appNamespace.setId(0);
        String createBy = appNamespace.getDataChangeCreatedBy();
        appNamespace.setDataChangeCreatedBy(createBy);
        appNamespace.setDataChangeLastModifiedBy(createBy);
        appNamespace = appNamespaceRepository.save(appNamespace);

        // 为应用的所有集群创建命名空间
        createNamespaceForAppNamespaceInAllCluster(appNamespace.getAppId(), appNamespace.getName(), createBy);

        // 插入审计
        auditService.audit(AppNamespace.class.getSimpleName(), appNamespace.getId(), Audit.OP.INSERT, createBy);
        return appNamespace;
    }

    public AppNamespace update(AppNamespace appNamespace) {
        AppNamespace managedNs = appNamespaceRepository.findByAppIdAndName(appNamespace.getAppId(),
                appNamespace.getName());
        BeanUtils.copyEntityProperties(appNamespace, managedNs);
        managedNs = appNamespaceRepository.save(managedNs);

        auditService.audit(AppNamespace.class.getSimpleName(), managedNs.getId(), Audit.OP.UPDATE,
                managedNs.getDataChangeLastModifiedBy());

        return managedNs;
    }

    /**
     * 为应用的所有集群创建命名空间
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @param createBy      创建者
     */
    public void createNamespaceForAppNamespaceInAllCluster(String appId, String namespaceName, String createBy) {
        // 查找所有的父集群
        List<Cluster> clusters = clusterService.findParentClusters(appId);

        for (Cluster cluster : clusters) {
            // 如果有一些脏数据，例如公共命名空间已存在，则不再次创建
            if (!namespaceService.isNamespaceUnique(appId, cluster.getName(), namespaceName)) {
                continue;
            }

            // 保存实际上的命名空间
            Namespace namespace = new Namespace();
            namespace.setClusterName(cluster.getName());
            namespace.setAppId(appId);
            namespace.setNamespaceName(namespaceName);
            namespace.setDataChangeCreatedBy(createBy);
            namespace.setDataChangeLastModifiedBy(createBy);

            namespaceService.save(namespace);
        }
    }

    @Transactional
    public void batchDelete(String appId, String operator) {
        appNamespaceRepository.batchDeleteByAppId(appId, operator);
    }

    @Transactional
    public void deleteAppNamespace(AppNamespace appNamespace, String operator) {
        String appId = appNamespace.getAppId();
        String namespaceName = appNamespace.getName();

        logger.info("{} is deleting AppNamespace, appId: {}, namespace: {}", operator, appId, namespaceName);

        // 1. delete namespaces
        List<Namespace> namespaces = namespaceService.findByAppIdAndNamespaceName(appId, namespaceName);

        if (namespaces != null) {
            for (Namespace namespace : namespaces) {
                namespaceService.deleteNamespace(namespace, operator);
            }
        }

        // 2. delete app namespace
        appNamespaceRepository.delete(appId, namespaceName, operator);
    }
}
