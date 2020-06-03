package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.enums.Env;
import com.ctrip.framework.apollo.core.spi.MetaServerProvider;
import com.ctrip.framework.foundation.Foundation;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认的元数据服务提供者
 */
public class DefaultMetaServerProvider implements MetaServerProvider {

    /**
     * 当前实现类的排序
     */
    public static final int ORDER = 0;

    private static final Logger logger = LoggerFactory.getLogger(DefaultMetaServerProvider.class);

    /**
     * 元数据服务地址
     */
    private final String metaServerAddress;

    public DefaultMetaServerProvider() {
        metaServerAddress = initMetaServerAddress();
    }

    /**
     * 初始化元数据服务地址
     *
     * @return 元数据服务地址
     */
    private String initMetaServerAddress() {
        // JVM环境变量
        String metaAddress = System.getProperty(ConfigConsts.APOLLO_META_KEY);
        if (Strings.isNullOrEmpty(metaAddress)) {
            // OS环境变量
            metaAddress = System.getenv("APOLLO_META");
        }
        if (Strings.isNullOrEmpty(metaAddress)) {
            // 从配置文件 server.properties 中读取
            metaAddress = Foundation.server().getProperty(ConfigConsts.APOLLO_META_KEY, null);
        }
        if (Strings.isNullOrEmpty(metaAddress)) {
            // 从配置文件 app.properties 中读取
            metaAddress = Foundation.app().getProperty(ConfigConsts.APOLLO_META_KEY, null);
        }

        if (Strings.isNullOrEmpty(metaAddress)) {
            logger.warn("Could not find meta server address, because it is not available in neither (1) JVM system " +
                    "property 'apollo.meta', (2) OS env variable 'APOLLO_META' (3) property 'apollo.meta' from server" +
                    ".properties nor (4) property 'apollo.meta' from app.properties");
        } else {
            metaAddress = metaAddress.trim();
            logger.info("Located meta services from apollo.meta configuration: {}!", metaAddress);
        }

        return metaAddress;
    }

    @Override
    public String getMetaServerAddress(Env targetEnv) {
        // 对于默认的元服务器提供程序，我们不在乎实际环境
        return metaServerAddress;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
