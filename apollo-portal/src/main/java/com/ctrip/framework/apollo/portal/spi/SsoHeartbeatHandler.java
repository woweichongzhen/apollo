package com.ctrip.framework.apollo.portal.spi;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * sso心跳处理
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface SsoHeartbeatHandler {

    /**
     * 实现心跳处理逻辑
     *
     * @param request  请求
     * @param response 返回
     */
    void doHeartbeat(HttpServletRequest request, HttpServletResponse response);
}
