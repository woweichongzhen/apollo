package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.ServerConfig;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 服务配置数据层
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ServerConfigRepository extends PagingAndSortingRepository<ServerConfig, Long> {
    ServerConfig findTopByKeyAndCluster(String key, String cluster);
}
