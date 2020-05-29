package com.ctrip.framework.apollo.portal.spi;

import com.ctrip.framework.apollo.portal.entity.bo.ReleaseHistoryBO;
import com.ctrip.framework.apollo.portal.environment.Env;

/**
 * mq服务
 */
public interface MQService {

    /**
     * 发送发布消息
     *
     * @param env            环境
     * @param releaseHistory 发布历史bo
     */
    void sendPublishMsg(Env env, ReleaseHistoryBO releaseHistory);

}
