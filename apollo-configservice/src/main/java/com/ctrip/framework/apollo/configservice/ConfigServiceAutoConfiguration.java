package com.ctrip.framework.apollo.configservice;

import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.grayReleaseRule.GrayReleaseRulesHolder;
import com.ctrip.framework.apollo.biz.message.ReleaseMessageScanner;
import com.ctrip.framework.apollo.configservice.controller.ConfigFileController;
import com.ctrip.framework.apollo.configservice.controller.NotificationController;
import com.ctrip.framework.apollo.configservice.controller.NotificationControllerV2;
import com.ctrip.framework.apollo.configservice.filter.ClientAuthenticationFilter;
import com.ctrip.framework.apollo.configservice.service.ReleaseMessageServiceWithCache;
import com.ctrip.framework.apollo.configservice.service.config.ConfigService;
import com.ctrip.framework.apollo.configservice.service.config.ConfigServiceWithCache;
import com.ctrip.framework.apollo.configservice.service.config.DefaultConfigService;
import com.ctrip.framework.apollo.configservice.util.AccessKeyUtil;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

/**
 * 配置服务需要加载的一些bean
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Configuration
public class ConfigServiceAutoConfiguration {

    private final BizConfig bizConfig;

    public ConfigServiceAutoConfiguration(final BizConfig bizConfig) {
        this.bizConfig = bizConfig;
    }

    @Bean
    public GrayReleaseRulesHolder grayReleaseRulesHolder() {
        return new GrayReleaseRulesHolder();
    }

    /**
     * 根据配置是否启用缓存，决定是否把所有配置加载到缓存中
     *
     * @return 配置服务，默认不缓存
     */
    @Bean
    public ConfigService configService() {
        if (bizConfig.isConfigServiceCacheEnabled()) {
            return new ConfigServiceWithCache();
        }
        return new DefaultConfigService();
    }

    @Bean
    public static NoOpPasswordEncoder passwordEncoder() {
        return (NoOpPasswordEncoder) NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public FilterRegistrationBean clientAuthenticationFilter(AccessKeyUtil accessKeyUtil) {
        FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean();

        filterRegistrationBean.setFilter(new ClientAuthenticationFilter(accessKeyUtil));
        filterRegistrationBean.addUrlPatterns("/configs/*");
        filterRegistrationBean.addUrlPatterns("/configfiles/*");
        filterRegistrationBean.addUrlPatterns("/notifications/v2/*");

        return filterRegistrationBean;
    }

    /**
     * 消息扫描配置
     */
    @Configuration
    static class MessageScannerConfiguration {
        private final NotificationController notificationController;
        private final ConfigFileController configFileController;
        private final NotificationControllerV2 notificationControllerV2;
        private final GrayReleaseRulesHolder grayReleaseRulesHolder;
        private final ReleaseMessageServiceWithCache releaseMessageServiceWithCache;
        private final ConfigService configService;

        public MessageScannerConfiguration(
                final NotificationController notificationController,
                final ConfigFileController configFileController,
                final NotificationControllerV2 notificationControllerV2,
                final GrayReleaseRulesHolder grayReleaseRulesHolder,
                final ReleaseMessageServiceWithCache releaseMessageServiceWithCache,
                final ConfigService configService) {
            this.notificationController = notificationController;
            this.configFileController = configFileController;
            this.notificationControllerV2 = notificationControllerV2;
            this.grayReleaseRulesHolder = grayReleaseRulesHolder;
            this.releaseMessageServiceWithCache = releaseMessageServiceWithCache;
            this.configService = configService;
        }

        /**
         * 发布消息扫描
         * 注册 ReleaseMessageListener
         *
         * @return 发布消息扫描bean
         */
        @Bean
        public ReleaseMessageScanner releaseMessageScanner() {
            ReleaseMessageScanner releaseMessageScanner = new ReleaseMessageScanner();
            //0. handle release message cache
            // 处理发布消息通过缓存
            releaseMessageScanner.addMessageListener(releaseMessageServiceWithCache);
            //1. handle gray release rule
            // 处理灰度发布规则
            releaseMessageScanner.addMessageListener(grayReleaseRulesHolder);
            //2. handle server cache
            // 处理服务缓存
            releaseMessageScanner.addMessageListener(configService);
            releaseMessageScanner.addMessageListener(configFileController);
            //3. notify clients
            // 通知客户端
            releaseMessageScanner.addMessageListener(notificationControllerV2);
            releaseMessageScanner.addMessageListener(notificationController);
            return releaseMessageScanner;
        }
    }

}
