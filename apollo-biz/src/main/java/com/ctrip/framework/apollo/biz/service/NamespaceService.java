package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.*;
import com.ctrip.framework.apollo.biz.message.MessageSender;
import com.ctrip.framework.apollo.biz.message.Topics;
import com.ctrip.framework.apollo.biz.repository.NamespaceRepository;
import com.ctrip.framework.apollo.biz.utils.ReleaseMessageKeyGenerator;
import com.ctrip.framework.apollo.common.constants.GsonType;
import com.ctrip.framework.apollo.common.constants.NamespaceBranchStatus;
import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.exception.ServiceException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 命名空间服务
 */
@Service
public class NamespaceService {

    private Gson gson = new Gson();

    private final NamespaceRepository namespaceRepository;
    private final AuditService auditService;
    private final AppNamespaceService appNamespaceService;
    private final ItemService itemService;
    private final CommitService commitService;
    private final ReleaseService releaseService;
    private final ClusterService clusterService;
    private final NamespaceBranchService namespaceBranchService;
    private final ReleaseHistoryService releaseHistoryService;
    private final NamespaceLockService namespaceLockService;
    private final InstanceService instanceService;
    private final MessageSender messageSender;

    public NamespaceService(
            final ReleaseHistoryService releaseHistoryService,
            final NamespaceRepository namespaceRepository,
            final AuditService auditService,
            final @Lazy AppNamespaceService appNamespaceService,
            final MessageSender messageSender,
            final @Lazy ItemService itemService,
            final CommitService commitService,
            final @Lazy ReleaseService releaseService,
            final @Lazy ClusterService clusterService,
            final @Lazy NamespaceBranchService namespaceBranchService,
            final NamespaceLockService namespaceLockService,
            final InstanceService instanceService) {
        this.releaseHistoryService = releaseHistoryService;
        this.namespaceRepository = namespaceRepository;
        this.auditService = auditService;
        this.appNamespaceService = appNamespaceService;
        this.messageSender = messageSender;
        this.itemService = itemService;
        this.commitService = commitService;
        this.releaseService = releaseService;
        this.clusterService = clusterService;
        this.namespaceBranchService = namespaceBranchService;
        this.namespaceLockService = namespaceLockService;
        this.instanceService = instanceService;
    }


    public Namespace findOne(Long namespaceId) {
        return namespaceRepository.findById(namespaceId).orElse(null);
    }

    /**
     * 查找一个命名空间
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @return 创建后的命名空间
     */
    public Namespace findOne(String appId, String clusterName, String namespaceName) {
        return namespaceRepository.findByAppIdAndClusterNameAndNamespaceName(appId, clusterName,
                namespaceName);
    }

    public Namespace findPublicNamespaceForAssociatedNamespace(String clusterName, String namespaceName) {
        AppNamespace appNamespace = appNamespaceService.findPublicNamespaceByName(namespaceName);
        if (appNamespace == null) {
            throw new BadRequestException("namespace not exist");
        }

        String appId = appNamespace.getAppId();

        Namespace namespace = findOne(appId, clusterName, namespaceName);

        //default cluster's namespace
        if (Objects.equals(clusterName, ConfigConsts.CLUSTER_NAME_DEFAULT)) {
            return namespace;
        }

        //custom cluster's namespace not exist.
        //return default cluster's namespace
        if (namespace == null) {
            return findOne(appId, ConfigConsts.CLUSTER_NAME_DEFAULT, namespaceName);
        }

        //custom cluster's namespace exist and has published.
        //return custom cluster's namespace
        Release latestActiveRelease = releaseService.findLatestActiveRelease(namespace);
        if (latestActiveRelease != null) {
            return namespace;
        }

        Namespace defaultNamespace = findOne(appId, ConfigConsts.CLUSTER_NAME_DEFAULT, namespaceName);

        //custom cluster's namespace exist but never published.
        //and default cluster's namespace not exist.
        //return custom cluster's namespace
        if (defaultNamespace == null) {
            return namespace;
        }

        //custom cluster's namespace exist but never published.
        //and default cluster's namespace exist and has published.
        //return default cluster's namespace
        Release defaultNamespaceLatestActiveRelease = releaseService.findLatestActiveRelease(defaultNamespace);
        if (defaultNamespaceLatestActiveRelease != null) {
            return defaultNamespace;
        }

        //custom cluster's namespace exist but never published.
        //and default cluster's namespace exist but never published.
        //return custom cluster's namespace
        return namespace;
    }

    public List<Namespace> findPublicAppNamespaceAllNamespaces(String namespaceName, Pageable page) {
        AppNamespace publicAppNamespace = appNamespaceService.findPublicNamespaceByName(namespaceName);

        if (publicAppNamespace == null) {
            throw new BadRequestException(
                    String.format("Public appNamespace not exists. NamespaceName = %s", namespaceName));
        }

        List<Namespace> namespaces = namespaceRepository.findByNamespaceName(namespaceName, page);

        return filterChildNamespace(namespaces);
    }

    private List<Namespace> filterChildNamespace(List<Namespace> namespaces) {
        List<Namespace> result = new LinkedList<>();

        if (CollectionUtils.isEmpty(namespaces)) {
            return result;
        }

        for (Namespace namespace : namespaces) {
            if (!isChildNamespace(namespace)) {
                result.add(namespace);
            }
        }

        return result;
    }

    public int countPublicAppNamespaceAssociatedNamespaces(String publicNamespaceName) {
        AppNamespace publicAppNamespace = appNamespaceService.findPublicNamespaceByName(publicNamespaceName);

        if (publicAppNamespace == null) {
            throw new BadRequestException(
                    String.format("Public appNamespace not exists. NamespaceName = %s", publicNamespaceName));
        }

        return namespaceRepository.countByNamespaceNameAndAppIdNot(publicNamespaceName, publicAppNamespace.getAppId());
    }

    /**
     * 获取到集群下的命名空间
     */
    public List<Namespace> findNamespaces(String appId, String clusterName) {
        List<Namespace> namespaces = namespaceRepository.findByAppIdAndClusterNameOrderByIdAsc(appId, clusterName);
        if (namespaces == null) {
            return Collections.emptyList();
        }
        return namespaces;
    }

    /**
     * 查找命名空间
     */
    public List<Namespace> findByAppIdAndNamespaceName(String appId, String namespaceName) {
        return namespaceRepository.findByAppIdAndNamespaceNameOrderByIdAsc(appId, namespaceName);
    }

    /**
     * 查找子命名空间，即指定父集群的子命名空间
     *
     * @param parentClusterName 父集群名称
     */
    public Namespace findChildNamespace(String appId, String parentClusterName, String namespaceName) {
        // 查找相关的命名空间
        List<Namespace> namespaces = findByAppIdAndNamespaceName(appId, namespaceName);
        // 若只有一个命名空间，说明没有子命名空间
        if (CollectionUtils.isEmpty(namespaces) || namespaces.size() == 1) {
            return null;
        }

        // 查找子集群
        List<Cluster> childClusters = clusterService.findChildClusters(appId, parentClusterName);
        // 若没有子集群，说明没有子命名空间
        if (CollectionUtils.isEmpty(childClusters)) {
            return null;
        }

        // 子命名空间是子集群和子命名空间的交集
        // 即子命名空间的集群名称 要和 子集群的名称相符，才是一个合格的子命名空间
        Set<String> childClusterNames = childClusters.stream()
                .map(Cluster::getName)
                .collect(Collectors.toSet());
        //the child namespace is the intersection of the child clusters and child namespaces
        for (Namespace namespace : namespaces) {
            if (childClusterNames.contains(namespace.getClusterName())) {
                return namespace;
            }
        }

        return null;
    }

    /**
     * 获取父命名空间的子命名空间
     */
    public Namespace findChildNamespace(Namespace parentNamespace) {
        String appId = parentNamespace.getAppId();
        String parentClusterName = parentNamespace.getClusterName();
        String namespaceName = parentNamespace.getNamespaceName();

        return findChildNamespace(appId, parentClusterName, namespaceName);

    }

    public Namespace findParentNamespace(String appId, String clusterName, String namespaceName) {
        return findParentNamespace(new Namespace(appId, clusterName, namespaceName));
    }

    /**
     * 获取父命名空间
     *
     * @param namespace 命名空间
     * @return 父命名空间，未查找到返回null
     */
    public Namespace findParentNamespace(Namespace namespace) {
        String appId = namespace.getAppId();
        String namespaceName = namespace.getNamespaceName();

        // 查找到指定集群
        Cluster cluster = clusterService.findOne(appId, namespace.getClusterName());

        if (cluster != null && cluster.getParentClusterId() > 0) {
            // 如果存在指定集群，并且该集群还有父集群，则查找到父集群
            Cluster parentCluster = clusterService.findOne(cluster.getParentClusterId());
            // 再查找父集群的命名空间
            return findOne(appId, parentCluster.getName(), namespaceName);
        }

        return null;
    }

    public boolean isChildNamespace(String appId, String clusterName, String namespaceName) {
        return isChildNamespace(new Namespace(appId, clusterName, namespaceName));
    }

    public boolean isChildNamespace(Namespace namespace) {
        return findParentNamespace(namespace) != null;
    }

    /**
     * 校验某个集群的命名空间唯一性
     *
     * @param appId     应用编号
     * @param cluster   集群
     * @param namespace 名称
     * @return true唯一，false不唯一
     */
    public boolean isNamespaceUnique(String appId, String cluster, String namespace) {
        Objects.requireNonNull(appId, "AppId must not be null");
        Objects.requireNonNull(cluster, "Cluster must not be null");
        Objects.requireNonNull(namespace, "Namespace must not be null");
        return Objects.isNull(
                namespaceRepository.findByAppIdAndClusterNameAndNamespaceName(appId, cluster, namespace));
    }

    /**
     * 删除与集群相连接的命名空间
     */
    @Transactional
    public void deleteByAppIdAndClusterName(String appId, String clusterName, String operator) {
        // 获取集群的命名空间
        List<Namespace> toDeleteNamespaces = findNamespaces(appId, clusterName);
        // 遍历删除
        for (Namespace namespace : toDeleteNamespaces) {
            deleteNamespace(namespace, operator);
        }
    }

    /**
     * 删除命名空间
     */
    @Transactional
    public Namespace deleteNamespace(Namespace namespace, String operator) {
        String appId = namespace.getAppId();
        String clusterName = namespace.getClusterName();
        String namespaceName = namespace.getNamespaceName();

        // 根据命名空间id批量删除项
        itemService.batchDelete(namespace.getId(), operator);
        // 批量删除指定集群的命名空间的变更记录
        commitService.batchDelete(appId, clusterName, namespace.getNamespaceName(), operator);

        // Child namespace releases should retain as long as the parent namespace exists, because parent namespaces'
        // release
        // histories need them
        // 如果命名空间不是子命名空间，还需要删除发布版本
        // 只要父名称空间存在，子名称空间版本就应保留，因为父名称空间的发布历史需要他们
        if (!isChildNamespace(namespace)) {
            releaseService.batchDelete(appId, clusterName, namespace.getNamespaceName(), operator);
        }

        // 如果存在子命名空间，需要删除
        Namespace childNamespace = findChildNamespace(namespace);
        if (childNamespace != null) {
            // 在删除父命名空间时，如果改集群存在灰度版本，也一起删除掉
            namespaceBranchService.deleteBranch(appId, clusterName, namespaceName,
                    childNamespace.getClusterName(), NamespaceBranchStatus.DELETED, operator);
            // 删除子名称空间的发布版本。注意：删除子名称空间不会删除子名称空间的版本
            releaseService.batchDelete(appId, childNamespace.getClusterName(), namespaceName, operator);
        }

        // 删除发布历史
        releaseHistoryService.batchDelete(appId, clusterName, namespaceName, operator);

        // 删除实例的相关配置
        instanceService.batchDeleteInstanceConfig(appId, clusterName, namespaceName);

        // 命名空间解锁
        namespaceLockService.unlock(namespace.getId());

        namespace.setDeleted(true);
        namespace.setDataChangeLastModifiedBy(operator);

        // 审计命名空间的删除
        auditService.audit(Namespace.class.getSimpleName(), namespace.getId(), Audit.OP.DELETE, operator);

        // 假删除命名空间
        Namespace deleted = namespaceRepository.save(namespace);

        // 发布消息，以在configservice中进行一些清理，例如更新缓存
        messageSender.sendMessage(ReleaseMessageKeyGenerator.generate(appId, clusterName, namespaceName),
                Topics.APOLLO_RELEASE_TOPIC);

        return deleted;
    }

    /**
     * 保存命名空间
     *
     * @param entity 命名空间
     * @return 保存后的命名空间
     */
    @Transactional
    public Namespace save(Namespace entity) {
        if (!isNamespaceUnique(entity.getAppId(), entity.getClusterName(), entity.getNamespaceName())) {
            throw new ServiceException("namespace not unique");
        }
        entity.setId(0);
        Namespace namespace = namespaceRepository.save(entity);

        // 插入审计
        auditService.audit(Namespace.class.getSimpleName(), namespace.getId(), Audit.OP.INSERT,
                namespace.getDataChangeCreatedBy());

        return namespace;
    }

    @Transactional
    public Namespace update(Namespace namespace) {
        Namespace managedNamespace = namespaceRepository.findByAppIdAndClusterNameAndNamespaceName(
                namespace.getAppId(), namespace.getClusterName(), namespace.getNamespaceName());
        BeanUtils.copyEntityProperties(namespace, managedNamespace);
        managedNamespace = namespaceRepository.save(managedNamespace);

        auditService.audit(Namespace.class.getSimpleName(), managedNamespace.getId(), Audit.OP.UPDATE,
                managedNamespace.getDataChangeLastModifiedBy());

        return managedNamespace;
    }

    /**
     * 根据应用命名空间，创建cluster的命名空间
     *
     * @param appId       应用编号
     * @param clusterName 集群名
     * @param createBy    创建者
     */
    @Transactional
    public void instanceOfAppNamespaces(String appId, String clusterName, String createBy) {
        // 获取应用命名空间
        List<AppNamespace> appNamespaces = appNamespaceService.findByAppId(appId);

        // 遍历应用命名空间，创建实际的集群命名空间
        for (AppNamespace appNamespace : appNamespaces) {
            Namespace ns = new Namespace();
            ns.setAppId(appId);
            ns.setClusterName(clusterName);
            ns.setNamespaceName(appNamespace.getName());
            ns.setDataChangeCreatedBy(createBy);
            ns.setDataChangeLastModifiedBy(createBy);
            namespaceRepository.save(ns);

            // 审计命名空间创建
            auditService.audit(Namespace.class.getSimpleName(), ns.getId(), Audit.OP.INSERT, createBy);
        }

    }

    public Map<String, Boolean> namespacePublishInfo(String appId) {
        List<Cluster> clusters = clusterService.findParentClusters(appId);
        if (CollectionUtils.isEmpty(clusters)) {
            throw new BadRequestException("app not exist");
        }

        Map<String, Boolean> clusterHasNotPublishedItems = Maps.newHashMap();

        for (Cluster cluster : clusters) {
            String clusterName = cluster.getName();
            List<Namespace> namespaces = findNamespaces(appId, clusterName);

            for (Namespace namespace : namespaces) {
                boolean isNamespaceNotPublished = isNamespaceNotPublished(namespace);

                if (isNamespaceNotPublished) {
                    clusterHasNotPublishedItems.put(clusterName, true);
                    break;
                }
            }

            clusterHasNotPublishedItems.putIfAbsent(clusterName, false);
        }

        return clusterHasNotPublishedItems;
    }

    private boolean isNamespaceNotPublished(Namespace namespace) {

        Release latestRelease = releaseService.findLatestActiveRelease(namespace);
        long namespaceId = namespace.getId();

        if (latestRelease == null) {
            Item lastItem = itemService.findLastOne(namespaceId);
            return lastItem != null;
        }

        Date lastPublishTime = latestRelease.getDataChangeLastModifiedTime();
        List<Item> itemsModifiedAfterLastPublish = itemService.findItemsModifiedAfterDate(namespaceId, lastPublishTime);

        if (CollectionUtils.isEmpty(itemsModifiedAfterLastPublish)) {
            return false;
        }

        Map<String, String> publishedConfiguration = gson.fromJson(latestRelease.getConfigurations(), GsonType.CONFIG);
        for (Item item : itemsModifiedAfterLastPublish) {
            if (!Objects.equals(item.getValue(), publishedConfiguration.get(item.getKey()))) {
                return true;
            }
        }

        return false;
    }


}
