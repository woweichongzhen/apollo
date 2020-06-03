package com.ctrip.framework.apollo.portal.constant;

/**
 * 角色类型
 */
public class RoleType {

    /**
     * 拥有者
     */
    public static final String MASTER = "Master";

    /**
     * 修改命名空间的角色
     */
    public static final String MODIFY_NAMESPACE = "ModifyNamespace";

    /**
     * 发布命名空间的角色
     */
    public static final String RELEASE_NAMESPACE = "ReleaseNamespace";

    /**
     * 校验角色类型是否正确
     *
     * @param roleType 角色类型
     * @return true正确，false不正确
     */
    public static boolean isValidRoleType(String roleType) {
        return MASTER.equals(roleType)
                || MODIFY_NAMESPACE.equals(roleType)
                || RELEASE_NAMESPACE.equals(roleType);
    }

}
