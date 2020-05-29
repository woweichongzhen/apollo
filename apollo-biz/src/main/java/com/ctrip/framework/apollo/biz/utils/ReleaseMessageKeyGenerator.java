package com.ctrip.framework.apollo.biz.utils;

import com.ctrip.framework.apollo.core.ConfigConsts;
import com.google.common.base.Joiner;

/**
 * 发布消息key生成
 */
public class ReleaseMessageKeyGenerator {

    private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR);

    /**
     * 根据标识生成消息key
     *
     * @param appId     应用编号
     * @param cluster   集群名称
     * @param namespace 命名空间名称
     * @return 消息key
     */
    public static String generate(String appId, String cluster, String namespace) {
        return STRING_JOINER.join(appId, cluster, namespace);
    }
}
