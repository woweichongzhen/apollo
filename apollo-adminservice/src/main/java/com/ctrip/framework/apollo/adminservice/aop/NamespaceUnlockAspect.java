package com.ctrip.framework.apollo.adminservice.aop;

import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.entity.Item;
import com.ctrip.framework.apollo.biz.entity.Namespace;
import com.ctrip.framework.apollo.biz.entity.Release;
import com.ctrip.framework.apollo.biz.service.ItemService;
import com.ctrip.framework.apollo.biz.service.NamespaceLockService;
import com.ctrip.framework.apollo.biz.service.NamespaceService;
import com.ctrip.framework.apollo.biz.service.ReleaseService;
import com.ctrip.framework.apollo.common.constants.GsonType;
import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.dto.ItemDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 释放 NamespaceLock 切面
 * 在配置多次修改，恢复到原有状态( 即最后一次 Release 的配置)
 * <p>
 * 如果重做操作，则解锁名称空间
 * 例如：如果名称空间具有项 K1 = v1
 * 第一个操作：更改 k1 = v2（锁定名称空间）
 * 第二个操作：更改 k1 = v1（解锁名称空间）
 * <p>
 * unlock namespace if is redo operation.
 * --------------------------------------------
 * For example: If namespace has a item K1 = v1
 * --------------------------------------------
 * First operate: change k1 = v2 (lock namespace)
 * Second operate: change k1 = v1 (unlock namespace)
 */
@Aspect
@Component
public class NamespaceUnlockAspect {

    private final Gson gson = new Gson();

    private final NamespaceLockService namespaceLockService;
    private final NamespaceService namespaceService;
    private final ItemService itemService;
    private final ReleaseService releaseService;
    private final BizConfig bizConfig;

    public NamespaceUnlockAspect(
            final NamespaceLockService namespaceLockService,
            final NamespaceService namespaceService,
            final ItemService itemService,
            final ReleaseService releaseService,
            final BizConfig bizConfig) {
        this.namespaceLockService = namespaceLockService;
        this.namespaceService = namespaceService;
        this.itemService = itemService;
        this.releaseService = releaseService;
        this.bizConfig = bizConfig;
    }

    /**
     * 创建项的锁切面
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @param item          项
     */
    @After("@annotation(PreAcquireNamespaceLock) && args(appId, clusterName, namespaceName, item, ..)")
    public void requireLockAdvice(String appId, String clusterName, String namespaceName,
                                  ItemDTO item) {
        tryUnlock(namespaceService.findOne(appId, clusterName, namespaceName));
    }

    /**
     * 更新项的锁切面
     *
     * @param appId         应用id
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @param itemId        项id
     * @param item          项
     */
    @After("@annotation(PreAcquireNamespaceLock) && args(appId, clusterName, namespaceName, itemId, item, ..)")
    public void requireLockAdvice(String appId, String clusterName, String namespaceName, long itemId,
                                  ItemDTO item) {
        tryUnlock(namespaceService.findOne(appId, clusterName, namespaceName));
    }

    /**
     * 通过变更集修改项
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @param changeSet     改变项集合
     */
    @After("@annotation(PreAcquireNamespaceLock) && args(appId, clusterName, namespaceName, changeSet, ..)")
    public void requireLockAdvice(String appId, String clusterName, String namespaceName,
                                  ItemChangeSets changeSet) {
        tryUnlock(namespaceService.findOne(appId, clusterName, namespaceName));
    }

    /**
     * 删除项切面
     *
     * @param itemId   项id
     * @param operator 操作者
     */
    @After("@annotation(PreAcquireNamespaceLock) && args(itemId, operator, ..)")
    public void requireLockAdvice(long itemId, String operator) {
        Item item = itemService.findOne(itemId);
        if (item == null) {
            throw new BadRequestException("item not exist.");
        }
        tryUnlock(namespaceService.findOne(item.getNamespaceId()));
    }

    /**
     * 尝试解锁
     *
     * @param namespace 命名空间
     */
    private void tryUnlock(Namespace namespace) {
        // 锁开关关闭，直接返回
        if (bizConfig.isNamespaceLockSwitchOff()) {
            return;
        }

        // 若当前命名空间配置恢复原有状态，即未修改，恢复原有状态
        if (!isModified(namespace)) {
            namespaceLockService.unlock(namespace.getId());
        }
    }

    /**
     * 判断命名空间是否有修改
     *
     * @param namespace 命名空间
     * @return true可修改，flase未修改
     */
    boolean isModified(Namespace namespace) {
        // 获取当前命名空间最后发布的对象
        Release release = releaseService.findLatestActiveRelease(namespace);
        // 获取未排序的项
        List<Item> items = itemService.findItemsWithoutOrdered(namespace.getId());

        // 无发布版本，返回是否有正常项
        if (release == null) {
            return hasNormalItems(items);
        }

        // 转换发布的完整配置为map
        Map<String, String> releasedConfiguration = gson.fromJson(release.getConfigurations(), GsonType.CONFIG);
        // 基于已有的项和新项整合的map
        Map<String, String> configurationFromItems = generateConfigurationFromItems(namespace, items);

        // 比较发布版本配置和整合后的map是否有区别
        MapDifference<String, String> difference = Maps.difference(releasedConfiguration, configurationFromItems);
        // 不相等则有区别
        return !difference.areEqual();
    }

    /**
     * 是否有正常条目
     *
     * @param items 项
     * @return true包含，flase无
     */
    private boolean hasNormalItems(List<Item> items) {
        for (Item item : items) {
            if (!StringUtils.isEmpty(item.getKey())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 根据项生成配置
     *
     * @param namespace      命名空间
     * @param namespaceItems 命名空间项
     * @return 配置
     */
    private Map<String, String> generateConfigurationFromItems(Namespace namespace, List<Item> namespaceItems) {
        Map<String, String> configurationFromItems = Maps.newHashMap();

        // 获取父命名空间
        Namespace parentNamespace = namespaceService.findParentNamespace(namespace);
        //parent namespace
        if (parentNamespace == null) {
            // 如果不存在父命名空间，直接使用自己的map
            generateMapFromItems(namespaceItems, configurationFromItems);
        } else {//child namespace
            // 如果存在父命名空间，说明是灰度发布，先获取父的最后发布，然后进行合并
            Release parentRelease = releaseService.findLatestActiveRelease(parentNamespace);
            if (parentRelease != null) {
                // 转换配置为map
                configurationFromItems = gson.fromJson(parentRelease.getConfigurations(), GsonType.CONFIG);
            }
            // 基于父map生成
            generateMapFromItems(namespaceItems, configurationFromItems);
        }

        return configurationFromItems;
    }

    /**
     * 基于已配置的map，再生成项
     *
     * @param items                  项
     * @param configurationFromItems 来自项的配置map
     * @return 整合后的
     */
    private Map<String, String> generateMapFromItems(List<Item> items, Map<String, String> configurationFromItems) {
        for (Item item : items) {
            String key = item.getKey();
            // 跳过注释和空白行
            if (StringUtils.isBlank(key)) {
                continue;
            }
            configurationFromItems.put(key, item.getValue());
        }

        return configurationFromItems;
    }

}
