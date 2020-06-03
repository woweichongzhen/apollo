package com.ctrip.framework.apollo.biz.eureka;


import com.ctrip.framework.apollo.biz.config.BizConfig;
import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * apollo eureka客户端配置
 */
@Component
@Primary
public class ApolloEurekaClientConfig extends EurekaClientConfigBean {

    private final BizConfig bizConfig;

    public ApolloEurekaClientConfig(final BizConfig bizConfig) {
        this.bizConfig = bizConfig;
    }

    /**
     * 仅声明一个区域：defaultZone，但声明多个环境
     * 指定eureka服务地址获取途径
     * <p>
     * Assert only one zone: defaultZone, but multiple environments.
     */
    @Override
    public List<String> getEurekaServerServiceUrls(String myZone) {
        List<String> urls = bizConfig.eurekaServiceUrls();
        return CollectionUtils.isEmpty(urls)
                ? super.getEurekaServerServiceUrls(myZone)
                : urls;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }
}
