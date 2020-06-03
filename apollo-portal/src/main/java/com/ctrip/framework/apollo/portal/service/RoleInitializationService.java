package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.common.entity.App;

/**
 * 角色初始化接口
 */
public interface RoleInitializationService {

    /**
     * 初始化应用角色信息
     *
     * @param app 应用
     */
    void initAppRoles(App app);

    /**
     * 初始化命名空间相关角色（修改和发布）
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @param operator      操作者
     */
    void initNamespaceRoles(String appId, String namespaceName, String operator);

    /**
     * 初始化不同环境的命名空间相关角色（修改和发布）
     *
     * @param appId         应用id
     * @param namespaceName 名称
     * @param operator      操作者
     */
    void initNamespaceEnvRoles(String appId, String namespaceName, String operator);

    /**
     * 初始化具体环境的命名空间的角色
     *
     * @param appId         应用id
     * @param namespaceName 名称
     * @param env           指定环境
     * @param operator      操作者
     */
    void initNamespaceSpecificEnvRoles(String appId, String namespaceName, String env, String operator);

    /**
     * 初始化创建应用角色
     */
    void initCreateAppRole();

    /**
     * 初始化管理应用的master角色
     *
     * @param appId    应用编号
     * @param operator 操作者
     */
    void initManageAppMasterRole(String appId, String operator);

}
