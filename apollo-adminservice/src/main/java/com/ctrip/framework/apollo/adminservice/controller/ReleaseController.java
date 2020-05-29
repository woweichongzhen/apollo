package com.ctrip.framework.apollo.adminservice.controller;


import com.ctrip.framework.apollo.biz.entity.Namespace;
import com.ctrip.framework.apollo.biz.entity.Release;
import com.ctrip.framework.apollo.biz.message.MessageSender;
import com.ctrip.framework.apollo.biz.message.Topics;
import com.ctrip.framework.apollo.biz.service.NamespaceBranchService;
import com.ctrip.framework.apollo.biz.service.NamespaceService;
import com.ctrip.framework.apollo.biz.service.ReleaseService;
import com.ctrip.framework.apollo.biz.utils.ReleaseMessageKeyGenerator;
import com.ctrip.framework.apollo.common.constants.NamespaceBranchStatus;
import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.dto.ReleaseDTO;
import com.ctrip.framework.apollo.common.exception.NotFoundException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.google.common.base.Splitter;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * adminservice的发布API
 */
@RestController
public class ReleaseController {

    /**
     * ，发布分割器
     */
    private static final Splitter RELEASES_SPLITTER = Splitter.on(",").omitEmptyStrings()
            .trimResults();

    private final ReleaseService releaseService;
    private final NamespaceService namespaceService;
    private final MessageSender messageSender;
    private final NamespaceBranchService namespaceBranchService;

    public ReleaseController(
            final ReleaseService releaseService,
            final NamespaceService namespaceService,
            final MessageSender messageSender,
            final NamespaceBranchService namespaceBranchService) {
        this.releaseService = releaseService;
        this.namespaceService = namespaceService;
        this.messageSender = messageSender;
        this.namespaceBranchService = namespaceBranchService;
    }


    @GetMapping("/releases/{releaseId}")
    public ReleaseDTO get(@PathVariable("releaseId") long releaseId) {
        Release release = releaseService.findOne(releaseId);
        if (release == null) {
            throw new NotFoundException(String.format("release not found for %s", releaseId));
        }
        return BeanUtils.transform(ReleaseDTO.class, release);
    }

    @GetMapping("/releases")
    public List<ReleaseDTO> findReleaseByIds(@RequestParam("releaseIds") String releaseIds) {
        Set<Long> releaseIdSet = RELEASES_SPLITTER.splitToList(releaseIds).stream().map(Long::parseLong)
                .collect(Collectors.toSet());

        List<Release> releases = releaseService.findByReleaseIds(releaseIdSet);

        return BeanUtils.batchTransform(ReleaseDTO.class, releases);
    }

    @GetMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/releases/all")
    public List<ReleaseDTO> findAllReleases(@PathVariable("appId") String appId,
                                            @PathVariable("clusterName") String clusterName,
                                            @PathVariable("namespaceName") String namespaceName,
                                            Pageable page) {
        List<Release> releases = releaseService.findAllReleases(appId, clusterName, namespaceName, page);
        return BeanUtils.batchTransform(ReleaseDTO.class, releases);
    }


    @GetMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/releases/active")
    public List<ReleaseDTO> findActiveReleases(@PathVariable("appId") String appId,
                                               @PathVariable("clusterName") String clusterName,
                                               @PathVariable("namespaceName") String namespaceName,
                                               Pageable page) {
        List<Release> releases = releaseService.findActiveReleases(appId, clusterName, namespaceName, page);
        return BeanUtils.batchTransform(ReleaseDTO.class, releases);
    }

    @GetMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/releases/latest")
    public ReleaseDTO getLatest(@PathVariable("appId") String appId,
                                @PathVariable("clusterName") String clusterName,
                                @PathVariable("namespaceName") String namespaceName) {
        Release release = releaseService.findLatestActiveRelease(appId, clusterName, namespaceName);
        return BeanUtils.transform(ReleaseDTO.class, release);
    }

    /**
     * 执行发布
     *
     * @param appId              应用编号
     * @param clusterName        集群名称
     * @param namespaceName      命名空间名称
     * @param releaseName        发布名称
     * @param releaseComment     发布备注
     * @param operator           操作者
     * @param isEmergencyPublish 是否紧急发布
     * @return 发布dto
     */
    @Transactional
    @PostMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/releases")
    public ReleaseDTO publish(@PathVariable("appId") String appId,
                              @PathVariable("clusterName") String clusterName,
                              @PathVariable("namespaceName") String namespaceName,
                              @RequestParam("name") String releaseName,
                              @RequestParam(name = "comment", required = false) String releaseComment,
                              @RequestParam("operator") String operator,
                              @RequestParam(name = "isEmergencyPublish", defaultValue = "false") boolean isEmergencyPublish) {
        // 校验命名空间是否存在，不存在404
        Namespace namespace = namespaceService.findOne(appId, clusterName, namespaceName);
        if (namespace == null) {
            throw new NotFoundException(String.format("Could not find namespace for %s %s %s", appId,
                    clusterName, namespaceName));
        }

        // 发布配置
        Release release = releaseService.publish(namespace, releaseName, releaseComment, operator, isEmergencyPublish);

        // 查找父命名空间，存在父命名空间的也就是灰度发布
        Namespace parentNamespace = namespaceService.findParentNamespace(namespace);
        // 获取要通知的集群的名称，灰度发布也是用父集群的名称，因为灰度发布对客户端是透明无感知的
        String messageCluster;
        if (parentNamespace != null) {
            messageCluster = parentNamespace.getClusterName();
        } else {
            messageCluster = clusterName;
        }
        // 通过mysql通知对应的集群
        // 对于同一个 Namespace ，生成的消息内容是相同的
        // 通过这样的方式，可以使用最新的 ReleaseMessage 的 id 属性，作为 Namespace 是否发生变更的标识
        // 而 Apollo 确实是通过这样的方式实现，Client 通过不断使用获得到 ReleaseMessage 的 id 属性作为版本号，请求 Config Service 判断是否配置发生了变化
        //
        // ReleaseMessage 设计的意图是作为配置发生变化的通知，所以对于同一个 Namespace ，仅需要保留其最新的 ReleaseMessage 记录即可
        // 所以消息发送器里，有后台任务不断清理旧的 ReleaseMessage 记录
        messageSender.sendMessage(
                ReleaseMessageKeyGenerator.generate(appId, messageCluster, namespaceName),
                Topics.APOLLO_RELEASE_TOPIC);

        return BeanUtils.transform(ReleaseDTO.class, release);
    }


    /**
     * 合并灰度发布的到主版本，并发布主版本
     *
     * @return 发布结果
     */
    @Transactional
    @PostMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/updateAndPublish")
    public ReleaseDTO updateAndPublish(@PathVariable("appId") String appId,
                                       @PathVariable("clusterName") String clusterName,
                                       @PathVariable("namespaceName") String namespaceName,
                                       @RequestParam("releaseName") String releaseName,
                                       @RequestParam("branchName") String branchName,
                                       @RequestParam(value = "deleteBranch", defaultValue = "true") boolean deleteBranch,
                                       @RequestParam(name = "releaseComment", required = false) String releaseComment,
                                       @RequestParam(name = "isEmergencyPublish", defaultValue = "false") boolean isEmergencyPublish,
                                       @RequestBody ItemChangeSets changeSets) {
        // 获取父命名空间，不存在404
        Namespace namespace = namespaceService.findOne(appId, clusterName, namespaceName);
        if (namespace == null) {
            throw new NotFoundException(String.format("Could not find namespace for %s %s %s", appId,
                    clusterName, namespaceName));
        }

        // 合并子命名空间到父命名空间，并发布一次master
        Release release = releaseService.mergeBranchChangeSetsAndRelease(namespace, branchName, releaseName,
                releaseComment, isEmergencyPublish, changeSets);

        // 如果需要删除分支，执行删除
        if (deleteBranch) {
            namespaceBranchService.deleteBranch(appId, clusterName, namespaceName, branchName,
                    NamespaceBranchStatus.MERGED, changeSets.getDataChangeLastModifiedBy());
        }

        // 通知configservice变化
        messageSender.sendMessage(ReleaseMessageKeyGenerator.generate(appId, clusterName, namespaceName),
                Topics.APOLLO_RELEASE_TOPIC);

        return BeanUtils.transform(ReleaseDTO.class, release);

    }

    @Transactional
    @PutMapping("/releases/{releaseId}/rollback")
    public void rollback(@PathVariable("releaseId") long releaseId,
                         @RequestParam("operator") String operator) {

        Release release = releaseService.rollback(releaseId, operator);

        String appId = release.getAppId();
        String clusterName = release.getClusterName();
        String namespaceName = release.getNamespaceName();
        //send release message
        messageSender.sendMessage(ReleaseMessageKeyGenerator.generate(appId, clusterName, namespaceName),
                Topics.APOLLO_RELEASE_TOPIC);
    }

    @Transactional
    @PostMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/gray-del-releases")
    public ReleaseDTO publish(@PathVariable("appId") String appId,
                              @PathVariable("clusterName") String clusterName,
                              @PathVariable("namespaceName") String namespaceName,
                              @RequestParam("operator") String operator,
                              @RequestParam("releaseName") String releaseName,
                              @RequestParam(name = "comment", required = false) String releaseComment,
                              @RequestParam(name = "isEmergencyPublish", defaultValue = "false") boolean isEmergencyPublish,
                              @RequestParam(name = "grayDelKeys") Set<String> grayDelKeys) {
        Namespace namespace = namespaceService.findOne(appId, clusterName, namespaceName);
        if (namespace == null) {
            throw new NotFoundException(String.format("Could not find namespace for %s %s %s", appId,
                    clusterName, namespaceName));
        }

        Release release = releaseService.grayDeletionPublish(namespace, releaseName, releaseComment, operator,
                isEmergencyPublish, grayDelKeys);

        //send release message
        Namespace parentNamespace = namespaceService.findParentNamespace(namespace);
        String messageCluster;
        if (parentNamespace != null) {
            messageCluster = parentNamespace.getClusterName();
        } else {
            messageCluster = clusterName;
        }
        messageSender.sendMessage(ReleaseMessageKeyGenerator.generate(appId, messageCluster, namespaceName),
                Topics.APOLLO_RELEASE_TOPIC);
        return BeanUtils.transform(ReleaseDTO.class, release);
    }

}
