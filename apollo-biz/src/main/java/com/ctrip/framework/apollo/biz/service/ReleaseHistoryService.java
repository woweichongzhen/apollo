package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.Audit;
import com.ctrip.framework.apollo.biz.entity.ReleaseHistory;
import com.ctrip.framework.apollo.biz.repository.ReleaseHistoryRepository;
import com.google.gson.Gson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * 发布历史服务
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Service
public class ReleaseHistoryService {
    private Gson gson = new Gson();

    private final ReleaseHistoryRepository releaseHistoryRepository;
    private final AuditService auditService;

    public ReleaseHistoryService(
            final ReleaseHistoryRepository releaseHistoryRepository,
            final AuditService auditService) {
        this.releaseHistoryRepository = releaseHistoryRepository;
        this.auditService = auditService;
    }


    public Page<ReleaseHistory> findReleaseHistoriesByNamespace(String appId, String clusterName,
                                                                String namespaceName, Pageable
                                                                        pageable) {
        return releaseHistoryRepository.findByAppIdAndClusterNameAndNamespaceNameOrderByIdDesc(appId, clusterName,
                namespaceName, pageable);
    }

    public Page<ReleaseHistory> findByReleaseIdAndOperation(long releaseId, int operation, Pageable page) {
        return releaseHistoryRepository.findByReleaseIdAndOperationOrderByIdDesc(releaseId, operation, page);
    }

    public Page<ReleaseHistory> findByPreviousReleaseIdAndOperation(long previousReleaseId, int operation,
                                                                    Pageable page) {
        return releaseHistoryRepository.findByPreviousReleaseIdAndOperationOrderByIdDesc(previousReleaseId, operation
                , page);
    }

    /**
     * 获取发布历史
     */
    public Page<ReleaseHistory> findByReleaseIdAndOperationInOrderByIdDesc(long releaseId, Set<Integer> operations,
                                                                           Pageable page) {
        return releaseHistoryRepository.findByReleaseIdAndOperationInOrderByIdDesc(releaseId, operations, page);
    }

    /**
     * 创建发布历史
     *
     * @param branchName        分支名称
     * @param releaseId         发布id
     * @param previousReleaseId 上一个发布id
     * @param operation         操作类型
     * @param operationContext  操作上下文
     */
    @Transactional
    public ReleaseHistory createReleaseHistory(String appId, String clusterName, String
            namespaceName, String branchName, long releaseId, long previousReleaseId, int operation,
                                               Map<String, Object> operationContext, String operator) {
        ReleaseHistory releaseHistory = new ReleaseHistory();
        releaseHistory.setAppId(appId);
        releaseHistory.setClusterName(clusterName);
        releaseHistory.setNamespaceName(namespaceName);
        releaseHistory.setBranchName(branchName);
        releaseHistory.setReleaseId(releaseId);
        releaseHistory.setPreviousReleaseId(previousReleaseId);
        releaseHistory.setOperation(operation);

        // 设置操作上下文
        if (operationContext == null) {
            releaseHistory.setOperationContext("{}"); //default empty object
        } else {
            releaseHistory.setOperationContext(gson.toJson(operationContext));
        }
        releaseHistory.setDataChangeCreatedTime(new Date());
        releaseHistory.setDataChangeCreatedBy(operator);
        releaseHistory.setDataChangeLastModifiedBy(operator);

        releaseHistoryRepository.save(releaseHistory);

        // 插入操作历史审计
        auditService.audit(ReleaseHistory.class.getSimpleName(), releaseHistory.getId(),
                Audit.OP.INSERT, releaseHistory.getDataChangeCreatedBy());

        return releaseHistory;
    }

    /**
     * 删除发布历史
     */
    @Transactional
    public int batchDelete(String appId, String clusterName, String namespaceName, String operator) {
        return releaseHistoryRepository.batchDelete(appId, clusterName, namespaceName, operator);
    }
}
