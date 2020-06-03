package com.ctrip.framework.apollo.portal.spi.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * spring ldap配置，加载属性
 *
 * @author xm.lin xm.lin@anxincloud.com
 * @date 18-8-9 下午4:36
 */
@ConfigurationProperties(prefix = "spring.ldap")
public class LdapProperties {

    private static final int DEFAULT_PORT = 389;

    /**
     * ldap 服务 url
     * LDAP URLs of the server.
     */
    private String[] urls;

    /**
     * 操作起源基本尾缀
     * Base suffix from which all operations should originate.
     */
    private String base;

    /**
     * 用户名
     * Login username of the server.
     */
    private String username;

    /**
     * 密码
     * Login password of the server.
     */
    private String password;

    /**
     * 只读操作是否应使用匿名环境
     * Whether read-only operations should use an anonymous environment.
     */
    private boolean anonymousReadOnly;

    /**
     * 用户搜索过滤
     * User search filter
     */
    private String searchFilter;

    /**
     * ladp 规范设置
     * LDAP specification settings.
     */
    private final Map<String, String> baseEnvironment = new HashMap<>();

    public String[] getUrls() {
        return this.urls;
    }

    public void setUrls(String[] urls) {
        this.urls = urls;
    }

    public String getBase() {
        return this.base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean getAnonymousReadOnly() {
        return this.anonymousReadOnly;
    }

    public void setAnonymousReadOnly(boolean anonymousReadOnly) {
        this.anonymousReadOnly = anonymousReadOnly;
    }

    public String getSearchFilter() {
        return searchFilter;
    }

    public void setSearchFilter(String searchFilter) {
        this.searchFilter = searchFilter;
    }

    public Map<String, String> getBaseEnvironment() {
        return this.baseEnvironment;
    }

    public String[] determineUrls(Environment environment) {
        if (ObjectUtils.isEmpty(this.urls)) {
            return new String[]{"ldap://localhost:" + determinePort(environment)};
        }
        return this.urls;
    }

    private int determinePort(Environment environment) {
        Assert.notNull(environment, "Environment must not be null");
        String localPort = environment.getProperty("local.ldap.port");
        if (localPort != null) {
            return Integer.parseInt(localPort);
        }
        return DEFAULT_PORT;
    }
}
