package com.ctrip.framework.apollo.core.schedule;

/**
 * 定时任务策略
 * Schedule policy
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface SchedulePolicy {

    /**
     * 失败
     */
    long fail();

    /**
     * 成功
     */
    void success();
}
