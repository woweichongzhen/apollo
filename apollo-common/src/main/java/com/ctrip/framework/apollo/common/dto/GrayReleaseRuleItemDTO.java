package com.ctrip.framework.apollo.common.dto;

import com.google.common.collect.Sets;

import java.util.Set;

import static com.google.common.base.MoreObjects.toStringHelper;

/**
 * 灰度发布规则项DTO
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class GrayReleaseRuleItemDTO {

    public static final String ALL_IP = "*";

    /**
     * 客户端应用编号
     * <p>
     * 为什么会有 clientAppId 字段呢？对于公共 Namespace 的灰度规则，需要先指定要灰度的 appId ，然后再选择 IP 。
     * <p>
     * 默认公共 namespace 就允许被所有应用使用的，可以认为是一个隐性的关联。
     * 在应用界面上的关联是为了覆盖公共配置使用的。
     * 客户端的 appId 是获取自己的配置，公共配置的获取不需要 appid 。
     * 从而实现公用类型的 Namespace ，可以设置对任意 App 灰度发布。
     */
    private final String clientAppId;

    /**
     * 客户端ip集合
     */
    private final Set<String> clientIpList;

    public GrayReleaseRuleItemDTO(String clientAppId) {
        this(clientAppId, Sets.newHashSet());
    }

    public GrayReleaseRuleItemDTO(String clientAppId, Set<String> clientIpList) {
        this.clientAppId = clientAppId;
        this.clientIpList = clientIpList;
    }

    public String getClientAppId() {
        return clientAppId;
    }

    public Set<String> getClientIpList() {
        return clientIpList;
    }

    /**
     * 判断客户端应用编号和ip是否符合
     */
    public boolean matches(String clientAppId, String clientIp) {
        return appIdMatches(clientAppId) && ipMatches(clientIp);
    }

    /**
     * 判断应用编号是否符合（忽视大小写equal）
     */
    private boolean appIdMatches(String clientAppId) {
        return this.clientAppId.equalsIgnoreCase(clientAppId);
    }

    /**
     * 判断ip是否符合
     * 是指定了所有ip，还是指定的ip集合是否包含了ip
     */
    private boolean ipMatches(String clientIp) {
        return clientIpList.contains(ALL_IP)
                || clientIpList.contains(clientIp);
    }

    @Override
    public String toString() {
        return toStringHelper(this).add("clientAppId", clientAppId)
                .add("clientIpList", clientIpList).toString();
    }
}
