package com.ctrip.framework.apollo.portal.spi.configuration;

import com.ctrip.framework.apollo.openapi.filter.ConsumerAuthenticationFilter;
import com.ctrip.framework.apollo.openapi.util.ConsumerAuditUtil;
import com.ctrip.framework.apollo.openapi.util.ConsumerAuthUtil;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证过滤配置
 */
@Configuration
public class AuthFilterConfiguration {

    /**
     * OpenAPI认证过滤器添加，拦截 /openapi开头的url
     *
     * @param consumerAuthUtil  第三方认证工具
     * @param consumerAuditUtil 第三方审计工具
     * @return 过滤注册bean
     */
    @Bean
    public FilterRegistrationBean openApiAuthenticationFilter(ConsumerAuthUtil consumerAuthUtil,
                                                              ConsumerAuditUtil consumerAuditUtil) {
        FilterRegistrationBean openApiFilter = new FilterRegistrationBean();

        openApiFilter.setFilter(new ConsumerAuthenticationFilter(consumerAuthUtil, consumerAuditUtil));
        openApiFilter.addUrlPatterns("/openapi/*");

        return openApiFilter;
    }


}
