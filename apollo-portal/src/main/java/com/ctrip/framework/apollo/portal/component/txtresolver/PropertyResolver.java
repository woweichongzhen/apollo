package com.ctrip.framework.apollo.portal.component.txtresolver;

import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.dto.ItemDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.google.common.base.Strings;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * properties 配置解析器
 * <p>
 * 通过创建新项和删除旧项来更新注释和空白项目工具
 * 通过更新来更新常规键/值项工具
 * <p>
 * normal property file resolver.
 * update comment and blank item implement by create new item and delete old item.
 * update normal key/value item implement by update.
 */
@Component("propertyResolver")
public class PropertyResolver implements ConfigTextResolver {

    /**
     * 键值对分隔符
     */
    private static final String KV_SEPARATOR = "=";

    /**
     * 行号分隔符
     */
    private static final String ITEM_SEPARATOR = "\n";

    @Override
    public ItemChangeSets resolve(long namespaceId, String configText, List<ItemDTO> baseItems) {
        // 旧的数据，key 为 lineNum ，值为旧项
        Map<Integer, ItemDTO> oldLineNumMapItem = BeanUtils.mapByKey("lineNum", baseItems);

        // 旧键值对，key 为 键，值为旧项
        Map<String, ItemDTO> oldKeyMapItem = BeanUtils.mapByKey("key", baseItems);
        // 删除空白项
        oldKeyMapItem.remove("");

        // 按行分割
        String[] newItems = configText.split(ITEM_SEPARATOR);
        // 存在重复key抛400
        if (isHasRepeatKey(newItems)) {
            throw new BadRequestException("config text has repeat key please check.");
        }

        ItemChangeSets changeSets = new ItemChangeSets();
        // 用于删除空白和注释项，key行数，value每行数据
        Map<Integer, String> newLineNumMapItem = new HashMap<>();
        int lineCounter = 1;

        // 遍历新行
        for (String newItem : newItems) {
            newItem = newItem.trim();
            newLineNumMapItem.put(lineCounter, newItem);

            // 获取行对应的老行数据
            ItemDTO oldItemByLine = oldLineNumMapItem.get(lineCounter);

            if (isCommentItem(newItem)) {
                // 新行是注释行
                handleCommentLine(namespaceId, oldItemByLine, newItem, lineCounter, changeSets);
            } else if (isBlankItem(newItem)) {
                // 新行是空白行
                handleBlankLine(namespaceId, oldItemByLine, lineCounter, changeSets);
            } else {
                // 新行是正常行
                handleNormalLine(namespaceId, oldKeyMapItem, newItem, lineCounter, changeSets);
            }

            // 行新增
            lineCounter++;
        }

        // 删除注释和空白项
        deleteCommentAndBlankItem(oldLineNumMapItem, newLineNumMapItem, changeSets);
        // 删除正常键值项
        deleteNormalKvItem(oldKeyMapItem, changeSets);

        return changeSets;
    }

    /**
     * 判断是否有重复key
     *
     * @param newItems 每行数据
     * @return true包含，false不包含，kv为null抛出400
     */
    private boolean isHasRepeatKey(String[] newItems) {
        Set<String> keys = new HashSet<>();
        int lineCounter = 1;
        int keyCount = 0;
        for (String item : newItems) {
            // 非注释或空白行
            if (!isCommentItem(item)
                    && !isBlankItem(item)) {
                keyCount++;
                // 解析后的键值对为null，抛400
                String[] kv = parseKeyValueFromItem(item);
                if (kv != null) {
                    keys.add(kv[0].toLowerCase());
                } else {
                    throw new BadRequestException("line:" + lineCounter + " key value must separate by '='");
                }
            }
            lineCounter++;
        }

        // 总行数大于去重后的，则存在重复
        return keyCount > keys.size();
    }

    /**
     * 解析一行的key和value
     *
     * @param item 一行数据
     * @return 解析后的键值对数组，不存在=返回null
     */
    private String[] parseKeyValueFromItem(String item) {
        int kvSeparator = item.indexOf(KV_SEPARATOR);
        if (kvSeparator == -1) {
            return null;
        }

        String[] kv = new String[2];
        kv[0] = item.substring(0, kvSeparator).trim();
        kv[1] = item.substring(kvSeparator + 1).trim();
        return kv;
    }

    /**
     * 处理注释行
     *
     * @param namespaceId   命名空间id
     * @param oldItemByLine 旧项
     * @param newItem       新项
     * @param lineCounter   行计数
     * @param changeSets    改变集合
     */
    private void handleCommentLine(Long namespaceId, ItemDTO oldItemByLine, String newItem, int lineCounter,
                                   ItemChangeSets changeSets) {
        String oldComment = oldItemByLine == null
                ? ""
                : oldItemByLine.getComment();
        // 若老行不是注释 或者新老注释不相等，创建注释的新增项
        // 更新注释配置，通过删除 + 添加的方式
        if (!(isCommentItem(oldItemByLine)
                && newItem.equals(oldComment))) {
            changeSets.addCreateItem(buildCommentItem(0l, namespaceId, newItem, lineCounter));
        }
    }

    /**
     * 处理空白行
     *
     * @param namespaceId 命名空间id
     * @param oldItem     老项
     * @param lineCounter 行号
     * @param changeSets  改变集合
     */
    private void handleBlankLine(Long namespaceId, ItemDTO oldItem, int lineCounter, ItemChangeSets changeSets) {
        // 如果老项非空白，新增新行的空白项
        // 更新空行配置，通过删除 + 添加的方式
        if (!isBlankItem(oldItem)) {
            changeSets.addCreateItem(buildBlankItem(0l, namespaceId, lineCounter));
        }
    }

    /**
     * 处理正常行
     *
     * @param namespaceId   命名空间id
     * @param keyMapOldItem 老项的key-map
     * @param newItem       新行
     * @param lineCounter   行号
     * @param changeSets    改变集合
     */
    private void handleNormalLine(Long namespaceId, Map<String, ItemDTO> keyMapOldItem, String newItem,
                                  int lineCounter, ItemChangeSets changeSets) {
        // 解析一行的key和value
        String[] kv = parseKeyValueFromItem(newItem);
        // kv不存在抛400
        if (kv == null) {
            throw new BadRequestException("line:" + lineCounter + " key value must separate by '='");
        }

        String newKey = kv[0];
        // 处理用户输入的换行符
        String newValue = kv[1].replace("\\n", "\n");

        // 获得老的 ItemDTO 对象
        ItemDTO oldItem = keyMapOldItem.get(newKey);
        if (oldItem == null) {
            // 老行对应key不存在，则这是新增的行
            changeSets.addCreateItem(buildNormalItem(0l, namespaceId, newKey, newValue, "", lineCounter));
        } else if (!newValue.equals(oldItem.getValue())
                || lineCounter != oldItem.getLineNum()) {
            // 新行和老行值不相等，或者老行的行号和新行不符，构建修改项
            changeSets.addUpdateItem(buildNormalItem(
                    oldItem.getId(),
                    namespaceId,
                    newKey,
                    newValue,
                    oldItem.getComment(),
                    lineCounter));
        }
        // 移除添加进更新队列的老项，这个map保留的就是还需要删除的普通项
        keyMapOldItem.remove(newKey);
    }

    /**
     * 是否是注释项
     *
     * @param item 项
     * @return true注释，false取消注释
     */
    private boolean isCommentItem(ItemDTO item) {
        return item != null && "".equals(item.getKey())
                && (item.getComment().startsWith("#") || item.getComment().startsWith("!"));
    }

    /**
     * 是否是注释项
     * #或!开头的为注释
     *
     * @param line 一行
     * @return true是注释，false非注释
     */
    private boolean isCommentItem(String line) {
        return line != null
                && (line.startsWith("#") || line.startsWith("!"));
    }

    /**
     * 是否为空白行
     *
     * @param item 项
     * @return true空白，false非空白
     */
    private boolean isBlankItem(ItemDTO item) {
        return item != null && "".equals(item.getKey()) && "".equals(item.getComment());
    }

    /**
     * 一行是否是空白项
     *
     * @param line 行
     * @return true空白，false非空白
     */
    private boolean isBlankItem(String line) {
        // 转换 null 为 ""
        return Strings.nullToEmpty(line)
                // 去除左右空格
                .trim()
                .isEmpty();
    }

    /**
     * 删除正常键值项
     *
     * @param baseKeyMapItem 无用键值map
     * @param changeSets     改变集合
     */
    private void deleteNormalKvItem(Map<String, ItemDTO> baseKeyMapItem, ItemChangeSets changeSets) {
        // 多余的项目将被删除
        for (Map.Entry<String, ItemDTO> entry : baseKeyMapItem.entrySet()) {
            changeSets.addDeleteItem(entry.getValue());
        }
    }

    /**
     * 删除注释和空白项
     *
     * @param oldLineNumMapItem 旧行map
     * @param newLineNumMapItem 新行map
     * @param changeSets        改变集合
     */
    private void deleteCommentAndBlankItem(Map<Integer, ItemDTO> oldLineNumMapItem,
                                           Map<Integer, String> newLineNumMapItem,
                                           ItemChangeSets changeSets) {
        for (Map.Entry<Integer, ItemDTO> entry : oldLineNumMapItem.entrySet()) {
            int lineNum = entry.getKey();
            ItemDTO oldItem = entry.getValue();
            String newItem = newLineNumMapItem.get(lineNum);

            // 添加删除项
            // 老项空白，新项非空白
            if ((isBlankItem(oldItem) && !isBlankItem(newItem))
                    // 老项是注释项
                    || isCommentItem(oldItem)
                    // 新项为null 或 新项不等于老项的注释
                    && (newItem == null || !newItem.equals(oldItem.getComment()))) {
                changeSets.addDeleteItem(oldItem);
            }
        }
    }

    /**
     * 构建注释项
     *
     * @param id
     * @param namespaceId 命名空间id
     * @param comment     注释
     * @param lineNum     行号
     * @return 项dto
     */
    private ItemDTO buildCommentItem(Long id, Long namespaceId, String comment, int lineNum) {
        return buildNormalItem(id, namespaceId, "", "", comment, lineNum);
    }

    /**
     * 构建空白项
     *
     * @param id
     * @param namespaceId 命名空间id
     * @param lineNum     行号
     * @return 项dto
     */
    private ItemDTO buildBlankItem(Long id, Long namespaceId, int lineNum) {
        return buildNormalItem(id, namespaceId, "", "", "", lineNum);
    }

    /**
     * 构建正常项
     *
     * @param id
     * @param namespaceId 命名空间id
     * @param key         键
     * @param value       值
     * @param comment     注释
     * @param lineNum     行号
     * @return 项dto
     */
    private ItemDTO buildNormalItem(Long id, Long namespaceId, String key, String value, String comment, int lineNum) {
        ItemDTO item = new ItemDTO(key, value, comment, lineNum);
        item.setId(id);
        item.setNamespaceId(namespaceId);
        return item;
    }
}
