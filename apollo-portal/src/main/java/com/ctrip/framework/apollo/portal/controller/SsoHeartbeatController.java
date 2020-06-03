package com.ctrip.framework.apollo.portal.controller;

import com.ctrip.framework.apollo.portal.spi.SsoHeartbeatHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * sso心跳处理
 * 由于sso身份验证信息的有效时间有限，因此需要进行sso心跳操作，以在不可用时刷新信息
 * <p>
 * Since sso auth information has a limited expiry time, so we need to do sso heartbeat to keep the
 * information refreshed when unavailable
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Controller
@RequestMapping("/sso_heartbeat")
public class SsoHeartbeatController {
    private final SsoHeartbeatHandler handler;

    public SsoHeartbeatController(final SsoHeartbeatHandler handler) {
        this.handler = handler;
    }

    /**
     * 返回默认的sso页面，嵌入js代码，将心跳变量置为true
     */
    @GetMapping
    public void heartbeat(HttpServletRequest request, HttpServletResponse response) {
        handler.doHeartbeat(request, response);
    }
}
