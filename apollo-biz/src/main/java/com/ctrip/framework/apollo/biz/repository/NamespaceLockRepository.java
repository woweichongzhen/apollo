package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.NamespaceLock;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 命名空间锁数据层
 */
public interface NamespaceLockRepository extends PagingAndSortingRepository<NamespaceLock, Long> {

    /**
     * 查找锁
     *
     * @param namespaceId 命名空间id
     * @return 锁
     */
    NamespaceLock findByNamespaceId(Long namespaceId);

    /**
     * 删除锁
     *
     * @param namespaceId 命名空间id
     * @return
     */
    Long deleteByNamespaceId(Long namespaceId);

}
