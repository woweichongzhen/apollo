package com.ctrip.framework.apollo.portal.spi.configuration;

import com.ctrip.framework.apollo.common.condition.ConditionalOnMissingProfile;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.spi.LogoutHandler;
import com.ctrip.framework.apollo.portal.spi.SsoHeartbeatHandler;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.ctrip.framework.apollo.portal.spi.UserService;
import com.ctrip.framework.apollo.portal.spi.ctrip.CtripLogoutHandler;
import com.ctrip.framework.apollo.portal.spi.ctrip.CtripSsoHeartbeatHandler;
import com.ctrip.framework.apollo.portal.spi.ctrip.CtripUserInfoHolder;
import com.ctrip.framework.apollo.portal.spi.ctrip.CtripUserService;
import com.ctrip.framework.apollo.portal.spi.defaultimpl.DefaultLogoutHandler;
import com.ctrip.framework.apollo.portal.spi.defaultimpl.DefaultSsoHeartbeatHandler;
import com.ctrip.framework.apollo.portal.spi.defaultimpl.DefaultUserInfoHolder;
import com.ctrip.framework.apollo.portal.spi.defaultimpl.DefaultUserService;
import com.ctrip.framework.apollo.portal.spi.ldap.ApolloLdapAuthenticationProvider;
import com.ctrip.framework.apollo.portal.spi.ldap.FilterLdapByGroupUserSearch;
import com.ctrip.framework.apollo.portal.spi.ldap.LdapUserService;
import com.ctrip.framework.apollo.portal.spi.springsecurity.SpringSecurityUserInfoHolder;
import com.ctrip.framework.apollo.portal.spi.springsecurity.SpringSecurityUserService;
import com.google.common.collect.Maps;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import javax.servlet.Filter;
import javax.sql.DataSource;
import java.util.Collections;
import java.util.EventListener;
import java.util.Map;

/**
 * 认证配置
 */
@Configuration
public class AuthConfiguration {

    /**
     * 携程内部配置
     * spring.profiles.active = ctrip
     */
    @Configuration
    @Profile("ctrip")
    static class CtripAuthAutoConfiguration {

        private final PortalConfig portalConfig;

        public CtripAuthAutoConfiguration(final PortalConfig portalConfig) {
            this.portalConfig = portalConfig;
        }

        /**
         * redis应用设置监听
         */
        @Bean
        public ServletListenerRegistrationBean redisAppSettingListner() {
            ServletListenerRegistrationBean redisAppSettingListener = new ServletListenerRegistrationBean();
            redisAppSettingListener
                    .setListener(listener("org.jasig.cas.client.credis.CRedisAppSettingListner"));
            return redisAppSettingListener;
        }

        /**
         * 登出会话监听
         */
        @Bean
        public ServletListenerRegistrationBean singleSignOutHttpSessionListener() {
            ServletListenerRegistrationBean singleSignOutHttpSessionListener = new ServletListenerRegistrationBean();
            singleSignOutHttpSessionListener
                    .setListener(listener("org.jasig.cas.client.session.SingleSignOutHttpSessionListener"));
            return singleSignOutHttpSessionListener;
        }

        /**
         * 登出会话过滤
         */
        @Bean
        public FilterRegistrationBean casFilter() {
            FilterRegistrationBean singleSignOutFilter = new FilterRegistrationBean();
            singleSignOutFilter.setFilter(filter("org.jasig.cas.client.session.SingleSignOutFilter"));
            singleSignOutFilter.addUrlPatterns("/*");
            singleSignOutFilter.setOrder(1);
            return singleSignOutFilter;
        }

        /**
         * 认证过滤
         *
         * @return
         */
        @Bean
        public FilterRegistrationBean authenticationFilter() {
            FilterRegistrationBean casFilter = new FilterRegistrationBean();

            Map<String, String> filterInitParam = Maps.newHashMap();
            filterInitParam.put("redisClusterName", "casClientPrincipal");
            filterInitParam.put("serverName", portalConfig.portalServerName());
            filterInitParam.put("casServerLoginUrl", portalConfig.casServerLoginUrl());
            //we don't want to use session to store login information, since we will be deployed to a cluster, not a
            // single instance
            filterInitParam.put("useSession", "false");
            filterInitParam.put("/openapi.*", "exclude");

            casFilter.setInitParameters(filterInitParam);
            casFilter.setFilter(filter("com.ctrip.framework.apollo.sso.filter.ApolloAuthenticationFilter"));
            casFilter.addUrlPatterns("/*");
            casFilter.setOrder(2);

            return casFilter;
        }

        @Bean
        public FilterRegistrationBean casValidationFilter() {
            FilterRegistrationBean casValidationFilter = new FilterRegistrationBean();
            Map<String, String> filterInitParam = Maps.newHashMap();
            filterInitParam.put("casServerUrlPrefix", portalConfig.casServerUrlPrefix());
            filterInitParam.put("serverName", portalConfig.portalServerName());
            filterInitParam.put("encoding", "UTF-8");
            //we don't want to use session to store login information, since we will be deployed to a cluster, not a
            // single instance
            filterInitParam.put("useSession", "false");
            filterInitParam.put("useRedis", "true");
            filterInitParam.put("redisClusterName", "casClientPrincipal");

            casValidationFilter
                    .setFilter(
                            filter("org.jasig.cas.client.validation.Cas20ProxyReceivingTicketValidationFilter"));
            casValidationFilter.setInitParameters(filterInitParam);
            casValidationFilter.addUrlPatterns("/*");
            casValidationFilter.setOrder(3);

            return casValidationFilter;
        }

        @Bean
        public FilterRegistrationBean assertionHolder() {
            FilterRegistrationBean assertionHolderFilter = new FilterRegistrationBean();

            Map<String, String> filterInitParam = Maps.newHashMap();
            filterInitParam.put("/openapi.*", "exclude");

            assertionHolderFilter.setInitParameters(filterInitParam);

            assertionHolderFilter.setFilter(
                    filter("com.ctrip.framework.apollo.sso.filter.ApolloAssertionThreadLocalFilter"));
            assertionHolderFilter.addUrlPatterns("/*");
            assertionHolderFilter.setOrder(4);

            return assertionHolderFilter;
        }

        @Bean
        public CtripUserInfoHolder ctripUserInfoHolder() {
            return new CtripUserInfoHolder();
        }

        @Bean
        public CtripLogoutHandler logoutHandler() {
            return new CtripLogoutHandler();
        }

        private Filter filter(String className) {
            Class clazz = null;
            try {
                clazz = Class.forName(className);
                Object obj = clazz.newInstance();
                return (Filter) obj;
            } catch (Exception e) {
                throw new RuntimeException("instance filter fail", e);
            }
        }

        private EventListener listener(String className) {
            Class clazz = null;
            try {
                clazz = Class.forName(className);
                Object obj = clazz.newInstance();
                return (EventListener) obj;
            } catch (Exception e) {
                throw new RuntimeException("instance listener fail", e);
            }
        }

        @Bean
        public UserService ctripUserService(PortalConfig portalConfig) {
            return new CtripUserService(portalConfig);
        }

        @Bean
        public SsoHeartbeatHandler ctripSsoHeartbeatHandler() {
            return new CtripSsoHeartbeatHandler();
        }
    }

    /**
     * apollo提供的基于spring security的简单安全配置
     * spring.profiles.active = auth
     */
    @Configuration
    @Profile("auth")
    static class SpringSecurityAuthAutoConfiguration {

        /**
         * 默认的sso心跳处理
         */
        @Bean
        @ConditionalOnMissingBean(SsoHeartbeatHandler.class)
        public SsoHeartbeatHandler defaultSsoHeartbeatHandler() {
            return new DefaultSsoHeartbeatHandler();
        }

        /**
         * spring安全提供的用户持有者
         */
        @Bean
        @ConditionalOnMissingBean(UserInfoHolder.class)
        public UserInfoHolder springSecurityUserInfoHolder() {
            return new SpringSecurityUserInfoHolder();
        }

        /**
         * 默认的登出处理
         */
        @Bean
        @ConditionalOnMissingBean(LogoutHandler.class)
        public LogoutHandler logoutHandler() {
            return new DefaultLogoutHandler();
        }

        /**
         * jdbc的用户信息管理
         *
         * @param auth       认证管理
         * @param datasource 数据源
         * @return jdb用户信息管理
         * @throws Exception 异常
         */
        @Bean
        public JdbcUserDetailsManager jdbcUserDetailsManager(AuthenticationManagerBuilder auth,
                                                             DataSource datasource) throws Exception {
            // jdb用户信息管理
            JdbcUserDetailsManager jdbcUserDetailsManager = auth.jdbcAuthentication()
                    // 密码加密
                    .passwordEncoder(new BCryptPasswordEncoder())
                    // 数据源
                    .dataSource(datasource)
                    // 根据用户名查询用户
                    .usersByUsernameQuery("select Username,Password,Enabled from `Users` where Username = ?")
                    // 根据用户名查询认证
                    .authoritiesByUsernameQuery(
                            "select Username,Authority from `Authorities` where Username = ?")
                    .getUserDetailsService();

            // 校验用户是否存在
            jdbcUserDetailsManager.setUserExistsSql("select Username from `Users` where Username = ?");
            // 创建用户
            jdbcUserDetailsManager.setCreateUserSql("insert into `Users` (Username, Password, Enabled) values (?,?,?)");
            // 更新用户（密码，是否启用）
            jdbcUserDetailsManager.setUpdateUserSql(
                    "update `Users` set Password = ?, Enabled = ? where id = " +
                            "(select u.id from " +
                            "(select id from `Users` where Username = ?) as u)");
            // 删除用户
            jdbcUserDetailsManager.setDeleteUserSql(
                    "delete from `Users` where id = " +
                            "(select u.id from " +
                            "(select id from `Users` where Username = ?) as u)");

            // 创建认证
            jdbcUserDetailsManager.setCreateAuthoritySql(
                    "insert into `Authorities` (Username, Authority) values (?,?)");
            // 删除认证
            jdbcUserDetailsManager.setDeleteUserAuthoritiesSql(
                    "delete from `Authorities` where id in " +
                            "(select a.id from " +
                            "(select id from `Authorities` where Username = ?) as a)");
            // 改变密码
            jdbcUserDetailsManager.setChangePasswordSql(
                    "update `Users` set Password = ? where id = " +
                            "(select u.id from " +
                            "(select id from `Users` where Username = ?) as u)");

            return jdbcUserDetailsManager;
        }

        /**
         * spring安全用户服务
         */
        @Bean
        @ConditionalOnMissingBean(UserService.class)
        public UserService springSecurityUserService() {
            return new SpringSecurityUserService();
        }

    }

    /**
     * spring安全配置
     * <p>
     * {@link EnableGlobalMethodSecurity} 启用前置方法安全认证，开启Security安全注解
     */
    @Order(99)
    @Profile("auth")
    @Configuration
    @EnableWebSecurity
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class SpringSecurityConfigurer extends WebSecurityConfigurerAdapter {

        public static final String USER_ROLE = "user";

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            // 禁用csrf
            http.csrf().disable();
            // 仅允许同origin访问
            http.headers().frameOptions().sameOrigin();
            http.authorizeRequests()
                    // 匹配下列路径允许直接访问
                    .antMatchers(
                            "/prometheus/**",
                            "/metrics/**",
                            "/openapi/**",
                            "/vendor/**",
                            "/styles/**",
                            "/scripts/**",
                            "/views/**",
                            "/img/**",
                            "/i18n/**",
                            "/prefix-path")
                    .permitAll()
                    // 其他路径要基于 user 角色
                    .antMatchers("/**")
                    // 该方法会自动加上 ROLE_ 前缀，相当于角色，目前apollo只有一种角色/Authority，ROLE_user
                    // 主要用方法注解进行进一步校验
                    .hasAnyRole(USER_ROLE);
            http.formLogin()
                    // 登录地址权限允许
                    .loginPage("/signin")
                    // 默认登录成功后url
                    .defaultSuccessUrl("/", true)
                    .permitAll()
                    // 登录失败页面跳转
                    .failureUrl("/signin?#/error")
                    .and()
                    // http基本认证
                    .httpBasic();
            http.logout()
                    // 登出页面地址
                    .logoutUrl("/user/logout")
                    // 销毁http会话
                    .invalidateHttpSession(true)
                    // 清理认证信息
                    .clearAuthentication(true)
                    // 登出后页面跳转
                    .logoutSuccessUrl("/signin?#/logout");
            http.exceptionHandling()
                    // 认证异常处理跳转页面
                    // 未认证跳转
                    .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/signin"));
        }

    }

    /**
     * 基于spring security ldap方式实现认证
     * spring.profiles.active = ldap
     */
    @Configuration
    @Profile("ldap")
    @EnableConfigurationProperties({LdapProperties.class, LdapExtendProperties.class})
    static class SpringSecurityLDAPAuthAutoConfiguration {

        private final LdapProperties properties;
        private final Environment environment;

        public SpringSecurityLDAPAuthAutoConfiguration(final LdapProperties properties, final Environment environment) {
            this.properties = properties;
            this.environment = environment;
        }

        /**
         * 默认sso心跳处理
         */
        @Bean
        @ConditionalOnMissingBean(SsoHeartbeatHandler.class)
        public SsoHeartbeatHandler defaultSsoHeartbeatHandler() {
            return new DefaultSsoHeartbeatHandler();
        }

        /**
         * 基于spring security实现的用户信息持有
         */
        @Bean
        @ConditionalOnMissingBean(UserInfoHolder.class)
        public UserInfoHolder springSecurityUserInfoHolder() {
            return new SpringSecurityUserInfoHolder();
        }

        /**
         * 默认的登出处理实现
         */
        @Bean
        @ConditionalOnMissingBean(LogoutHandler.class)
        public LogoutHandler logoutHandler() {
            return new DefaultLogoutHandler();
        }

        /**
         * ldap用户服务
         */
        @Bean
        @ConditionalOnMissingBean(UserService.class)
        public UserService springSecurityUserService() {
            return new LdapUserService();
        }

        /**
         * ldap上下文源
         */
        @Bean
        @ConditionalOnMissingBean
        public ContextSource ldapContextSource() {
            LdapContextSource source = new LdapContextSource();
            source.setUserDn(properties.getUsername());
            source.setPassword(properties.getPassword());
            source.setAnonymousReadOnly(properties.getAnonymousReadOnly());
            source.setBase(properties.getBase());
            source.setUrls(properties.determineUrls(environment));
            source.setBaseEnvironmentProperties(
                    Collections.unmodifiableMap(properties.getBaseEnvironment()));
            return source;
        }

        /**
         * ldap模板
         *
         * @param contextSource 上下文源
         * @return ldap模板
         */
        @Bean
        @ConditionalOnMissingBean(LdapOperations.class)
        public LdapTemplate ldapTemplate(ContextSource contextSource) {
            LdapTemplate ldapTemplate = new LdapTemplate(contextSource);
            ldapTemplate.setIgnorePartialResultException(true);
            return ldapTemplate;
        }
    }

    /**
     * ldap安全配置
     */
    @Order(99)
    @Profile("ldap")
    @Configuration
    @EnableWebSecurity
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class SpringSecurityLDAPConfigurer extends WebSecurityConfigurerAdapter {

        private final LdapProperties ldapProperties;
        private final LdapContextSource ldapContextSource;

        private final LdapExtendProperties ldapExtendProperties;

        public SpringSecurityLDAPConfigurer(final LdapProperties ldapProperties,
                                            final LdapContextSource ldapContextSource,
                                            final LdapExtendProperties ldapExtendProperties) {
            this.ldapProperties = ldapProperties;
            this.ldapContextSource = ldapContextSource;
            this.ldapExtendProperties = ldapExtendProperties;
        }

        @Bean
        public FilterBasedLdapUserSearch userSearch() {
            if (ldapExtendProperties.getGroup() == null || StringUtils
                    .isBlank(ldapExtendProperties.getGroup().getGroupSearch())) {
                FilterBasedLdapUserSearch filterBasedLdapUserSearch = new FilterBasedLdapUserSearch("",
                        ldapProperties.getSearchFilter(), ldapContextSource);
                filterBasedLdapUserSearch.setSearchSubtree(true);
                return filterBasedLdapUserSearch;
            }

            FilterLdapByGroupUserSearch filterLdapByGroupUserSearch = new FilterLdapByGroupUserSearch(
                    ldapProperties.getBase(), ldapProperties.getSearchFilter(),
                    ldapExtendProperties.getGroup().getGroupBase(),
                    ldapContextSource, ldapExtendProperties.getGroup().getGroupSearch(),
                    ldapExtendProperties.getMapping().getRdnKey(),
                    ldapExtendProperties.getGroup().getGroupMembership(),
                    ldapExtendProperties.getMapping().getLoginId());
            filterLdapByGroupUserSearch.setSearchSubtree(true);
            return filterLdapByGroupUserSearch;
        }

        @Bean
        public LdapAuthenticationProvider ldapAuthProvider() {
            BindAuthenticator bindAuthenticator = new BindAuthenticator(ldapContextSource);
            bindAuthenticator.setUserSearch(userSearch());
            DefaultLdapAuthoritiesPopulator defaultAuthAutoConfiguration = new DefaultLdapAuthoritiesPopulator(
                    ldapContextSource, null);
            defaultAuthAutoConfiguration.setIgnorePartialResultException(true);
            defaultAuthAutoConfiguration.setSearchSubtree(true);
            // Rewrite the logic of LdapAuthenticationProvider with ApolloLdapAuthenticationProvider,
            // use userId in LDAP system instead of userId input by user.
            return new ApolloLdapAuthenticationProvider(
                    bindAuthenticator, defaultAuthAutoConfiguration, ldapExtendProperties);
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.csrf().disable();
            http.headers().frameOptions().sameOrigin();
            http.authorizeRequests()
                    .antMatchers("/prometheus/**", "/metrics/**", "/openapi/**", "/vendor/**", "/styles/**",
                            "/scripts/**", "/views/**", "/img/**", "/i18n/**", "/prefix-path").permitAll()
                    .antMatchers("/**").authenticated();
            http.formLogin().loginPage("/signin").defaultSuccessUrl("/", true).permitAll().failureUrl("/signin" +
                    "?#/error").and()
                    .httpBasic();
            http.logout().logoutUrl("/user/logout").invalidateHttpSession(true).clearAuthentication(true)
                    .logoutSuccessUrl("/signin?#/logout");
            http.exceptionHandling().authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/signin"));
        }

        @Override
        protected void configure(AuthenticationManagerBuilder auth) throws Exception {
            auth.authenticationProvider(ldapAuthProvider());
        }
    }

    /**
     * 默认激活的配置，ctrip、auth、ldap都缺失时
     */
    @Configuration
    @ConditionalOnMissingProfile({"ctrip", "auth", "ldap"})
    static class DefaultAuthAutoConfiguration {

        /**
         * 默认的sso心跳处理
         */
        @Bean
        @ConditionalOnMissingBean(SsoHeartbeatHandler.class)
        public SsoHeartbeatHandler defaultSsoHeartbeatHandler() {
            return new DefaultSsoHeartbeatHandler();
        }

        /**
         * 默认的用户持有者
         */
        @Bean
        @ConditionalOnMissingBean(UserInfoHolder.class)
        public DefaultUserInfoHolder defaultUserInfoHolder() {
            return new DefaultUserInfoHolder();
        }

        /**
         * 默认登出处理
         */
        @Bean
        @ConditionalOnMissingBean(LogoutHandler.class)
        public DefaultLogoutHandler logoutHandler() {
            return new DefaultLogoutHandler();
        }

        /**
         * 默认用户服务
         */
        @Bean
        @ConditionalOnMissingBean(UserService.class)
        public UserService defaultUserService() {
            return new DefaultUserService();
        }
    }

    /**
     * ctrip和default时，网络安全配置
     */
    @ConditionalOnMissingProfile({"auth", "ldap"})
    @Configuration
    @EnableWebSecurity
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class DefaultWebSecurityConfig extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.csrf().disable();
            http.headers().frameOptions().sameOrigin();
        }
    }
}
