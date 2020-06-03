package com.ctrip.framework.apollo.util.factory;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.OrderedProperties;

import java.util.Properties;

/**
 * 默认的属性工厂
 * Default PropertiesFactory implementation.
 *
 * @author songdragon@zts.io
 */
public class DefaultPropertiesFactory implements PropertiesFactory {

    private final ConfigUtil configUtil;

    public DefaultPropertiesFactory() {
        configUtil = ApolloInjector.getInstance(ConfigUtil.class);
    }

    @Override
    public Properties getPropertiesInstance() {
        if (configUtil.isPropertiesOrderEnabled()) {
            return new OrderedProperties();
        } else {
            return new Properties();
        }
    }
}
