package com.ctrip.framework.apollo.openapi.filter;

import com.ctrip.framework.apollo.openapi.util.ConsumerAuditUtil;
import com.ctrip.framework.apollo.openapi.util.ConsumerAuthUtil;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 第三方认证过滤
 * OpenAPI认证过滤
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConsumerAuthenticationFilter implements Filter {

    private final ConsumerAuthUtil consumerAuthUtil;

    private final ConsumerAuditUtil consumerAuditUtil;

    public ConsumerAuthenticationFilter(ConsumerAuthUtil consumerAuthUtil, ConsumerAuditUtil consumerAuditUtil) {
        this.consumerAuthUtil = consumerAuthUtil;
        this.consumerAuditUtil = consumerAuditUtil;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        //nothing
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException,
            ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        // 获取请求头中认证标识
        String token = request.getHeader("Authorization");

        // 获取应用id，获取不到401，未认证成功
        Long consumerId = consumerAuthUtil.getConsumerId(token);
        if (consumerId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        // 存储应用id到request属性中
        consumerAuthUtil.storeConsumerId(request, consumerId);

        // 执行审计
        consumerAuditUtil.audit(request, consumerId);

        chain.doFilter(req, resp);
    }

    @Override
    public void destroy() {
        //nothing
    }
}
