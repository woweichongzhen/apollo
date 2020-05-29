package com.ctrip.framework.apollo.portal.entity.bo;

import java.util.List;

/**
 * 邮件实体
 */
public class Email {

    /**
     * 发送邮件地址
     */
    private String senderEmailAddress;

    /**
     * 接受者
     */
    private List<String> recipients;

    /**
     * 主题
     */
    private String subject;

    /**
     * 信息
     */
    private String body;

    public String getSenderEmailAddress() {
        return senderEmailAddress;
    }

    public void setSenderEmailAddress(String senderEmailAddress) {
        this.senderEmailAddress = senderEmailAddress;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }


}
