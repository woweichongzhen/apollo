package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.Commit;
import com.ctrip.framework.apollo.biz.repository.CommitRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 变更记录保存
 * 提供 Commit 的服务 给 Admin Service 和 Config Service
 */
@Service
public class CommitService {

    private final CommitRepository commitRepository;

    public CommitService(final CommitRepository commitRepository) {
        this.commitRepository = commitRepository;
    }

    /**
     * 保存变更记录
     *
     * @param commit 变更记录
     * @return 保存后的变更记录
     */
    @Transactional
    public Commit save(Commit commit) {
        commit.setId(0);//protection
        return commitRepository.save(commit);
    }

    public List<Commit> find(String appId, String clusterName, String namespaceName, Pageable page) {
        return commitRepository.findByAppIdAndClusterNameAndNamespaceNameOrderByIdDesc(appId, clusterName,
                namespaceName, page);
    }

    /**
     * 批量删除指定集群的命名空间的变更记录
     */
    @Transactional
    public int batchDelete(String appId, String clusterName, String namespaceName, String operator) {
        return commitRepository.batchDelete(appId, clusterName, namespaceName, operator);
    }

}
