package com.ctrip.framework.apollo.core.spi;

import com.ctrip.framework.apollo.core.enums.Env;

/**
 * 元数据服务提供者
 *
 * @since 1.0.0
 */
public interface MetaServerProvider extends Ordered {

    /**
     * 获取指定环境的元数据服务地址
     * 域名 或者 逗号分隔的url
     * <p>
     * Provide the Apollo meta server address, could be a domain url or comma separated ip addresses, like http://1.2.3
     * .4:8080,http://2.3.4.5:8080.
     * <br/>
     * In production environment, we suggest using one single domain like http://config.xxx.com(backed by software load
     * balancers like nginx) instead of multiple ip addresses
     */
    String getMetaServerAddress(Env targetEnv);
}
