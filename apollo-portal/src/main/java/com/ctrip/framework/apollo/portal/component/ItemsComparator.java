package com.ctrip.framework.apollo.portal.component;

import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.dto.ItemDTO;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 项比较器
 */
@Component
public class ItemsComparator {

    /**
     * 比较父子项之间的区别
     *
     * @param baseNamespaceId 父命名空间id
     * @param baseItems       父项
     * @param targetItems     子项（优先，覆盖父项）
     * @return 项改变集合
     */
    public ItemChangeSets compareIgnoreBlankAndCommentItem(long baseNamespaceId, List<ItemDTO> baseItems,
                                                           List<ItemDTO> targetItems) {
        // 过滤掉父子命名空间的空白和注释项
        List<ItemDTO> filteredSourceItems = filterBlankAndCommentItem(baseItems);
        List<ItemDTO> filteredTargetItems = filterBlankAndCommentItem(targetItems);

        // 转换为键和对应项的集合
        Map<String, ItemDTO> sourceItemMap = BeanUtils.mapByKey("key", filteredSourceItems);
        Map<String, ItemDTO> targetItemMap = BeanUtils.mapByKey("key", filteredTargetItems);

        ItemChangeSets changeSets = new ItemChangeSets();

        // 遍历子项
        for (ItemDTO item : targetItems) {
            String key = item.getKey();

            ItemDTO sourceItem = sourceItemMap.get(key);
            if (sourceItem == null) {
                // 如果父项不存在，说明是新增
                ItemDTO copiedItem = copyItem(item);
                copiedItem.setNamespaceId(baseNamespaceId);
                changeSets.addCreateItem(copiedItem);
            } else if (!Objects.equals(sourceItem.getValue(), item.getValue())) {
                // 如果是值不相等，说明是更新，只有值和注释能被更新
                sourceItem.setValue(item.getValue());
                sourceItem.setComment(item.getComment());
                changeSets.addUpdateItem(sourceItem);
            }
        }

        // 再遍历父项
        for (ItemDTO item : baseItems) {
            String key = item.getKey();

            // 获取子项对应的，如果子项中没有，说明被删除了
            ItemDTO targetItem = targetItemMap.get(key);
            if (targetItem == null) {
                changeSets.addDeleteItem(item);
            }
        }

        return changeSets;
    }

    /**
     * 过滤掉项的空白和注释
     */
    private List<ItemDTO> filterBlankAndCommentItem(List<ItemDTO> items) {
        List<ItemDTO> result = new LinkedList<>();
        if (CollectionUtils.isEmpty(items)) {
            return result;
        }

        for (ItemDTO item : items) {
            if (!StringUtils.isEmpty(item.getKey())) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * 深拷贝项
     */
    private ItemDTO copyItem(ItemDTO sourceItem) {
        ItemDTO copiedItem = new ItemDTO();
        copiedItem.setKey(sourceItem.getKey());
        copiedItem.setValue(sourceItem.getValue());
        copiedItem.setComment(sourceItem.getComment());
        return copiedItem;

    }

}
