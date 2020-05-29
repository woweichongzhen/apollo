package com.ctrip.framework.apollo.biz.message;

import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;

/**
 * 消息监听器
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ReleaseMessageListener {

    /**
     * 处理监听到的消息
     *
     * @param message 消息
     * @param channel 通道
     */
    void handleMessage(ReleaseMessage message, String channel);
}
