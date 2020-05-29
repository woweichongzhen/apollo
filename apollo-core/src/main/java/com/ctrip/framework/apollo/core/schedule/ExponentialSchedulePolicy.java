package com.ctrip.framework.apollo.core.schedule;

/**
 * 指数的重试策略
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ExponentialSchedulePolicy implements SchedulePolicy {

    /**
     * 最低指数
     */
    private final long delayTimeLowerBound;

    /**
     * 最高重试次数
     */
    private final long delayTimeUpperBound;

    /**
     * 最后一次的延迟重试
     */
    private long lastDelayTime;

    public ExponentialSchedulePolicy(long delayTimeLowerBound, long delayTimeUpperBound) {
        this.delayTimeLowerBound = delayTimeLowerBound;
        this.delayTimeUpperBound = delayTimeUpperBound;
    }

    @Override
    public long fail() {
        long delayTime = lastDelayTime;

        // 失败基于上次重试执行指数增长
        if (delayTime == 0) {
            delayTime = delayTimeLowerBound;
        } else {
            delayTime = Math.min(lastDelayTime << 1, delayTimeUpperBound);
        }

        lastDelayTime = delayTime;

        return delayTime;
    }

    @Override
    public void success() {
        lastDelayTime = 0;
    }
}
