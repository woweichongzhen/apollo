package com.ctrip.framework.apollo.biz.service;


import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.entity.Audit;
import com.ctrip.framework.apollo.biz.entity.Item;
import com.ctrip.framework.apollo.biz.entity.Namespace;
import com.ctrip.framework.apollo.biz.repository.ItemRepository;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.exception.NotFoundException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 项服务
 */
@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final NamespaceService namespaceService;
    private final AuditService auditService;
    private final BizConfig bizConfig;

    public ItemService(
            final ItemRepository itemRepository,
            final @Lazy NamespaceService namespaceService,
            final AuditService auditService,
            final BizConfig bizConfig) {
        this.itemRepository = itemRepository;
        this.namespaceService = namespaceService;
        this.auditService = auditService;
        this.bizConfig = bizConfig;
    }


    @Transactional
    public Item delete(long id, String operator) {
        Item item = itemRepository.findById(id).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("item not exist. ID:" + id);
        }

        item.setDeleted(true);
        item.setDataChangeLastModifiedBy(operator);
        Item deletedItem = itemRepository.save(item);

        auditService.audit(Item.class.getSimpleName(), id, Audit.OP.DELETE, operator);
        return deletedItem;
    }

    /**
     * 根据命名空间id批量删除项
     */
    @Transactional
    public int batchDelete(long namespaceId, String operator) {
        return itemRepository.deleteByNamespaceId(namespaceId, operator);
    }

    /**
     * 校验项是否存在
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @param key           键
     * @return 项
     */
    public Item findOne(String appId, String clusterName, String namespaceName, String key) {
        // 校验命名空间是否存在
        Namespace namespace = namespaceService.findOne(appId, clusterName, namespaceName);
        if (namespace == null) {
            throw new NotFoundException(
                    String.format("namespace not found for %s %s %s", appId, clusterName, namespaceName));
        }
        // 查找项
        return itemRepository.findByNamespaceIdAndKey(namespace.getId(), key);
    }

    public Item findLastOne(String appId, String clusterName, String namespaceName) {
        Namespace namespace = namespaceService.findOne(appId, clusterName, namespaceName);
        if (namespace == null) {
            throw new NotFoundException(
                    String.format("namespace not found for %s %s %s", appId, clusterName, namespaceName));
        }
        return findLastOne(namespace.getId());
    }

    /**
     * 获取最后一行
     *
     * @param namespaceId 命名空间id
     * @return 最后一项
     */
    public Item findLastOne(long namespaceId) {
        return itemRepository.findFirst1ByNamespaceIdOrderByLineNumDesc(namespaceId);
    }

    public Item findOne(long itemId) {
        Item item = itemRepository.findById(itemId).orElse(null);
        return item;
    }

    /**
     * 获取未排序的项
     *
     * @param namespaceId 命名空间id
     * @return 未排序的项
     */
    public List<Item> findItemsWithoutOrdered(Long namespaceId) {
        List<Item> items = itemRepository.findByNamespaceId(namespaceId);
        if (items == null) {
            return Collections.emptyList();
        }
        return items;
    }

    public List<Item> findItemsWithoutOrdered(String appId, String clusterName, String namespaceName) {
        Namespace namespace = namespaceService.findOne(appId, clusterName, namespaceName);
        if (namespace != null) {
            return findItemsWithoutOrdered(namespace.getId());
        }
        return Collections.emptyList();
    }

    public List<Item> findItemsWithOrdered(Long namespaceId) {
        List<Item> items = itemRepository.findByNamespaceIdOrderByLineNumAsc(namespaceId);
        if (items == null) {
            return Collections.emptyList();
        }
        return items;
    }

    public List<Item> findItemsWithOrdered(String appId, String clusterName, String namespaceName) {
        Namespace namespace = namespaceService.findOne(appId, clusterName, namespaceName);
        if (namespace != null) {
            return findItemsWithOrdered(namespace.getId());
        }
        return Collections.emptyList();
    }

    public List<Item> findItemsModifiedAfterDate(long namespaceId, Date date) {
        return itemRepository.findByNamespaceIdAndDataChangeLastModifiedTimeGreaterThan(namespaceId, date);
    }

    /**
     * 保存一项
     *
     * @param entity 项实体
     * @return 保存后的项
     */
    @Transactional
    public Item save(Item entity) {
        // 校验key长度限制
        checkItemKeyLength(entity.getKey());
        // 校验value长度限制
        checkItemValueLength(entity.getNamespaceId(), entity.getValue());

        // 保护性编程
        entity.setId(0);

        // 如果未指定行数，根据最后一项+1
        if (entity.getLineNum() == 0) {
            Item lastItem = findLastOne(entity.getNamespaceId());
            int lineNum = lastItem == null ? 1 : lastItem.getLineNum() + 1;
            entity.setLineNum(lineNum);
        }

        // 保存
        Item item = itemRepository.save(entity);

        // 新增审计
        auditService.audit(Item.class.getSimpleName(), item.getId(), Audit.OP.INSERT,
                item.getDataChangeCreatedBy());

        return item;
    }

    @Transactional
    public Item update(Item item) {
        checkItemValueLength(item.getNamespaceId(), item.getValue());
        Item managedItem = itemRepository.findById(item.getId()).orElse(null);
        BeanUtils.copyEntityProperties(item, managedItem);
        managedItem = itemRepository.save(managedItem);

        auditService.audit(Item.class.getSimpleName(), managedItem.getId(), Audit.OP.UPDATE,
                managedItem.getDataChangeLastModifiedBy());

        return managedItem;
    }

    /**
     * 校验value长度限制
     *
     * @param namespaceId 命名空间id
     * @param value       值
     * @return true符合，false 抛400
     */
    private boolean checkItemValueLength(long namespaceId, String value) {
        int limit = getItemValueLengthLimit(namespaceId);
        if (!StringUtils.isEmpty(value) && value.length() > limit) {
            throw new BadRequestException("value too long. length limit:" + limit);
        }
        return true;
    }

    /**
     * 检查键长度
     *
     * @param key 键
     * @return true符合，false抛400
     */
    private boolean checkItemKeyLength(String key) {
        if (!StringUtils.isEmpty(key)
                && key.length() > bizConfig.itemKeyLengthLimit()) {
            throw new BadRequestException("key too long. length limit:" + bizConfig.itemKeyLengthLimit());
        }
        return true;
    }

    /**
     * 获取项值的长度限制
     * 如果根据命名空间id重写过，使用重写过的值
     *
     * @param namespaceId 命名空间id
     * @return 项值的长度限制
     */
    private int getItemValueLengthLimit(long namespaceId) {
        Map<Long, Integer> namespaceValueLengthOverride = bizConfig.namespaceValueLengthLimitOverride();
        if (namespaceValueLengthOverride != null
                && namespaceValueLengthOverride.containsKey(namespaceId)) {
            return namespaceValueLengthOverride.get(namespaceId);
        }
        return bizConfig.itemValueLengthLimit();
    }

}
