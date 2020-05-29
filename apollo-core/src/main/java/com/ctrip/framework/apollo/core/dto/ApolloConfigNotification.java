package com.ctrip.framework.apollo.core.dto;

/**
 * apollo配置通知
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ApolloConfigNotification {

    /**
     * 命名空间名称，原始命名空间
     */
    private String namespaceName;

    /**
     * 通知id，即数据库ReleaseMessage 消息表id，越大的是越新的消息
     */
    private long notificationId;

    /**
     * 通知的消息集合
     * 存在多线程修改和读取
     * 并且使用synchronized的双重自检锁初始化
     */
    private volatile ApolloNotificationMessages messages;

    //for json converter
    public ApolloConfigNotification() {
    }

    public ApolloConfigNotification(String namespaceName, long notificationId) {
        this.namespaceName = namespaceName;
        this.notificationId = notificationId;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public long getNotificationId() {
        return notificationId;
    }

    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
    }

    public ApolloNotificationMessages getMessages() {
        return messages;
    }

    public void setMessages(ApolloNotificationMessages messages) {
        this.messages = messages;
    }

    /**
     * 添加消息
     *
     * @param key            监控的key
     * @param notificationId 通知id
     */
    public void addMessage(String key, long notificationId) {
        // volatile和synchronized的双重自检锁
        if (this.messages == null) {
            synchronized (this) {
                if (this.messages == null) {
                    this.messages = new ApolloNotificationMessages();
                }
            }
        }
        this.messages.put(key, notificationId);
    }

    @Override
    public String toString() {
        return "ApolloConfigNotification{" +
                "namespaceName='" + namespaceName + '\'' +
                ", notificationId=" + notificationId +
                '}';
    }
}
