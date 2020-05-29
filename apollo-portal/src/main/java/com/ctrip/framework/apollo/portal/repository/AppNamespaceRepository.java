package com.ctrip.framework.apollo.portal.repository;

import com.ctrip.framework.apollo.common.entity.AppNamespace;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * 应用命名空间
 */
public interface AppNamespaceRepository extends PagingAndSortingRepository<AppNamespace, Long> {

    /**
     * 查找命名空间
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @return 命名空间
     */
    AppNamespace findByAppIdAndName(String appId, String namespaceName);

    AppNamespace findByName(String namespaceName);

    /**
     * 查找命名空间
     *
     * @param namespaceName 名称
     * @param isPublic      是否公共
     * @return 命名空间集合
     */
    List<AppNamespace> findByNameAndIsPublic(String namespaceName, boolean isPublic);

    List<AppNamespace> findByIsPublicTrue();

    List<AppNamespace> findByAppId(String appId);

    @Modifying
    @Query("UPDATE AppNamespace SET IsDeleted=1,DataChange_LastModifiedBy=?2 WHERE AppId=?1")
    int batchDeleteByAppId(String appId, String operator);

    @Modifying
    @Query("UPDATE AppNamespace SET IsDeleted=1,DataChange_LastModifiedBy = ?3 WHERE AppId=?1 and Name = ?2")
    int delete(String appId, String namespaceName, String operator);
}
