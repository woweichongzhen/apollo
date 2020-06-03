package com.ctrip.framework.apollo.openapi.repository;

import com.ctrip.framework.apollo.openapi.entity.ConsumerToken;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Date;

/**
 * 第三方应用token数据层
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConsumerTokenRepository extends PagingAndSortingRepository<ConsumerToken, Long> {

    /**
     * 查找第三方应用token
     *
     * @param token     token
     * @param validDate token是否过期
     */
    ConsumerToken findTopByTokenAndExpiresAfter(String token, Date validDate);

    ConsumerToken findByConsumerId(Long consumerId);
}
