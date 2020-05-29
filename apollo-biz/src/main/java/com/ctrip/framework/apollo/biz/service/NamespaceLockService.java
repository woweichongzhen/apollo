package com.ctrip.framework.apollo.biz.service;

import com.ctrip.framework.apollo.biz.entity.NamespaceLock;
import com.ctrip.framework.apollo.biz.repository.NamespaceLockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 命名空间锁服务
 * <p>
 * NamespaceLock 的 Service 逻辑给 Admin Service 和 Config Service
 */
@Service
public class NamespaceLockService {

    private final NamespaceLockRepository namespaceLockRepository;

    public NamespaceLockService(final NamespaceLockRepository namespaceLockRepository) {
        this.namespaceLockRepository = namespaceLockRepository;
    }

    /**
     * 查找锁是否存在
     *
     * @param namespaceId 命名空间id
     * @return 锁
     */
    public NamespaceLock findLock(Long namespaceId) {
        return namespaceLockRepository.findByNamespaceId(namespaceId);
    }

    /**
     * 尝试获取指定id的锁
     *
     * @param lock 锁
     * @return 通过保存获取锁，返回结果中有id则获取成功，否则未获取成功
     */
    @Transactional
    public NamespaceLock tryLock(NamespaceLock lock) {
        return namespaceLockRepository.save(lock);
    }

    /**
     * 解锁
     *
     * @param namespaceId 命名空间id
     */
    @Transactional
    public void unlock(Long namespaceId) {
        namespaceLockRepository.deleteByNamespaceId(namespaceId);
    }
}
