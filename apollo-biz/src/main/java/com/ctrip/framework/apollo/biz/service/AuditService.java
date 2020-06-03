package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.Audit;
import com.ctrip.framework.apollo.biz.repository.AuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审计服务
 */
@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(final AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * 根据创建者查找
     *
     * @param owner 拥有者
     * @return 审计集合
     */
    List<Audit> findByOwner(String owner) {
        return auditRepository.findByOwner(owner);
    }

    /**
     * 查找审计
     *
     * @param owner  创建者
     * @param entity 实体
     * @param op     操作类型
     * @return 审计集合
     */
    List<Audit> find(String owner, String entity, String op) {
        return auditRepository.findAudits(owner, entity, op);
    }

    /**
     * 添加审计项
     *
     * @param entityName 实体名称
     * @param entityId   实体id
     * @param op         操作类型
     * @param owner      创建者
     */
    @Transactional
    void audit(String entityName, Long entityId, Audit.OP op, String owner) {
        Audit audit = new Audit();
        audit.setEntityName(entityName);
        audit.setEntityId(entityId);
        audit.setOpName(op.name());
        audit.setDataChangeCreatedBy(owner);
        auditRepository.save(audit);
    }

    @Transactional
    void audit(Audit audit) {
        auditRepository.save(audit);
    }
}
