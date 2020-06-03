package com.ctrip.framework.apollo.openapi.util;

import com.ctrip.framework.apollo.openapi.service.ConsumerService;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

/**
 * 第三方应用审计工具类
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Service
public class ConsumerAuthUtil {

    /**
     * 请求应用id标识符
     */
    static final String CONSUMER_ID = "ApolloConsumerId";

    private final ConsumerService consumerService;

    public ConsumerAuthUtil(final ConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    /**
     * 通过token获取第三方应用id
     *
     * @param token token
     * @return 第三方应用id
     */
    public Long getConsumerId(String token) {
        return consumerService.getConsumerIdByToken(token);
    }

    /**
     * 存储应用id到请求中
     *
     * @param request    请求
     * @param consumerId 应用id
     */
    public void storeConsumerId(HttpServletRequest request, Long consumerId) {
        request.setAttribute(CONSUMER_ID, consumerId);
    }

    /**
     * 获取request中的应用编号
     *
     * @param request 请求
     * @return 应用编号
     */
    public long retrieveConsumerId(HttpServletRequest request) {
        Object value = request.getAttribute(CONSUMER_ID);

        try {
            return Long.parseLong(value.toString());
        } catch (Throwable ex) {
            throw new IllegalStateException("No consumer id!", ex);
        }
    }
}
