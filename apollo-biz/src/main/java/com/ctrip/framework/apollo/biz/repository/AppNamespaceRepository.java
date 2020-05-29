package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.common.entity.AppNamespace;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;
import java.util.Set;

/**
 * 应用命名空间数据层
 */
public interface AppNamespaceRepository extends PagingAndSortingRepository<AppNamespace, Long> {

    /**
     * 查找应用命名空间
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @return 应用命名空间
     */
    AppNamespace findByAppIdAndName(String appId, String namespaceName);

    List<AppNamespace> findByAppIdAndNameIn(String appId, Set<String> namespaceNames);

    AppNamespace findByNameAndIsPublicTrue(String namespaceName);

    List<AppNamespace> findByNameInAndIsPublicTrue(Set<String> namespaceNames);

    List<AppNamespace> findByAppIdAndIsPublic(String appId, boolean isPublic);

    /**
     * 获取应用命名空间
     *
     * @param appId 应用编号
     * @return 应用命名空间
     */
    List<AppNamespace> findByAppId(String appId);

    /**
     * 每次基于当前id继续拉取500条
     *
     * @param id 当前命名空间id
     * @return 500条数据
     */
    List<AppNamespace> findFirst500ByIdGreaterThanOrderByIdAsc(long id);

    @Modifying
    @Query("UPDATE AppNamespace SET IsDeleted=1,DataChange_LastModifiedBy = ?2 WHERE AppId=?1")
    int batchDeleteByAppId(String appId, String operator);

    @Modifying
    @Query("UPDATE AppNamespace SET IsDeleted=1,DataChange_LastModifiedBy = ?3 WHERE AppId=?1 and Name = ?2")
    int delete(String appId, String namespaceName, String operator);
}
