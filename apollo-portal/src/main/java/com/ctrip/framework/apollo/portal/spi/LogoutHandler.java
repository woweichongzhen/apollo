package com.ctrip.framework.apollo.portal.spi;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录处理
 */
public interface LogoutHandler {

    /**
     * 登出处理
     *
     * @param request  请求
     * @param response 返回
     */
    void logout(HttpServletRequest request, HttpServletResponse response);

}
