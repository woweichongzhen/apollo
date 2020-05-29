package com.ctrip.framework.apollo.portal.component.txtresolver;

import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.dto.ItemDTO;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 文件配置文本解析器，适用于 yaml、yml、json、xml 格式
 */
@Component("fileTextResolver")
public class FileTextResolver implements ConfigTextResolver {

    @Override
    public ItemChangeSets resolve(long namespaceId, String configText, List<ItemDTO> baseItems) {
        ItemChangeSets changeSets = new ItemChangeSets();
        // 以前不存在项并且也无新增，无需解析
        if (CollectionUtils.isEmpty(baseItems) && StringUtils.isEmpty(configText)) {
            return changeSets;
        }
        if (CollectionUtils.isEmpty(baseItems)) {
            // 之前没有配置，直接新增即可
            changeSets.addCreateItem(createItem(namespaceId, 0, configText));
        } else {
            // 之前有配置，创建 ItemDTO 到 ItemChangeSets 的修改项
            ItemDTO beforeItem = baseItems.get(0);
            if (!configText.equals(beforeItem.getValue())) {
                changeSets.addUpdateItem(createItem(namespaceId, beforeItem.getId(), configText));
            }
        }

        return changeSets;
    }

    /**
     * 解析配置项
     *
     * @param namespaceId 命名空间id
     * @param itemId      项id
     * @param value       值
     * @return 项dto
     */
    private ItemDTO createItem(long namespaceId, long itemId, String value) {
        ItemDTO item = new ItemDTO();
        item.setId(itemId);
        item.setNamespaceId(namespaceId);
        item.setValue(value);
        item.setLineNum(1);
        item.setKey(ConfigConsts.CONFIG_FILE_CONTENT_KEY);
        return item;
    }
}
