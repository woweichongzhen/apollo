package com.ctrip.framework.apollo.portal.listener;

import com.ctrip.framework.apollo.common.dto.AppDTO;
import com.ctrip.framework.apollo.common.dto.AppNamespaceDTO;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.portal.api.AdminServiceAPI;
import com.ctrip.framework.apollo.portal.component.PortalSettings;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.tracer.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 对象创建监听器
 * <p>
 * 目前监听 {@link AppCreationEvent} 和 {@link AppNamespaceCreationEvent} 事件
 */
@Component
public class CreationListener {

    private static Logger logger = LoggerFactory.getLogger(CreationListener.class);

    /**
     * 前端设置
     */
    private final PortalSettings portalSettings;

    /**
     * 应用api
     */
    private final AdminServiceAPI.AppAPI appAPI;

    private final AdminServiceAPI.NamespaceAPI namespaceAPI;

    public CreationListener(
            final PortalSettings portalSettings,
            final AdminServiceAPI.AppAPI appAPI,
            final AdminServiceAPI.NamespaceAPI namespaceAPI) {
        this.portalSettings = portalSettings;
        this.appAPI = appAPI;
        this.namespaceAPI = namespaceAPI;
    }

    /**
     * 监听应用创建事件，让不同环境都创建应用
     *
     * @param event 应用创建事件
     */
    @EventListener
    public void onAppCreationEvent(AppCreationEvent event) {
        // 转换对象
        AppDTO appDTO = BeanUtils.transform(AppDTO.class, event.getApp());
        // 获取激活的环境
        List<Env> envs = portalSettings.getActiveEnvs();
        // 每个环境都创建应用对象
        for (Env env : envs) {
            try {
                appAPI.createApp(env, appDTO);
            } catch (Throwable e) {
                logger.error("Create app failed. appId = {}, env = {})", appDTO.getAppId(), env, e);
                Tracer.logError(String.format("Create app failed. appId = %s, env = %s", appDTO.getAppId(), env), e);
            }
        }
    }

    /**
     * 监听应用命名空间创建时间，让不同环境都创建应用命名空间
     *
     * @param event 应用命名空间创建事件
     */
    @EventListener
    public void onAppNamespaceCreationEvent(AppNamespaceCreationEvent event) {
        // 转换成dto对象
        AppNamespaceDTO appNamespace = BeanUtils.transform(AppNamespaceDTO.class, event.getAppNamespace());
        // 获取激活的环境，每个环境都创建应用命名空间
        List<Env> envs = portalSettings.getActiveEnvs();
        for (Env env : envs) {
            try {
                namespaceAPI.createAppNamespace(env, appNamespace);
            } catch (Throwable e) {
                logger.error("Create appNamespace failed. appId = {}, env = {}", appNamespace.getAppId(), env, e);
                Tracer.logError(String.format("Create appNamespace failed. appId = %s, env = %s",
                        appNamespace.getAppId(), env), e);
            }
        }
    }

}
