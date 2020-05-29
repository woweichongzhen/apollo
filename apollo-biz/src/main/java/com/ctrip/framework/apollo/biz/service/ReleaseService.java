package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.*;
import com.ctrip.framework.apollo.biz.repository.ReleaseRepository;
import com.ctrip.framework.apollo.biz.utils.ReleaseKeyGenerator;
import com.ctrip.framework.apollo.common.constants.GsonType;
import com.ctrip.framework.apollo.common.constants.ReleaseOperation;
import com.ctrip.framework.apollo.common.constants.ReleaseOperationContext;
import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.exception.NotFoundException;
import com.ctrip.framework.apollo.common.utils.GrayReleaseRuleItemTransformer;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang.time.FastDateFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Type;
import java.util.*;

/**
 * 发布服务
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Service
public class ReleaseService {

    private static final FastDateFormat TIMESTAMP_FORMAT = FastDateFormat.getInstance("yyyyMMddHHmmss");
    private static final Gson gson = new Gson();

    /**
     * 分支发布操作
     */
    private static final Set<Integer> BRANCH_RELEASE_OPERATIONS = Sets.newHashSet(
            ReleaseOperation.GRAY_RELEASE,
            ReleaseOperation.MASTER_NORMAL_RELEASE_MERGE_TO_GRAY,
            ReleaseOperation.MATER_ROLLBACK_MERGE_TO_GRAY);
    private static final Pageable FIRST_ITEM = PageRequest.of(0, 1);
    private static final Type OPERATION_CONTEXT_TYPE_REFERENCE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private final ReleaseRepository releaseRepository;
    private final ItemService itemService;
    private final AuditService auditService;
    private final NamespaceLockService namespaceLockService;
    private final NamespaceService namespaceService;
    private final NamespaceBranchService namespaceBranchService;
    private final ReleaseHistoryService releaseHistoryService;
    private final ItemSetService itemSetService;

    public ReleaseService(
            final ReleaseRepository releaseRepository,
            final ItemService itemService,
            final AuditService auditService,
            final NamespaceLockService namespaceLockService,
            final NamespaceService namespaceService,
            final NamespaceBranchService namespaceBranchService,
            final ReleaseHistoryService releaseHistoryService,
            final ItemSetService itemSetService) {
        this.releaseRepository = releaseRepository;
        this.itemService = itemService;
        this.auditService = auditService;
        this.namespaceLockService = namespaceLockService;
        this.namespaceService = namespaceService;
        this.namespaceBranchService = namespaceBranchService;
        this.releaseHistoryService = releaseHistoryService;
        this.itemSetService = itemSetService;
    }

    public Release findOne(long releaseId) {
        return releaseRepository.findById(releaseId).orElse(null);
    }

    /**
     * 获取未抛弃的发布版本
     *
     * @param releaseId 发布id
     * @return 发布按本
     */
    public Release findActiveOne(long releaseId) {
        return releaseRepository.findByIdAndIsAbandonedFalse(releaseId);
    }

    public List<Release> findByReleaseIds(Set<Long> releaseIds) {
        Iterable<Release> releases = releaseRepository.findAllById(releaseIds);
        if (releases == null) {
            return Collections.emptyList();
        }
        return Lists.newArrayList(releases);
    }

    public List<Release> findByReleaseKeys(Set<String> releaseKeys) {
        return releaseRepository.findByReleaseKeyIn(releaseKeys);
    }

    /**
     * 查找最后的发布激活
     *
     * @param namespace 命名空间
     * @return 最后的发布
     */
    public Release findLatestActiveRelease(Namespace namespace) {
        return findLatestActiveRelease(
                namespace.getAppId(),
                namespace.getClusterName(),
                namespace.getNamespaceName());
    }

    /**
     * 查找最后的发布版本
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @return 最后的发布
     */
    public Release findLatestActiveRelease(String appId, String clusterName, String namespaceName) {
        return releaseRepository.findFirstByAppIdAndClusterNameAndNamespaceNameAndIsAbandonedFalseOrderByIdDesc(
                appId,
                clusterName,
                namespaceName);
    }

    public List<Release> findAllReleases(String appId, String clusterName, String namespaceName, Pageable page) {
        List<Release> releases = releaseRepository.findByAppIdAndClusterNameAndNamespaceNameOrderByIdDesc(appId,
                clusterName,
                namespaceName,
                page);
        if (releases == null) {
            return Collections.emptyList();
        }
        return releases;
    }

    public List<Release> findActiveReleases(String appId, String clusterName, String namespaceName, Pageable page) {
        List<Release>
                releases =
                releaseRepository.findByAppIdAndClusterNameAndNamespaceNameAndIsAbandonedFalseOrderByIdDesc(appId,
                        clusterName,
                        namespaceName,
                        page);
        if (releases == null) {
            return Collections.emptyList();
        }
        return releases;
    }

    /**
     * 合并子命名空间到父命名空间，并发布一次master
     */
    @Transactional
    public Release mergeBranchChangeSetsAndRelease(Namespace namespace, String branchName, String releaseName,
                                                   String releaseComment, boolean isEmergencyPublish,
                                                   ItemChangeSets changeSets) {
        // 校验当前锁定人员是否为管理员
        checkLock(namespace, isEmergencyPublish, changeSets.getDataChangeLastModifiedBy());

        // 审计变更项，并更新到父集合中，创建变更记录保存
        itemSetService.updateSet(namespace, changeSets);

        // 查找最新的分支发布
        Release branchRelease = findLatestActiveRelease(namespace.getAppId(), branchName, namespace
                .getNamespaceName());
        long branchReleaseId = branchRelease == null ? 0 : branchRelease.getId();

        // 获取命名空间配置项
        Map<String, String> operateNamespaceItems = getNamespaceItems(namespace);

        /*
         * 新增操作上下文
         */
        Map<String, Object> operationContext = Maps.newLinkedHashMap();
        operationContext.put(ReleaseOperationContext.SOURCE_BRANCH, branchName);
        operationContext.put(ReleaseOperationContext.BASE_RELEASE_ID, branchReleaseId);
        operationContext.put(ReleaseOperationContext.IS_EMERGENCY_PUBLISH, isEmergencyPublish);

        // 执行master发布
        return masterRelease(namespace, releaseName, releaseComment, operateNamespaceItems,
                changeSets.getDataChangeLastModifiedBy(),
                ReleaseOperation.GRAY_RELEASE_MERGE_TO_MASTER, operationContext);
    }

    /**
     * 发布配置
     *
     * @param namespace          命名空间
     * @param releaseName        发布名称
     * @param releaseComment     发布备注
     * @param operator           操作者
     * @param isEmergencyPublish 是否紧急发布
     * @return 发布结果
     */
    @Transactional
    public Release publish(Namespace namespace, String releaseName, String releaseComment,
                           String operator, boolean isEmergencyPublish) {
        // 校验锁，校验锁定人是否是当前管理员
        checkLock(namespace, isEmergencyPublish, operator);

        // 获取命名空间配置
        Map<String, String> operateNamespaceItems = getNamespaceItems(namespace);

        //branch release
        // 如果存在父命名空间，则是灰度发布
        Namespace parentNamespace = namespaceService.findParentNamespace(namespace);
        if (parentNamespace != null) {
            return publishBranchNamespace(parentNamespace, namespace, operateNamespaceItems,
                    releaseName, releaseComment, operator, isEmergencyPublish);
        }

        // 查找子命名空间
        Namespace childNamespace = namespaceService.findChildNamespace(namespace);
        // 获取子命名空间的最后一次激活的发布
        Release previousRelease = null;
        if (childNamespace != null) {
            previousRelease = findLatestActiveRelease(namespace);
        }

        //master release
        Map<String, Object> operationContext = Maps.newLinkedHashMap();
        // 是否紧急发布上下文标识
        operationContext.put(ReleaseOperationContext.IS_EMERGENCY_PUBLISH, isEmergencyPublish);
        // 主分支发布
        Release release = this.masterRelease(namespace, releaseName, releaseComment, operateNamespaceItems,
                operator, ReleaseOperation.NORMAL_RELEASE, operationContext);

        //merge to branch and auto release
        // 存在子命名空间时，自动将主干合并到子命名空间，并执行子命名空间发布
        if (childNamespace != null) {
            this.mergeFromMasterAndPublishBranch(namespace, childNamespace, operateNamespaceItems,
                    releaseName, releaseComment, operator, previousRelease,
                    release, isEmergencyPublish);
        }

        return release;
    }

    /**
     * 分支灰度发布
     *
     * @param grayDelKeys 灰度删除key
     * @return 发布版本
     */
    private Release publishBranchNamespace(Namespace parentNamespace, Namespace childNamespace,
                                           Map<String, String> childNamespaceItems,
                                           String releaseName, String releaseComment,
                                           String operator, boolean isEmergencyPublish, Set<String> grayDelKeys) {
        // 找到父命名空间最后一次发布版本
        Release parentLatestRelease = findLatestActiveRelease(parentNamespace);

        // 获取父命名空间的发布配置
        Map<String, String> parentConfigurations = parentLatestRelease != null ?
                gson.fromJson(parentLatestRelease.getConfigurations(),
                        GsonType.CONFIG) : new LinkedHashMap<>();
        long baseReleaseId = parentLatestRelease == null ? 0 : parentLatestRelease.getId();

        // 合并父子配置项
        Map<String, String> configsToPublish = mergeConfiguration(parentConfigurations, childNamespaceItems);

        // 如果存在要删除的key，移除掉
        if (grayDelKeys != null && grayDelKeys.size() != 0) {
            for (String key : grayDelKeys) {
                configsToPublish.remove(key);
            }
        }

        // 分支发布执行
        return branchRelease(parentNamespace, childNamespace, releaseName, releaseComment,
                configsToPublish, baseReleaseId, operator, ReleaseOperation.GRAY_RELEASE, isEmergencyPublish,
                childNamespaceItems.keySet());

    }

    @Transactional
    public Release grayDeletionPublish(Namespace namespace, String releaseName, String releaseComment,
                                       String operator, boolean isEmergencyPublish, Set<String> grayDelKeys) {

        checkLock(namespace, isEmergencyPublish, operator);

        Map<String, String> operateNamespaceItems = getNamespaceItems(namespace);

        Namespace parentNamespace = namespaceService.findParentNamespace(namespace);

        //branch release
        if (parentNamespace != null) {
            return publishBranchNamespace(parentNamespace, namespace, operateNamespaceItems,
                    releaseName, releaseComment, operator, isEmergencyPublish, grayDelKeys);
        }
        throw new NotFoundException("Parent namespace not found");
    }

    /**
     * 校验锁定人是否是当前管理员
     *
     * @param namespace          命名空间
     * @param isEmergencyPublish 是否为紧急发布，true紧急发布，false不是
     *                           可通过设置 PortalDB 的 ServerConfig 的"emergencyPublish.supported.envs" 开启对应环境的紧急发布
     *                           比如dev环境可以开启
     * @param operator           操作者
     */
    private void checkLock(Namespace namespace, boolean isEmergencyPublish, String operator) {
        if (!isEmergencyPublish) {
            // 如果不是紧急发布，获取命名空间锁
            NamespaceLock lock = namespaceLockService.findLock(namespace.getId());
            // 如果锁存在，且创建者和前操作者是同一个人，抛出400，限制其他修改人操作
            if (lock != null
                    && lock.getDataChangeCreatedBy().equals(operator)) {
                throw new BadRequestException("Config can not be published by yourself.");
            }
        }
    }

    /**
     * 存在子命名空间时，自动将主干合并到子命名空间，并执行子命名空间发布
     *
     * @param parentNamespace       父命名空间
     * @param childNamespace        子命名空间
     * @param parentNamespaceItems  父命名空间项
     * @param releaseName           发布版本名称
     * @param releaseComment        发布注释
     * @param operator              操作者
     * @param masterPreviousRelease 上一次的主干发布版本
     * @param parentRelease         父发布版本
     * @param isEmergencyPublish    是否晋级发布
     */
    private void mergeFromMasterAndPublishBranch(Namespace parentNamespace, Namespace childNamespace,
                                                 Map<String, String> parentNamespaceItems,
                                                 String releaseName, String releaseComment,
                                                 String operator, Release masterPreviousRelease,
                                                 Release parentRelease, boolean isEmergencyPublish) {
        //create release for child namespace
        // 获取子命名空间最后的发布版本
        Release childNamespaceLatestActiveRelease = findLatestActiveRelease(childNamespace);

        // 子命名空间发布配置
        Map<String, String> childReleaseConfiguration;
        // 分支发布的key
        Collection<String> branchReleaseKeys;
        if (childNamespaceLatestActiveRelease != null) {
            childReleaseConfiguration = gson.fromJson(childNamespaceLatestActiveRelease.getConfigurations(),
                    GsonType.CONFIG);
            branchReleaseKeys = getBranchReleaseKeys(childNamespaceLatestActiveRelease.getId());
        } else {
            childReleaseConfiguration = Collections.emptyMap();
            branchReleaseKeys = null;
        }

        // 父命名空间老的配置
        Map<String, String> parentNamespaceOldConfiguration = masterPreviousRelease == null ?
                null : gson.fromJson(masterPreviousRelease.getConfigurations(),
                GsonType.CONFIG);

        // 子命名空间要发布的配置
        Map<String, String> childNamespaceToPublishConfigs =
                calculateChildNamespaceToPublishConfiguration(parentNamespaceOldConfiguration, parentNamespaceItems,
                        childReleaseConfiguration, branchReleaseKeys);

        // 如果子命名空间要发布的配置不等于原来的配置，执行分支发布
        if (!childNamespaceToPublishConfigs.equals(childReleaseConfiguration)) {
            branchRelease(parentNamespace, childNamespace, releaseName, releaseComment,
                    childNamespaceToPublishConfigs, parentRelease.getId(), operator,
                    ReleaseOperation.MASTER_NORMAL_RELEASE_MERGE_TO_GRAY, isEmergencyPublish, branchReleaseKeys);
        }

    }

    /**
     * 获取某次发布的配置key集合
     *
     * @param releaseId 发布id
     * @return 发布的配置key集合
     */
    private Collection<String> getBranchReleaseKeys(long releaseId) {
        Page<ReleaseHistory> releaseHistories = releaseHistoryService
                .findByReleaseIdAndOperationInOrderByIdDesc(releaseId, BRANCH_RELEASE_OPERATIONS, FIRST_ITEM);

        if (!releaseHistories.hasContent()) {
            return null;
        }

        // 获取发布操作上下文
        Map<String, Object> operationContext = gson
                .fromJson(releaseHistories.getContent().get(0).getOperationContext(), OPERATION_CONTEXT_TYPE_REFERENCE);

        if (operationContext == null
                || !operationContext.containsKey(ReleaseOperationContext.BRANCH_RELEASE_KEYS)) {
            return null;
        }

        return (Collection<String>) operationContext.get(ReleaseOperationContext.BRANCH_RELEASE_KEYS);
    }

    /**
     * 分支灰度发布
     *
     * @param parentNamespace     父命名空间
     * @param childNamespace      子命名空间
     * @param childNamespaceItems 子命名空间项
     * @param releaseName         发布名称
     * @param releaseComment      发布备注
     * @param operator            操作者
     * @param isEmergencyPublish  是否紧急发布
     * @return 发布版本
     */
    private Release publishBranchNamespace(Namespace parentNamespace, Namespace childNamespace,
                                           Map<String, String> childNamespaceItems,
                                           String releaseName, String releaseComment,
                                           String operator, boolean isEmergencyPublish) {
        return publishBranchNamespace(parentNamespace, childNamespace, childNamespaceItems, releaseName, releaseComment,
                operator, isEmergencyPublish, null);

    }

    /**
     * 主干命名空间发布
     *
     * @param namespace        命名空间
     * @param releaseName      发布名称
     * @param releaseComment   发布备注
     * @param configurations   发布的完整配置
     * @param operator         操作者
     * @param releaseOperation 发布操作
     * @param operationContext 发布上下文
     * @return 发布后的实体
     */
    private Release masterRelease(Namespace namespace, String releaseName, String releaseComment,
                                  Map<String, String> configurations, String operator,
                                  int releaseOperation, Map<String, Object> operationContext) {
        Release lastActiveRelease = findLatestActiveRelease(namespace);
        long previousReleaseId = lastActiveRelease == null
                ? 0
                : lastActiveRelease.getId();
        // 创建发布并保存，解除命名空间锁，审计
        Release release = createRelease(namespace, releaseName, releaseComment,
                configurations, operator);

        // 创建发布历史
        releaseHistoryService.createReleaseHistory(namespace.getAppId(), namespace.getClusterName(),
                namespace.getNamespaceName(), namespace.getClusterName(),
                release.getId(), previousReleaseId, releaseOperation,
                operationContext, operator);

        return release;
    }

    /**
     * 分支发布
     *
     * @param baseReleaseId     父发布id
     * @param branchReleaseKeys 灰度发布的key
     * @return 发布版本
     */
    private Release branchRelease(Namespace parentNamespace, Namespace childNamespace,
                                  String releaseName, String releaseComment,
                                  Map<String, String> configurations, long baseReleaseId,
                                  String operator, int releaseOperation, boolean isEmergencyPublish,
                                  Collection<String> branchReleaseKeys) {
        // 查找上一次发布版本
        Release previousRelease = findLatestActiveRelease(childNamespace.getAppId(),
                childNamespace.getClusterName(),
                childNamespace.getNamespaceName());
        long previousReleaseId = previousRelease == null ? 0 : previousRelease.getId();

        // 发布操作上下文
        Map<String, Object> releaseOperationContext = Maps.newLinkedHashMap();
        releaseOperationContext.put(ReleaseOperationContext.BASE_RELEASE_ID, baseReleaseId);
        releaseOperationContext.put(ReleaseOperationContext.IS_EMERGENCY_PUBLISH, isEmergencyPublish);
        releaseOperationContext.put(ReleaseOperationContext.BRANCH_RELEASE_KEYS, branchReleaseKeys);

        // 执行发布版本
        Release release =
                createRelease(childNamespace, releaseName, releaseComment, configurations, operator);

        //update gray release rules
        // 更新灰度发布规则
        GrayReleaseRule grayReleaseRule = namespaceBranchService.updateRulesReleaseId(childNamespace.getAppId(),
                parentNamespace.getClusterName(),
                childNamespace.getNamespaceName(),
                childNamespace.getClusterName(),
                release.getId(), operator);

        if (grayReleaseRule != null) {
            releaseOperationContext.put(ReleaseOperationContext.RULES, GrayReleaseRuleItemTransformer
                    .batchTransformFromJSON(grayReleaseRule.getRules()));
        }

        // 创建发布历史
        releaseHistoryService.createReleaseHistory(parentNamespace.getAppId(), parentNamespace.getClusterName(),
                parentNamespace.getNamespaceName(), childNamespace.getClusterName(),
                release.getId(),
                previousReleaseId, releaseOperation, releaseOperationContext, operator);

        return release;
    }

    /**
     * 合并父子命名空间配置项（子的覆盖父的）
     *
     * @param baseConfigurations  父命名空间配置
     * @param coverConfigurations 子命名空间配置，要覆盖的
     * @return 合并后的
     */
    private Map<String, String> mergeConfiguration(Map<String, String> baseConfigurations,
                                                   Map<String, String> coverConfigurations) {
        Map<String, String> result = new LinkedHashMap<>();
        //copy base configuration
        for (Map.Entry<String, String> entry : baseConfigurations.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }

        //update and publish
        // 子覆盖父的
        for (Map.Entry<String, String> entry : coverConfigurations.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    /**
     * 获取命名空间的配置
     *
     * @param namespace 命名空间
     * @return 命名空间配置map
     */
    private Map<String, String> getNamespaceItems(Namespace namespace) {
        List<Item> items = itemService.findItemsWithOrdered(namespace.getId());
        // 过滤注释和空行
        Map<String, String> configurations = new LinkedHashMap<>();
        for (Item item : items) {
            if (StringUtils.isEmpty(item.getKey())) {
                continue;
            }
            configurations.put(item.getKey(), item.getValue());
        }

        return configurations;
    }

    /**
     * 发布配置时，释放对应的命名空间的锁
     *
     * @param namespace      命名空间
     * @param name           发布配置名称
     * @param comment        备注
     * @param configurations 存档json配置数据
     * @param operator       操作者
     * @return 发布完的配置
     */
    private Release createRelease(Namespace namespace, String name, String comment,
                                  Map<String, String> configurations, String operator) {
        Release release = new Release();
        release.setReleaseKey(ReleaseKeyGenerator.generateReleaseKey(namespace));
        release.setDataChangeCreatedTime(new Date());
        release.setDataChangeCreatedBy(operator);
        release.setDataChangeLastModifiedBy(operator);
        release.setName(name);
        release.setComment(comment);
        release.setAppId(namespace.getAppId());
        release.setClusterName(namespace.getClusterName());
        release.setNamespaceName(namespace.getNamespaceName());
        release.setConfigurations(gson.toJson(configurations));
        release = releaseRepository.save(release);

        // 解锁对应命名空间
        namespaceLockService.unlock(namespace.getId());

        // 插入发布审计
        auditService.audit(Release.class.getSimpleName(), release.getId(), Audit.OP.INSERT,
                release.getDataChangeCreatedBy());

        return release;
    }

    @Transactional
    public Release rollback(long releaseId, String operator) {
        Release release = findOne(releaseId);
        if (release == null) {
            throw new NotFoundException("release not found");
        }
        if (release.isAbandoned()) {
            throw new BadRequestException("release is not active");
        }

        String appId = release.getAppId();
        String clusterName = release.getClusterName();
        String namespaceName = release.getNamespaceName();

        PageRequest page = PageRequest.of(0, 2);
        List<Release> twoLatestActiveReleases = findActiveReleases(appId, clusterName, namespaceName, page);
        if (twoLatestActiveReleases == null || twoLatestActiveReleases.size() < 2) {
            throw new BadRequestException(String.format(
                    "Can't rollback namespace(appId=%s, clusterName=%s, namespaceName=%s) because there is only one " +
                            "active release",
                    appId,
                    clusterName,
                    namespaceName));
        }

        release.setAbandoned(true);
        release.setDataChangeLastModifiedBy(operator);

        releaseRepository.save(release);

        releaseHistoryService.createReleaseHistory(appId, clusterName,
                namespaceName, clusterName, twoLatestActiveReleases.get(1).getId(),
                release.getId(), ReleaseOperation.ROLLBACK, null, operator);

        //publish child namespace if namespace has child
        rollbackChildNamespace(appId, clusterName, namespaceName, twoLatestActiveReleases, operator);

        return release;
    }

    private void rollbackChildNamespace(String appId, String clusterName, String namespaceName,
                                        List<Release> parentNamespaceTwoLatestActiveRelease, String operator) {
        Namespace parentNamespace = namespaceService.findOne(appId, clusterName, namespaceName);
        Namespace childNamespace = namespaceService.findChildNamespace(appId, clusterName, namespaceName);
        if (parentNamespace == null || childNamespace == null) {
            return;
        }

        Release childNamespaceLatestActiveRelease = findLatestActiveRelease(childNamespace);
        Map<String, String> childReleaseConfiguration;
        Collection<String> branchReleaseKeys;
        if (childNamespaceLatestActiveRelease != null) {
            childReleaseConfiguration = gson.fromJson(childNamespaceLatestActiveRelease.getConfigurations(),
                    GsonType.CONFIG);
            branchReleaseKeys = getBranchReleaseKeys(childNamespaceLatestActiveRelease.getId());
        } else {
            childReleaseConfiguration = Collections.emptyMap();
            branchReleaseKeys = null;
        }

        Release abandonedRelease = parentNamespaceTwoLatestActiveRelease.get(0);
        Release parentNamespaceNewLatestRelease = parentNamespaceTwoLatestActiveRelease.get(1);

        Map<String, String> parentNamespaceAbandonedConfiguration = gson.fromJson(abandonedRelease.getConfigurations(),
                GsonType.CONFIG);

        Map<String, String>
                parentNamespaceNewLatestConfiguration =
                gson.fromJson(parentNamespaceNewLatestRelease.getConfigurations(), GsonType.CONFIG);

        Map<String, String>
                childNamespaceNewConfiguration =
                calculateChildNamespaceToPublishConfiguration(parentNamespaceAbandonedConfiguration,
                        parentNamespaceNewLatestConfiguration, childReleaseConfiguration, branchReleaseKeys);

        //compare
        if (!childNamespaceNewConfiguration.equals(childReleaseConfiguration)) {
            branchRelease(parentNamespace, childNamespace,
                    TIMESTAMP_FORMAT.format(new Date()) + "-master-rollback-merge-to-gray", "",
                    childNamespaceNewConfiguration, parentNamespaceNewLatestRelease.getId(), operator,
                    ReleaseOperation.MATER_ROLLBACK_MERGE_TO_GRAY, false, branchReleaseKeys);
        }
    }

    /**
     * 计算合并父命名空间新配置后，子命名空间要发布的配置
     * <p>
     * 子 Namespace 的配置 Map 是包含老的父 Namespace 的配置 Map ，所以需要剔除。
     * 但是呢，剔除的过程中，又需要保留子 Namespace 的自定义的配置项
     * <p>
     * 什么情况下会未发生变化呢？
     * 例如，父 Namespace 修改配置项 timeout: 2000=> 3000 ，
     * 而恰好子 Namespace 修改配置项 timeout: 2000=> 3000 并且已经灰度发布
     *
     * @param parentNamespaceOldConfiguration         父命名空间的老配置
     * @param parentNamespaceNewConfiguration         父命名空间的新配置
     * @param childNamespaceLatestActiveConfiguration 子命名空间最后的激活配置
     * @param branchReleaseKeys                       分支发布key值集合
     * @return 子命名空间要发布的配置
     */
    private Map<String, String> calculateChildNamespaceToPublishConfiguration(
            Map<String, String> parentNamespaceOldConfiguration,
            Map<String, String> parentNamespaceNewConfiguration,
            Map<String, String> childNamespaceLatestActiveConfiguration,
            Collection<String> branchReleaseKeys) {
        //first. calculate child namespace modified configs
        // 首先，计算子命名空间自己修改的配置
        Map<String, String> childNamespaceModifiedConfiguration = calculateBranchModifiedItemsAccordingToRelease(
                parentNamespaceOldConfiguration,
                childNamespaceLatestActiveConfiguration,
                branchReleaseKeys);

        //second. append child namespace modified configs to parent namespace new latest configuration
        // 然后合并子命名空间的修改配置到父命名空间最新的版本中
        return mergeConfiguration(parentNamespaceNewConfiguration, childNamespaceModifiedConfiguration);
    }

    /**
     * 计算子命名空间修改的配置
     *
     * @param masterReleaseConfigs master发布配置
     * @param branchReleaseConfigs 分支发布配置
     * @param branchReleaseKeys    分支发布key集合
     * @return 子命名空间修改的配置
     */
    private Map<String, String> calculateBranchModifiedItemsAccordingToRelease(
            Map<String, String> masterReleaseConfigs,
            Map<String, String> branchReleaseConfigs,
            Collection<String> branchReleaseKeys) {
        Map<String, String> modifiedConfigs = new LinkedHashMap<>();

        if (CollectionUtils.isEmpty(branchReleaseConfigs)) {
            return modifiedConfigs;
        }

        // new logic, retrieve modified configurations based on branch release keys
        // 新逻辑：基于分支释放键检索修改的配置
        if (branchReleaseKeys != null) {
            for (String branchReleaseKey : branchReleaseKeys) {
                if (branchReleaseConfigs.containsKey(branchReleaseKey)) {
                    modifiedConfigs.put(branchReleaseKey, branchReleaseConfigs.get(branchReleaseKey));
                }
            }
            return modifiedConfigs;
        }

        // old logic, retrieve modified configurations by comparing branchReleaseConfigs with masterReleaseConfigs
        // 旧逻辑：通过将branchReleaseConfigs与masterReleaseConfigs比较来检索修改的配置
        if (CollectionUtils.isEmpty(masterReleaseConfigs)) {
            return branchReleaseConfigs;
        }

        for (Map.Entry<String, String> entry : branchReleaseConfigs.entrySet()) {
            if (!Objects.equals(entry.getValue(), masterReleaseConfigs.get(entry.getKey()))) {
                modifiedConfigs.put(entry.getKey(), entry.getValue());
            }
        }

        return modifiedConfigs;

    }

    /**
     * 批量删除集群下命名空间的发布版本
     */
    @Transactional
    public int batchDelete(String appId, String clusterName, String namespaceName, String operator) {
        return releaseRepository.batchDelete(appId, clusterName, namespaceName, operator);
    }

}
