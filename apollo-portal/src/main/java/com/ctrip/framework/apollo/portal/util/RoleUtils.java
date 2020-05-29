package com.ctrip.framework.apollo.portal.util;

import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.portal.constant.RoleType;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import java.util.Iterator;

/**
 * 角色工具类
 */
public class RoleUtils {

    /**
     * + 号 join 工具
     * 跳过空值
     */
    private static final Joiner STRING_JOINER = Joiner
            .on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR)
            .skipNulls();

    /**
     * + 号 spliter 拆分工具
     * 忽略空值，进行trim
     */
    private static final Splitter STRING_SPLITTER = Splitter
            .on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR)
            .omitEmptyStrings()
            .trimResults();

    /**
     * 构建应用拥有者角色
     *
     * @param appId 应用编号
     * @return 构建后的角色，比如： Master+appId
     */
    public static String buildAppMasterRoleName(String appId) {
        return STRING_JOINER.join(RoleType.MASTER, appId);
    }

    public static String extractAppIdFromMasterRoleName(String masterRoleName) {
        Iterator<String> parts = STRING_SPLITTER.split(masterRoleName).iterator();

        // skip role type
        if (parts.hasNext() && parts.next().equals(RoleType.MASTER) && parts.hasNext()) {
            return parts.next();
        }

        return null;
    }

    public static String extractAppIdFromRoleName(String roleName) {
        Iterator<String> parts = STRING_SPLITTER.split(roleName).iterator();
        if (parts.hasNext()) {
            String roleType = parts.next();
            if (RoleType.isValidRoleType(roleType) && parts.hasNext()) {
                return parts.next();
            }
        }
        return null;
    }

    public static String buildAppRoleName(String appId, String roleType) {
        return STRING_JOINER.join(roleType, appId);
    }

    /**
     * 构建修改命名空间的角色
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @return 修改命名空间的橘色
     */
    public static String buildModifyNamespaceRoleName(String appId, String namespaceName) {
        return buildModifyNamespaceRoleName(appId, namespaceName, null);
    }

    /**
     * 构建修改命名空间的角色
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @param env           指定环境
     * @return 修改命名空间的橘色
     */
    public static String buildModifyNamespaceRoleName(String appId, String namespaceName, String env) {
        return STRING_JOINER.join(RoleType.MODIFY_NAMESPACE, appId, namespaceName, env);
    }

    public static String buildModifyDefaultNamespaceRoleName(String appId) {
        return STRING_JOINER.join(RoleType.MODIFY_NAMESPACE, appId, ConfigConsts.NAMESPACE_APPLICATION);
    }

    /**
     * 构建发布命名空间的角色
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @return 发布环境命名空间的角色
     */
    public static String buildReleaseNamespaceRoleName(String appId, String namespaceName) {
        return buildReleaseNamespaceRoleName(appId, namespaceName, null);
    }

    /**
     * 构建发布命名空间的角色
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @param env           指定环境
     * @return 发布环境命名空间的角色
     */
    public static String buildReleaseNamespaceRoleName(String appId, String namespaceName, String env) {
        return STRING_JOINER.join(RoleType.RELEASE_NAMESPACE, appId, namespaceName, env);
    }

    public static String buildNamespaceRoleName(String appId, String namespaceName, String roleType) {
        return buildNamespaceRoleName(appId, namespaceName, roleType, null);
    }

    public static String buildNamespaceRoleName(String appId, String namespaceName, String roleType, String env) {
        return STRING_JOINER.join(roleType, appId, namespaceName, env);
    }

    public static String buildReleaseDefaultNamespaceRoleName(String appId) {
        return STRING_JOINER.join(RoleType.RELEASE_NAMESPACE, appId, ConfigConsts.NAMESPACE_APPLICATION);
    }

    /**
     * 构建命名空间目标id
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @return 命名空间权限目标id
     */
    public static String buildNamespaceTargetId(String appId, String namespaceName) {
        return buildNamespaceTargetId(appId, namespaceName, null);
    }

    /**
     * 构建命名空间目标id
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @param env           指定环境
     * @return 命名空间权限目标id
     */
    public static String buildNamespaceTargetId(String appId, String namespaceName, String env) {
        return STRING_JOINER.join(appId, namespaceName, env);
    }

    public static String buildDefaultNamespaceTargetId(String appId) {
        return STRING_JOINER.join(appId, ConfigConsts.NAMESPACE_APPLICATION);
    }

    public static String buildCreateApplicationRoleName(String permissionType, String permissionTargetId) {
        return STRING_JOINER.join(permissionType, permissionTargetId);
    }
}
