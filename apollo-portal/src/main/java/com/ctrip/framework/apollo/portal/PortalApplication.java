package com.ctrip.framework.apollo.portal;

import com.ctrip.framework.apollo.common.ApolloCommonConfig;
import com.ctrip.framework.apollo.openapi.PortalOpenApiConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 前台启动类
 * <p>
 * 虚拟机参数：
 * -Dapollo_profile=github,auth
 * -Ddev_meta=http://localhost:8080/
 * -Dserver.port=8070
 * -Dspring.datasource.url=jdbc:mysql://localhost:3306/ApolloPortalDB?characterEncoding=utf8
 * -Dspring.datasource.username=root
 * -Dspring.datasource.password=
 * -Dlogging.file=E:/logs/apollo/apollo-portal.log
 * <p>
 * 账号：apollo/admin
 */
@EnableAspectJAutoProxy
@Configuration
@EnableAutoConfiguration
@EnableTransactionManagement
@ComponentScan(basePackageClasses = {
        ApolloCommonConfig.class,
        PortalApplication.class,
        PortalOpenApiConfig.class})
public class PortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}
