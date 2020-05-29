package com.ctrip.framework.apollo.adminservice.controller;

import com.ctrip.framework.apollo.biz.entity.GrayReleaseRule;
import com.ctrip.framework.apollo.biz.entity.Namespace;
import com.ctrip.framework.apollo.biz.message.MessageSender;
import com.ctrip.framework.apollo.biz.message.Topics;
import com.ctrip.framework.apollo.biz.service.NamespaceBranchService;
import com.ctrip.framework.apollo.biz.service.NamespaceService;
import com.ctrip.framework.apollo.biz.utils.ReleaseMessageKeyGenerator;
import com.ctrip.framework.apollo.common.constants.NamespaceBranchStatus;
import com.ctrip.framework.apollo.common.dto.GrayReleaseRuleDTO;
import com.ctrip.framework.apollo.common.dto.NamespaceDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.common.utils.GrayReleaseRuleItemTransformer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 命名空间分支API
 */
@RestController
public class NamespaceBranchController {

    private final MessageSender messageSender;
    private final NamespaceBranchService namespaceBranchService;
    private final NamespaceService namespaceService;

    public NamespaceBranchController(
            final MessageSender messageSender,
            final NamespaceBranchService namespaceBranchService,
            final NamespaceService namespaceService) {
        this.messageSender = messageSender;
        this.namespaceBranchService = namespaceBranchService;
        this.namespaceService = namespaceService;
    }

    /**
     * 创建分支
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @param operator      操作者
     * @return 创建后的dto
     */
    @PostMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches")
    public NamespaceDTO createBranch(@PathVariable String appId,
                                     @PathVariable String clusterName,
                                     @PathVariable String namespaceName,
                                     @RequestParam("operator") String operator) {
        // 检查命名空间是否存在
        checkNamespace(appId, clusterName, namespaceName);

        // 创建分支
        Namespace createdBranch = namespaceBranchService.createBranch(appId, clusterName, namespaceName, operator);

        return BeanUtils.transform(NamespaceDTO.class, createdBranch);
    }

    @GetMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName}/rules")
    public GrayReleaseRuleDTO findBranchGrayRules(@PathVariable String appId,
                                                  @PathVariable String clusterName,
                                                  @PathVariable String namespaceName,
                                                  @PathVariable String branchName) {

        checkBranch(appId, clusterName, namespaceName, branchName);

        GrayReleaseRule rules = namespaceBranchService.findBranchGrayRules(appId, clusterName, namespaceName,
                branchName);
        if (rules == null) {
            return null;
        }
        GrayReleaseRuleDTO ruleDTO =
                new GrayReleaseRuleDTO(rules.getAppId(), rules.getClusterName(), rules.getNamespaceName(),
                        rules.getBranchName());

        ruleDTO.setReleaseId(rules.getReleaseId());

        ruleDTO.setRuleItems(GrayReleaseRuleItemTransformer.batchTransformFromJSON(rules.getRules()));

        return ruleDTO;
    }

    /**
     * 更新灰度发布规则
     *
     * @param branchName 分支名称
     * @param newRuleDto 新规则dto
     */
    @Transactional
    @PutMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName}/rules")
    public void updateBranchGrayRules(@PathVariable String appId, @PathVariable String clusterName,
                                      @PathVariable String namespaceName, @PathVariable String branchName,
                                      @RequestBody GrayReleaseRuleDTO newRuleDto) {
        // 检测分支（父命名空间和子命名空间）
        checkBranch(appId, clusterName, namespaceName, branchName);

        GrayReleaseRule newRules = BeanUtils.transform(GrayReleaseRule.class, newRuleDto);
        // 设置规则，分支状态为正在激活
        newRules.setRules(GrayReleaseRuleItemTransformer.batchTransformToJSON(newRuleDto.getRuleItems()));
        newRules.setBranchStatus(NamespaceBranchStatus.ACTIVE);

        // 更新子命名空间灰度发布规则
        namespaceBranchService.updateBranchGrayRules(appId, clusterName, namespaceName, branchName, newRules);

        // db消息表通知configService，配置服务再通过长轮询等方式通知客户端
        messageSender.sendMessage(ReleaseMessageKeyGenerator.generate(appId, clusterName, namespaceName),
                Topics.APOLLO_RELEASE_TOPIC);
    }

    @Transactional
    @DeleteMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName}")
    public void deleteBranch(@PathVariable String appId, @PathVariable String clusterName,
                             @PathVariable String namespaceName, @PathVariable String branchName,
                             @RequestParam("operator") String operator) {

        checkBranch(appId, clusterName, namespaceName, branchName);

        namespaceBranchService
                .deleteBranch(appId, clusterName, namespaceName, branchName, NamespaceBranchStatus.DELETED, operator);

        messageSender.sendMessage(ReleaseMessageKeyGenerator.generate(appId, clusterName, namespaceName),
                Topics.APOLLO_RELEASE_TOPIC);

    }

    @GetMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches")
    public NamespaceDTO loadNamespaceBranch(@PathVariable String appId, @PathVariable String clusterName,
                                            @PathVariable String namespaceName) {

        checkNamespace(appId, clusterName, namespaceName);

        Namespace childNamespace = namespaceBranchService.findBranch(appId, clusterName, namespaceName);
        if (childNamespace == null) {
            return null;
        }

        return BeanUtils.transform(NamespaceDTO.class, childNamespace);
    }

    /**
     * 检测分支
     */
    private void checkBranch(String appId, String clusterName, String namespaceName, String branchName) {
        //1. check parent namespace
        // 检测父命名空间
        checkNamespace(appId, clusterName, namespaceName);

        //2. check child namespace
        // 检测子命名空间，不存在400
        Namespace childNamespace = namespaceService.findOne(appId, branchName, namespaceName);
        if (childNamespace == null) {
            throw new BadRequestException(String.format("Namespace's branch not exist. AppId = %s, ClusterName = %s, "
                            + "NamespaceName = %s, BranchName = %s",
                    appId, clusterName, namespaceName, branchName));
        }

    }

    /**
     * 检查命名空间是否存在
     */
    private void checkNamespace(String appId, String clusterName, String namespaceName) {
        Namespace parentNamespace = namespaceService.findOne(appId, clusterName, namespaceName);
        if (parentNamespace == null) {
            throw new BadRequestException(String.format("Namespace not exist. AppId = %s, ClusterName = %s, " +
                            "NamespaceName = %s", appId,
                    clusterName, namespaceName));
        }
    }


}
