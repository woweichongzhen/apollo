package com.ctrip.framework.apollo.portal.environment;

/**
 * portal元数据服务提供者
 * 支持配置文件
 * OS环境变量
 * 数据库
 * <p>
 * For the supporting of multiple meta server address providers.
 * From configuration file,
 * from OS environment,
 * From database,
 * ...
 * Just implement this interface
 *
 * @author wxq
 */
public interface PortalMetaServerProvider {

    /**
     * 获取元数据服务地址
     *
     * @param targetEnv environment 环境
     * @return meta server address matched environment 匹配的元数据服务地址
     */
    String getMetaServerAddress(Env targetEnv);

    /**
     * 判断指定环境的元数据服务地址是否存在
     *
     * @param targetEnv environment
     * @return environment's meta server address exists or not
     */
    boolean exists(Env targetEnv);

    /**
     * 运行时重新加载元数据服务地址
     * <p>
     * reload the meta server address in runtime
     */
    void reload();

}
