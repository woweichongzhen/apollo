package com.ctrip.framework.apollo.portal.spi.springsecurity;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;

/**
 * 基于spring security实现的用户信息持有
 */
public class SpringSecurityUserInfoHolder implements UserInfoHolder {

    @Override
    public UserInfo getUser() {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(this.getCurrentUsername());
        return userInfo;
    }

    /**
     * 获取当前用户
     *
     * @return 当前用户名
     */
    private String getCurrentUsername() {
        // 获取认证凭证
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        if (principal instanceof Principal) {
            return ((Principal) principal).getName();
        }
        return String.valueOf(principal);
    }

}
