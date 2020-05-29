package com.ctrip.framework.apollo.biz.message;

/**
 * 消息发送接口
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface MessageSender {

    /**
     * 发送消息到指定通道中
     *
     * @param message 消息
     * @param channel 通道
     */
    void sendMessage(String message, String channel);
}
