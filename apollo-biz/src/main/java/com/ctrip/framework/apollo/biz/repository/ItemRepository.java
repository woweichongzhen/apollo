package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.Item;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Date;
import java.util.List;

/**
 * 项数据层
 */
public interface ItemRepository extends PagingAndSortingRepository<Item, Long> {

    /**
     * 查找项
     *
     * @param namespaceId 命名空间id
     * @param key         键
     * @return 项
     */
    Item findByNamespaceIdAndKey(Long namespaceId, String key);

    List<Item> findByNamespaceIdOrderByLineNumAsc(Long namespaceId);

    /**
     * 获取项
     *
     * @param namespaceId 命名空间id
     * @return 项
     */
    List<Item> findByNamespaceId(Long namespaceId);

    List<Item> findByNamespaceIdAndDataChangeLastModifiedTimeGreaterThan(Long namespaceId, Date date);

    /**
     * 获取最后一项
     *
     * @param namespaceId 命名空间id
     * @return 最后一项
     */
    Item findFirst1ByNamespaceIdOrderByLineNumDesc(Long namespaceId);

    /**
     * 根据命名空间id删除项，假删除
     */
    @Modifying
    @Query("update Item set isdeleted=1,DataChange_LastModifiedBy = ?2 where namespaceId = ?1")
    int deleteByNamespaceId(long namespaceId, String operator);

}
