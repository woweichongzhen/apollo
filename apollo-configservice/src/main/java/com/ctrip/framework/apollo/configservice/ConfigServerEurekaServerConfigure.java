package com.ctrip.framework.apollo.configservice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.context.annotation.Configuration;

/**
 * 开启eureka 服务端配置，自身就是一个eureka server
 * <p>
 * Start Eureka Server annotations according to configuration
 *
 * @author Zhiqiang Lin(linzhiqiang0514@163.com)
 */
@Configuration
@EnableEurekaServer
@ConditionalOnProperty(name = "apollo.eureka.server.enabled", havingValue = "true", matchIfMissing = true)
public class ConfigServerEurekaServerConfigure {
}
