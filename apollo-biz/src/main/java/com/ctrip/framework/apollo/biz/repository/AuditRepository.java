package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.Audit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 审计数据库操作
 */
public interface AuditRepository extends PagingAndSortingRepository<Audit, Long> {

    /**
     * 根据创建者查找审计
     *
     * @param owner 创建者
     * @return 审计稽核
     */
    @Query("SELECT a from Audit a WHERE a.dataChangeCreatedBy = :owner")
    List<Audit> findByOwner(@Param("owner") String owner);

    /**
     * 查找审计
     *
     * @param owner  创建者
     * @param entity 实体
     * @param op     操作类型
     * @return 审计集合
     */
    @Query("SELECT a from Audit a WHERE a.dataChangeCreatedBy = :owner AND a.entityName =:entity AND a.opName = :op")
    List<Audit> findAudits(@Param("owner") String owner, @Param("entity") String entity,
                           @Param("op") String op);
}
