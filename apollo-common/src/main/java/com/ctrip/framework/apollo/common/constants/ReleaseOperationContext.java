package com.ctrip.framework.apollo.common.constants;

/**
 * 发布操作上下文
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ReleaseOperationContext {

    /**
     * 源分支
     */
    String SOURCE_BRANCH = "sourceBranch";

    /**
     * 新规则
     */
    String RULES = "rules";

    /**
     * 旧规则
     */
    String OLD_RULES = "oldRules";

    /**
     * 父发布id
     */
    String BASE_RELEASE_ID = "baseReleaseId";

    /**
     * 是否紧急发布上下文标识
     */
    String IS_EMERGENCY_PUBLISH = "isEmergencyPublish";

    /**
     * 分支发布的键值集合
     */
    String BRANCH_RELEASE_KEYS = "branchReleaseKeys";
}
