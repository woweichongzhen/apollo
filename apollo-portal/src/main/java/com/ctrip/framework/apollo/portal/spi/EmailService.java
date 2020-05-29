package com.ctrip.framework.apollo.portal.spi;

import com.ctrip.framework.apollo.portal.entity.bo.Email;

/**
 * 邮件服务接口
 */
public interface EmailService {

    /**
     * 发送邮件
     *
     * @param email 邮件
     */
    void send(Email email);

}
