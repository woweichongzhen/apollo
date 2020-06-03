package com.ctrip.framework.apollo.openapi.repository;

import com.ctrip.framework.apollo.openapi.entity.Consumer;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 第三方应用数据层
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConsumerRepository extends PagingAndSortingRepository<Consumer, Long> {

    /**
     * 通过应用编号查找第三方应用
     *
     * @param appId 应用编号
     * @return 第三方应用
     */
    Consumer findByAppId(String appId);

}
