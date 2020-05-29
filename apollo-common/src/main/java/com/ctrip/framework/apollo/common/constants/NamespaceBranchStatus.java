package com.ctrip.framework.apollo.common.constants;

/**
 * 命名空间分支状态
 */
public interface NamespaceBranchStatus {

    /**
     * 已删除
     */
    int DELETED = 0;

    /**
     * 正在灰度发布
     */
    int ACTIVE = 1;

    /**
     * 已合并
     */
    int MERGED = 2;

}
