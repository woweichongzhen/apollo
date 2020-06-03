package com.ctrip.framework.apollo.configservice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Configuration;

/**
 * eureka客户端配置，当启用客户端配置时，即自身不启动eureka server，注册到外部的eureka上
 * <p>
 * Start Eureka Client annotations according to configuration
 *
 * @author Zhiqiang Lin(linzhiqiang0514@163.com)
 */
@Configuration
@EnableEurekaClient
@ConditionalOnProperty(name = "apollo.eureka.client.enabled", havingValue = "true", matchIfMissing = true)
public class ConfigServerEurekaClientConfigure {
}
