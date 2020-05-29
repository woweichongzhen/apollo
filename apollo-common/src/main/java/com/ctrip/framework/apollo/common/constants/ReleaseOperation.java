package com.ctrip.framework.apollo.common.constants;

/**
 * 操作类型
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ReleaseOperation {

    /**
     * 主干发布
     */
    int NORMAL_RELEASE = 0;

    /**
     * 回滚
     */
    int ROLLBACK = 1;

    /**
     * 灰度发布
     */
    int GRAY_RELEASE = 2;

    /**
     * 更新灰度发布规则
     */
    int APPLY_GRAY_RULES = 3;

    /**
     * 合并灰度发布到master
     */
    int GRAY_RELEASE_MERGE_TO_MASTER = 4;

    /**
     * 合并master正常的发布到灰度
     */
    int MASTER_NORMAL_RELEASE_MERGE_TO_GRAY = 5;

    /**
     * master回滚到灰度
     */
    int MATER_ROLLBACK_MERGE_TO_GRAY = 6;

    /**
     * 被抛弃的灰度导致的删除
     */
    int ABANDON_GRAY_RELEASE = 7;

    /**
     * 灰度合并到主版本导致的删除
     */
    int GRAY_RELEASE_DELETED_AFTER_MERGE = 8;
}
