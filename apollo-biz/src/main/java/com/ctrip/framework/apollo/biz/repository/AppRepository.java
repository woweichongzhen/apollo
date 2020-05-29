package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.common.entity.App;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 应用信息查询
 */
public interface AppRepository extends PagingAndSortingRepository<App, Long> {

    @Query("SELECT a from App a WHERE a.name LIKE %:name%")
    List<App> findByName(@Param("name") String name);

    /**
     * 查询应用信息
     *
     * @param appId 应用编号
     * @return 应用信息
     */
    App findByAppId(String appId);
}
