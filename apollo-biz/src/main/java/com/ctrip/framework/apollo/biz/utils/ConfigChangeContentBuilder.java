package com.ctrip.framework.apollo.biz.utils;

import com.ctrip.framework.apollo.biz.entity.Item;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.beans.BeanUtils;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * 配置改变内容构建
 */
public class ConfigChangeContentBuilder {

    private static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();

    /**
     * 创建 Item 集合
     */
    private List<Item> createItems = new LinkedList<>();

    /**
     * 更新 Item 集合
     */
    private List<ItemPair> updateItems = new LinkedList<>();

    /**
     * 删除 Item 集合
     */
    private List<Item> deleteItems = new LinkedList<>();

    /**
     * 创建项新增
     *
     * @param item 一项
     */
    public ConfigChangeContentBuilder createItem(Item item) {
        if (!StringUtils.isEmpty(item.getKey())) {
            createItems.add(cloneItem(item));
        }
        return this;
    }

    /**
     * 修改项新增
     *
     * @param oldItem 旧
     * @param newItem 新
     */
    public ConfigChangeContentBuilder updateItem(Item oldItem, Item newItem) {
        if (!oldItem.getValue().equals(newItem.getValue())) {
            ItemPair itemPair = new ItemPair(cloneItem(oldItem), cloneItem(newItem));
            updateItems.add(itemPair);
        }
        return this;
    }

    /**
     * 删除项新增
     *
     * @param item 一项
     */
    public ConfigChangeContentBuilder deleteItem(Item item) {
        if (!StringUtils.isEmpty(item.getKey())) {
            deleteItems.add(cloneItem(item));
        }
        return this;
    }

    /**
     * 判断是否有变化
     * 当且仅当有变化才记录 Commit
     *
     * @return true有变化，false无变化
     */
    public boolean hasContent() {
        return !createItems.isEmpty() || !updateItems.isEmpty() || !deleteItems.isEmpty();
    }

    /**
     * 构建
     * 因为事务第一段提交并没有更新时间,所以build时统一更新
     */
    public String build() {
        Date now = new Date();

        for (Item item : createItems) {
            item.setDataChangeLastModifiedTime(now);
        }

        for (ItemPair item : updateItems) {
            item.newItem.setDataChangeLastModifiedTime(now);
        }

        for (Item item : deleteItems) {
            item.setDataChangeLastModifiedTime(now);
        }
        return gson.toJson(this);
    }

    /**
     * 修改的一对
     */
    static class ItemPair {

        /**
         * 旧项
         */
        Item oldItem;

        /**
         * 新项
         */
        Item newItem;

        public ItemPair(Item oldItem, Item newItem) {
            this.oldItem = oldItem;
            this.newItem = newItem;
        }
    }

    /**
     * 深拷贝，克隆内部值
     *
     * @param source 源
     * @return 目标
     */
    Item cloneItem(Item source) {
        Item target = new Item();

        BeanUtils.copyProperties(source, target);

        return target;
    }

    /**
     * 转换json字符串为变更项
     *
     * @param content json字符串
     * @return 当前对象
     */
    public static ConfigChangeContentBuilder convertJsonString(String content) {
        return gson.fromJson(content, ConfigChangeContentBuilder.class);
    }

    public List<Item> getCreateItems() {
        return createItems;
    }

    public List<ItemPair> getUpdateItems() {
        return updateItems;
    }

    public List<Item> getDeleteItems() {
        return deleteItems;
    }
}
