package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.common.dto.*;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.portal.api.AdminServiceAPI;
import com.ctrip.framework.apollo.portal.component.ItemsComparator;
import com.ctrip.framework.apollo.portal.constant.TracerEventType;
import com.ctrip.framework.apollo.portal.entity.bo.NamespaceBO;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.ctrip.framework.apollo.tracer.Tracer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class NamespaceBranchService {

    private final ItemsComparator itemsComparator;
    private final UserInfoHolder userInfoHolder;
    private final NamespaceService namespaceService;
    private final ItemService itemService;
    private final AdminServiceAPI.NamespaceBranchAPI namespaceBranchAPI;
    private final ReleaseService releaseService;

    public NamespaceBranchService(
            final ItemsComparator itemsComparator,
            final UserInfoHolder userInfoHolder,
            final NamespaceService namespaceService,
            final ItemService itemService,
            final AdminServiceAPI.NamespaceBranchAPI namespaceBranchAPI,
            final ReleaseService releaseService) {
        this.itemsComparator = itemsComparator;
        this.userInfoHolder = userInfoHolder;
        this.namespaceService = namespaceService;
        this.itemService = itemService;
        this.namespaceBranchAPI = namespaceBranchAPI;
        this.releaseService = releaseService;
    }

    /**
     * 创建灰度发布分支
     *
     * @param appId             应用编号
     * @param env               环境
     * @param parentClusterName 父集群名称
     * @param namespaceName     命名空间名称
     * @return 命名空间dto
     */
    @Transactional
    public NamespaceDTO createBranch(String appId, Env env, String parentClusterName, String namespaceName) {
        String operator = userInfoHolder.getUser().getUserId();
        return createBranch(appId, env, parentClusterName, namespaceName, operator);
    }

    /**
     * 实际创建分支
     */
    @Transactional
    public NamespaceDTO createBranch(String appId, Env env, String parentClusterName, String namespaceName,
                                     String operator) {
        // 创建命名空间
        NamespaceDTO createdBranch = namespaceBranchAPI.createBranch(appId, env, parentClusterName, namespaceName,
                operator);

        Tracer.logEvent(TracerEventType.CREATE_GRAY_RELEASE, String.format("%s+%s+%s+%s", appId, env, parentClusterName,
                namespaceName));
        return createdBranch;

    }

    public GrayReleaseRuleDTO findBranchGrayRules(String appId, Env env, String clusterName,
                                                  String namespaceName, String branchName) {
        return namespaceBranchAPI.findBranchGrayRules(appId, env, clusterName, namespaceName, branchName);

    }

    /**
     * 灰度发布规则更新
     */
    public void updateBranchGrayRules(String appId, Env env, String clusterName, String namespaceName,
                                      String branchName, GrayReleaseRuleDTO rules) {
        String operator = userInfoHolder.getUser().getUserId();
        updateBranchGrayRules(appId, env, clusterName, namespaceName, branchName, rules, operator);
    }

    /**
     * 灰度发布规则更新
     */
    public void updateBranchGrayRules(String appId, Env env, String clusterName, String namespaceName,
                                      String branchName, GrayReleaseRuleDTO rules, String operator) {
        rules.setDataChangeCreatedBy(operator);
        rules.setDataChangeLastModifiedBy(operator);

        // 请求adminService更新灰度规则
        namespaceBranchAPI.updateBranchGrayRules(appId, env, clusterName, namespaceName, branchName, rules);

        Tracer.logEvent(TracerEventType.UPDATE_GRAY_RELEASE_RULE,
                String.format("%s+%s+%s+%s", appId, env, clusterName, namespaceName));
    }

    public void deleteBranch(String appId, Env env, String clusterName, String namespaceName,
                             String branchName) {

        String operator = userInfoHolder.getUser().getUserId();
        deleteBranch(appId, env, clusterName, namespaceName, branchName, operator);
    }

    public void deleteBranch(String appId, Env env, String clusterName, String namespaceName,
                             String branchName, String operator) {
        namespaceBranchAPI.deleteBranch(appId, env, clusterName, namespaceName, branchName, operator);

        Tracer.logEvent(TracerEventType.DELETE_GRAY_RELEASE,
                String.format("%s+%s+%s+%s", appId, env, clusterName, namespaceName));
    }

    /**
     * 合并子命名空间到主命名空间，并执行一次发布
     */
    public ReleaseDTO merge(String appId, Env env, String clusterName, String namespaceName,
                            String branchName, String title, String comment,
                            boolean isEmergencyPublish, boolean deleteBranch) {
        String operator = userInfoHolder.getUser().getUserId();
        return merge(appId, env, clusterName, namespaceName, branchName, title, comment, isEmergencyPublish,
                deleteBranch, operator);
    }

    /**
     * 合并子命名空间到主命名空间
     */
    public ReleaseDTO merge(String appId, Env env, String clusterName, String namespaceName,
                            String branchName, String title, String comment,
                            boolean isEmergencyPublish, boolean deleteBranch, String operator) {
        // 计算子版本的改变集合
        ItemChangeSets changeSets = calculateBranchChangeSet(appId, env, clusterName, namespaceName, branchName,
                operator);

        // 更新子到父，并发布配置
        ReleaseDTO mergedResult = releaseService.updateAndPublish(appId, env, clusterName, namespaceName, title,
                comment, branchName, isEmergencyPublish, deleteBranch, changeSets);

        Tracer.logEvent(TracerEventType.MERGE_GRAY_RELEASE,
                String.format("%s+%s+%s+%s", appId, env, clusterName, namespaceName));

        return mergedResult;
    }

    /**
     * 计算分支 子版本的改变集合
     */
    private ItemChangeSets calculateBranchChangeSet(String appId, Env env, String clusterName, String namespaceName,
                                                    String branchName, String operator) {
        // 获取父命名空间BO对象，获取不到400，该对象包含最新的和最后发布之间的区别
        NamespaceBO parentNamespace = namespaceService.loadNamespaceBO(appId, env, clusterName, namespaceName);

        if (parentNamespace == null) {
            throw new BadRequestException("base namespace not existed");
        }

        // 若父 Namespace 有配置项的变更，不允许合并。因为，可能存在冲突
        // 此时需要对父命名空间进行一次发布，或者回退历史版本
        if (parentNamespace.getItemModifiedCnt() > 0) {
            throw new BadRequestException("Merge operation failed. Because master has modified items");
        }

        // 分别获取父子命名空间的项
        List<ItemDTO> masterItems = itemService.findItems(appId, env, clusterName, namespaceName);
        List<ItemDTO> branchItems = itemService.findItems(appId, env, branchName, namespaceName);

        // 比较父子改变的集合
        ItemChangeSets changeSets =
                itemsComparator.compareIgnoreBlankAndCommentItem(parentNamespace.getBaseInfo().getId(),
                        masterItems, branchItems);
        changeSets.setDeleteItems(Collections.emptyList());
        changeSets.setDataChangeLastModifiedBy(operator);
        return changeSets;
    }

    public NamespaceDTO findBranchBaseInfo(String appId, Env env, String clusterName, String namespaceName) {
        return namespaceBranchAPI.findBranch(appId, env, clusterName, namespaceName);
    }

    public NamespaceBO findBranch(String appId, Env env, String clusterName, String namespaceName) {
        NamespaceDTO namespaceDTO = findBranchBaseInfo(appId, env, clusterName, namespaceName);
        if (namespaceDTO == null) {
            return null;
        }
        return namespaceService.loadNamespaceBO(appId, env, namespaceDTO.getClusterName(), namespaceName);
    }

}
