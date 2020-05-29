package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.Audit;
import com.ctrip.framework.apollo.biz.entity.Commit;
import com.ctrip.framework.apollo.biz.entity.Item;
import com.ctrip.framework.apollo.biz.entity.Namespace;
import com.ctrip.framework.apollo.biz.utils.ConfigChangeContentBuilder;
import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.dto.ItemDTO;
import com.ctrip.framework.apollo.common.exception.NotFoundException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 项保存业务服务
 */
@Service
public class ItemSetService {

    private final AuditService auditService;
    private final CommitService commitService;
    private final ItemService itemService;

    public ItemSetService(
            final AuditService auditService,
            final CommitService commitService,
            final ItemService itemService) {
        this.auditService = auditService;
        this.commitService = commitService;
        this.itemService = itemService;
    }

    /**
     * 更新项
     *
     * @param namespace  命名空间
     * @param changeSets 改变集合
     * @return 处理后的改变集合
     */
    @Transactional
    public ItemChangeSets updateSet(Namespace namespace, ItemChangeSets changeSets) {
        return updateSet(namespace.getAppId(), namespace.getClusterName(), namespace.getNamespaceName(), changeSets);
    }

    /**
     * 实际的更新项
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 应用空间名称
     * @param changeSet     改变集合
     * @return 处理后的改变集合
     */
    @Transactional
    public ItemChangeSets updateSet(String appId,
                                    String clusterName,
                                    String namespaceName,
                                    ItemChangeSets changeSet) {
        String operator = changeSet.getDataChangeLastModifiedBy();
        ConfigChangeContentBuilder configChangeContentBuilder = new ConfigChangeContentBuilder();

        if (!CollectionUtils.isEmpty(changeSet.getCreateItems())) {
            // 遍历保存新增项
            for (ItemDTO item : changeSet.getCreateItems()) {
                Item entity = BeanUtils.transform(Item.class, item);
                entity.setDataChangeCreatedBy(operator);
                entity.setDataChangeLastModifiedBy(operator);

                Item createdItem = itemService.save(entity);

                configChangeContentBuilder.createItem(createdItem);
            }
            // 新增项审计
            auditService.audit("ItemSet", null, Audit.OP.INSERT, operator);
        }

        if (!CollectionUtils.isEmpty(changeSet.getUpdateItems())) {
            // 修改先校验是否存在，然后更改
            for (ItemDTO item : changeSet.getUpdateItems()) {
                Item entity = BeanUtils.transform(Item.class, item);

                // 不存在404异常
                Item managedItem = itemService.findOne(entity.getId());
                if (managedItem == null) {
                    throw new NotFoundException(String.format("item not found.(key=%s)", entity.getKey()));
                }

                Item beforeUpdateItem = BeanUtils.transform(Item.class, managedItem);
                // 保护。只能修改value，comment，lastModifiedBy，lineNum
                managedItem.setValue(entity.getValue());
                managedItem.setComment(entity.getComment());
                managedItem.setLineNum(entity.getLineNum());
                managedItem.setDataChangeLastModifiedBy(operator);

                Item updatedItem = itemService.update(managedItem);

                configChangeContentBuilder.updateItem(beforeUpdateItem, updatedItem);
            }
            // 修改项审计
            auditService.audit("ItemSet", null, Audit.OP.UPDATE, operator);
        }

        if (!CollectionUtils.isEmpty(changeSet.getDeleteItems())) {
            // 删除指定的项
            for (ItemDTO item : changeSet.getDeleteItems()) {
                Item deletedItem = itemService.delete(item.getId(), operator);

                configChangeContentBuilder.deleteItem(deletedItem);
            }

            // 删除审计
            auditService.audit("ItemSet", null, Audit.OP.DELETE, operator);
        }

        if (configChangeContentBuilder.hasContent()) {
            // 创建变更记录保存
            createCommit(appId,
                    clusterName,
                    namespaceName,
                    configChangeContentBuilder.build(),
                    changeSet.getDataChangeLastModifiedBy());
        }

        return changeSet;

    }

    /**
     * 创建变更记录并保存
     *
     * @param appId               应用编号
     * @param clusterName         集群名称
     * @param namespaceName       命名空间名称
     * @param configChangeContent 配置改变项
     * @param operator            操作者
     */
    private void createCommit(String appId, String clusterName, String namespaceName, String configChangeContent,
                              String operator) {

        Commit commit = new Commit();
        commit.setAppId(appId);
        commit.setClusterName(clusterName);
        commit.setNamespaceName(namespaceName);
        commit.setChangeSets(configChangeContent);
        commit.setDataChangeCreatedBy(operator);
        commit.setDataChangeLastModifiedBy(operator);
        commitService.save(commit);
    }

}
