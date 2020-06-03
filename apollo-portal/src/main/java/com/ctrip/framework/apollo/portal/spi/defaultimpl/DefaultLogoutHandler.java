package com.ctrip.framework.apollo.portal.spi.defaultimpl;

import com.ctrip.framework.apollo.portal.spi.LogoutHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 默认的登出处理实现
 */
public class DefaultLogoutHandler implements LogoutHandler {

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            response.sendRedirect("/");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
