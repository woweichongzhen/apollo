package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.*;
import com.ctrip.framework.apollo.biz.repository.GrayReleaseRuleRepository;
import com.ctrip.framework.apollo.common.constants.NamespaceBranchStatus;
import com.ctrip.framework.apollo.common.constants.ReleaseOperation;
import com.ctrip.framework.apollo.common.constants.ReleaseOperationContext;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.GrayReleaseRuleItemTransformer;
import com.ctrip.framework.apollo.common.utils.UniqueKeyGenerator;
import com.google.common.collect.Maps;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class NamespaceBranchService {

    private final AuditService auditService;
    private final GrayReleaseRuleRepository grayReleaseRuleRepository;
    private final ClusterService clusterService;
    private final ReleaseService releaseService;
    private final NamespaceService namespaceService;
    private final ReleaseHistoryService releaseHistoryService;

    public NamespaceBranchService(
            final AuditService auditService,
            final GrayReleaseRuleRepository grayReleaseRuleRepository,
            final ClusterService clusterService,
            final @Lazy ReleaseService releaseService,
            final NamespaceService namespaceService,
            final ReleaseHistoryService releaseHistoryService) {
        this.auditService = auditService;
        this.grayReleaseRuleRepository = grayReleaseRuleRepository;
        this.clusterService = clusterService;
        this.releaseService = releaseService;
        this.namespaceService = namespaceService;
        this.releaseHistoryService = releaseHistoryService;
    }

    /**
     * 创建分支
     *
     * @param appId             应用编号
     * @param parentClusterName 父集群名称
     * @param namespaceName     命名空间名称
     * @param operator          操作者
     * @return 命名空间
     */
    @Transactional
    public Namespace createBranch(String appId, String parentClusterName, String namespaceName, String operator) {
        // 校验子命名空间是否存在，一个命名空间有且仅有一个子命名空间，存在则抛400
        Namespace childNamespace = findBranch(appId, parentClusterName, namespaceName);
        if (childNamespace != null) {
            throw new BadRequestException("namespace already has branch");
        }

        // 获取父集群，父集群不存在或不是顶级集群，抛400
        Cluster parentCluster = clusterService.findOne(appId, parentClusterName);
        if (parentCluster == null
                || parentCluster.getParentClusterId() != 0) {
            throw new BadRequestException("cluster not exist or illegal cluster");
        }

        // 创建子集群
        Cluster childCluster = createChildCluster(appId, parentCluster, namespaceName, operator);
        // 保存子集群实例
        Cluster createdChildCluster = clusterService.saveWithoutInstanceOfAppNamespaces(childCluster);

        // 创建子命名空间
        childNamespace = createNamespaceBranch(appId, createdChildCluster.getName(),
                namespaceName, operator);
        // 保存
        return namespaceService.save(childNamespace);
    }

    /**
     * 查找分支命名空间
     */
    public Namespace findBranch(String appId, String parentClusterName, String namespaceName) {
        return namespaceService.findChildNamespace(appId, parentClusterName, namespaceName);
    }

    public GrayReleaseRule findBranchGrayRules(String appId, String clusterName, String namespaceName,
                                               String branchName) {
        return grayReleaseRuleRepository
                .findTopByAppIdAndClusterNameAndNamespaceNameAndBranchNameOrderByIdDesc(appId, clusterName,
                        namespaceName, branchName);
    }

    /**
     * 更新分支灰度发布规则
     */
    @Transactional
    public void updateBranchGrayRules(String appId, String clusterName, String namespaceName,
                                      String branchName, GrayReleaseRule newRules) {
        doUpdateBranchGrayRules(appId, clusterName, namespaceName, branchName, newRules,
                true,
                ReleaseOperation.APPLY_GRAY_RULES);
    }

    /**
     * 更新分支灰度发布规则
     *
     * @param newRules             新规则
     * @param recordReleaseHistory 是否记录发布历史
     * @param releaseOperation     发布操作类型
     */
    private void doUpdateBranchGrayRules(String appId, String clusterName, String namespaceName,
                                         String branchName, GrayReleaseRule newRules, boolean recordReleaseHistory,
                                         int releaseOperation) {
        // 获取老的子命名空间的发布规则
        GrayReleaseRule oldRules =
                grayReleaseRuleRepository.findTopByAppIdAndClusterNameAndNamespaceNameAndBranchNameOrderByIdDesc(appId, clusterName,
                        namespaceName, branchName);

        // 查找最后的分支发布版本
        Release latestBranchRelease = releaseService.findLatestActiveRelease(appId, branchName, namespaceName);
        // 获取最后的分支发布id
        long latestBranchReleaseId = latestBranchRelease != null ? latestBranchRelease.getId() : 0;

        // 保存新的对象
        newRules.setReleaseId(latestBranchReleaseId);
        grayReleaseRuleRepository.save(newRules);

        //delete old rules
        // 删除老的规则
        if (oldRules != null) {
            grayReleaseRuleRepository.delete(oldRules);
        }

        if (recordReleaseHistory) {
            Map<String, Object> releaseOperationContext = Maps.newHashMap();
            // 新规则上下文
            releaseOperationContext.put(ReleaseOperationContext.RULES,
                    GrayReleaseRuleItemTransformer.batchTransformFromJSON(newRules.getRules()));

            // 老规则上下文
            if (oldRules != null) {
                releaseOperationContext.put(ReleaseOperationContext.OLD_RULES,
                        GrayReleaseRuleItemTransformer.batchTransformFromJSON(oldRules.getRules()));
            }

            // 记录发布历史
            releaseHistoryService.createReleaseHistory(appId, clusterName, namespaceName, branchName,
                    latestBranchReleaseId,
                    latestBranchReleaseId, releaseOperation, releaseOperationContext,
                    newRules.getDataChangeLastModifiedBy());
        }
    }

    /**
     * 更新灰度发布规则
     */
    @Transactional
    public GrayReleaseRule updateRulesReleaseId(String appId, String clusterName,
                                                String namespaceName, String branchName,
                                                long latestReleaseId, String operator) {
        // 获取最后的分支发布规则
        GrayReleaseRule oldRules = grayReleaseRuleRepository.
                findTopByAppIdAndClusterNameAndNamespaceNameAndBranchNameOrderByIdDesc(appId, clusterName,
                        namespaceName, branchName);

        if (oldRules == null) {
            return null;
        }

        // 保存新的灰度发布规则
        GrayReleaseRule newRules = new GrayReleaseRule();
        newRules.setBranchStatus(NamespaceBranchStatus.ACTIVE);
        newRules.setReleaseId(latestReleaseId);
        newRules.setRules(oldRules.getRules());
        newRules.setAppId(oldRules.getAppId());
        newRules.setClusterName(oldRules.getClusterName());
        newRules.setNamespaceName(oldRules.getNamespaceName());
        newRules.setBranchName(oldRules.getBranchName());
        newRules.setDataChangeCreatedBy(operator);
        newRules.setDataChangeLastModifiedBy(operator);

        grayReleaseRuleRepository.save(newRules);

        // 删除旧的灰度发布规则
        grayReleaseRuleRepository.delete(oldRules);

        return newRules;
    }

    /**
     * 删除分支灰度发布版本
     *
     * @param branchStatus 分支状态，为什么被删除（合并到主版本，或者直接删除）
     */
    @Transactional
    public void deleteBranch(String appId, String clusterName, String namespaceName,
                             String branchName, int branchStatus, String operator) {
        // 获取到待删除的子集群
        Cluster toDeleteCluster = clusterService.findOne(appId, branchName);
        if (toDeleteCluster == null) {
            return;
        }

        // 获取分支最后的发布版本
        Release latestBranchRelease = releaseService.findLatestActiveRelease(appId, branchName, namespaceName);

        long latestBranchReleaseId = latestBranchRelease != null ? latestBranchRelease.getId() : 0;

        // 更新待删除的灰度规则，清空规则
        GrayReleaseRule deleteRule = new GrayReleaseRule();
        deleteRule.setRules("[]");
        deleteRule.setAppId(appId);
        deleteRule.setClusterName(clusterName);
        deleteRule.setNamespaceName(namespaceName);
        deleteRule.setBranchName(branchName);
        deleteRule.setBranchStatus(branchStatus);
        deleteRule.setDataChangeLastModifiedBy(operator);
        deleteRule.setDataChangeCreatedBy(operator);
        doUpdateBranchGrayRules(appId, clusterName, namespaceName, branchName, deleteRule, false, -1);

        // 删除子集群
        clusterService.delete(toDeleteCluster.getId(), operator);

        // 判断是合并还是抛弃导致的删除
        int releaseOperation = branchStatus == NamespaceBranchStatus.MERGED
                ? ReleaseOperation.GRAY_RELEASE_DELETED_AFTER_MERGE
                : ReleaseOperation.ABANDON_GRAY_RELEASE;

        // 创建发布历史
        releaseHistoryService.createReleaseHistory(appId, clusterName, namespaceName, branchName, latestBranchReleaseId,
                latestBranchReleaseId, releaseOperation, null, operator);

        // 审计删除分支
        auditService.audit("Branch", toDeleteCluster.getId(), Audit.OP.DELETE, operator);
    }

    /**
     * 创建子集群
     */
    private Cluster createChildCluster(String appId, Cluster parentCluster,
                                       String namespaceName, String operator) {

        Cluster childCluster = new Cluster();
        childCluster.setAppId(appId);
        // 父集群id
        childCluster.setParentClusterId(parentCluster.getId());
        // 根据父集群名称，创建子集群的唯一名称
        childCluster.setName(UniqueKeyGenerator.generate(appId, parentCluster.getName(), namespaceName));
        childCluster.setDataChangeCreatedBy(operator);
        childCluster.setDataChangeLastModifiedBy(operator);

        return childCluster;
    }

    /**
     * 创建子命名空间
     */
    private Namespace createNamespaceBranch(String appId, String clusterName, String namespaceName, String operator) {
        Namespace childNamespace = new Namespace();
        childNamespace.setAppId(appId);
        // 子集群名称
        childNamespace.setClusterName(clusterName);
        childNamespace.setNamespaceName(namespaceName);
        childNamespace.setDataChangeLastModifiedBy(operator);
        childNamespace.setDataChangeCreatedBy(operator);
        return childNamespace;
    }

}
