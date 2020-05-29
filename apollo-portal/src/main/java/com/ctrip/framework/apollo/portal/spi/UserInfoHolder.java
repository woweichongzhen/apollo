package com.ctrip.framework.apollo.portal.spi;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;

/**
 * 用户信息持有类
 * <p>
 * 获得对用户信息的访问权，不同的公司应该有不同的实现方式
 * <p>
 * Get access to the user's information,
 * different companies should have a different implementation
 */
public interface UserInfoHolder {

    /**
     * @return 用户信息
     */
    UserInfo getUser();

}
