package com.ctrip.framework.apollo.portal.repository;

import com.ctrip.framework.apollo.portal.entity.po.ServerConfig;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 服务配置数据层
 */
public interface ServerConfigRepository extends PagingAndSortingRepository<ServerConfig, Long> {

    /**
     * 查找指定的服务配置
     *
     * @param key key
     * @return 服务配置
     */
    ServerConfig findByKey(String key);
}
