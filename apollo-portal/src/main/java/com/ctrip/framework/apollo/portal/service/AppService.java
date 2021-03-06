package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.common.dto.AppDTO;
import com.ctrip.framework.apollo.common.dto.PageDTO;
import com.ctrip.framework.apollo.common.entity.App;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.portal.api.AdminServiceAPI;
import com.ctrip.framework.apollo.portal.constant.TracerEventType;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.vo.EnvClusterInfo;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.repository.AppRepository;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.ctrip.framework.apollo.portal.spi.UserService;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.collect.Lists;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 应用管理业务层
 */
@Service
public class AppService {

    /**
     * 当前用户信息持有类
     */
    private final UserInfoHolder userInfoHolder;
    private final AdminServiceAPI.AppAPI appAPI;
    private final AppRepository appRepository;
    private final ClusterService clusterService;

    /**
     * 应用命名空间服务
     */
    private final AppNamespaceService appNamespaceService;
    private final RoleInitializationService roleInitializationService;
    private final RolePermissionService rolePermissionService;
    private final FavoriteService favoriteService;
    private final UserService userService;

    public AppService(
            final UserInfoHolder userInfoHolder,
            final AdminServiceAPI.AppAPI appAPI,
            final AppRepository appRepository,
            final ClusterService clusterService,
            final AppNamespaceService appNamespaceService,
            final RoleInitializationService roleInitializationService,
            final RolePermissionService rolePermissionService,
            final FavoriteService favoriteService,
            final UserService userService) {
        this.userInfoHolder = userInfoHolder;
        this.appAPI = appAPI;
        this.appRepository = appRepository;
        this.clusterService = clusterService;
        this.appNamespaceService = appNamespaceService;
        this.roleInitializationService = roleInitializationService;
        this.rolePermissionService = rolePermissionService;
        this.favoriteService = favoriteService;
        this.userService = userService;
    }


    public List<App> findAll() {
        Iterable<App> apps = appRepository.findAll();
        if (apps == null) {
            return Collections.emptyList();
        }
        return Lists.newArrayList((apps));
    }

    public PageDTO<App> findAll(Pageable pageable) {
        Page<App> apps = appRepository.findAll(pageable);

        return new PageDTO<>(apps.getContent(), pageable, apps.getTotalElements());
    }

    public PageDTO<App> searchByAppIdOrAppName(String query, Pageable pageable) {
        Page<App> apps = appRepository.findByAppIdContainingOrNameContaining(query, query, pageable);

        return new PageDTO<>(apps.getContent(), pageable, apps.getTotalElements());
    }

    public List<App> findByAppIds(Set<String> appIds) {
        return appRepository.findByAppIdIn(appIds);
    }

    public List<App> findByAppIds(Set<String> appIds, Pageable pageable) {
        return appRepository.findByAppIdIn(appIds, pageable);
    }

    public List<App> findByOwnerName(String ownerName, Pageable page) {
        return appRepository.findByOwnerName(ownerName, page);
    }

    public App load(String appId) {
        return appRepository.findByAppId(appId);
    }

    public AppDTO load(Env env, String appId) {
        return appAPI.loadApp(env, appId);
    }

    public void createAppInRemote(Env env, App app) {
        String username = userInfoHolder.getUser().getUserId();
        app.setDataChangeCreatedBy(username);
        app.setDataChangeLastModifiedBy(username);

        AppDTO appDTO = BeanUtils.transform(AppDTO.class, app);
        appAPI.createApp(env, appDTO);
    }

    /**
     * 创建本地应用信息
     *
     * @param app 应用信息
     * @return 创建好的应用信息
     */
    @Transactional
    public App createAppInLocal(App app) {
        String appId = app.getAppId();
        // appId已存在，抛出400异常
        App managedApp = appRepository.findByAppId(appId);
        if (managedApp != null) {
            throw new BadRequestException(String.format("App already exists. AppId = %s", appId));
        }
        // 拥有者不存在，抛出400异常
        UserInfo owner = userService.findByUserId(app.getOwnerName());
        if (owner == null) {
            throw new BadRequestException("Application's owner not exist.");
        }

        // 填充应用的相关信息
        app.setOwnerEmail(owner.getEmail());
        String operator = userInfoHolder.getUser().getUserId();
        app.setDataChangeCreatedBy(operator);
        app.setDataChangeLastModifiedBy(operator);

        // 保存应用信息
        App createdApp = appRepository.save(app);

        // 创建默认的应用命名空间 application
        appNamespaceService.createDefaultAppNamespace(appId);

        // 初始化应用角色信息（应用拥有，应用管理，不同环境的默认命名空间的修改和发布）
        roleInitializationService.initAppRoles(createdApp);

        Tracer.logEvent(TracerEventType.CREATE_APP, appId);

        return createdApp;
    }

    @Transactional
    public App updateAppInLocal(App app) {
        String appId = app.getAppId();

        App managedApp = appRepository.findByAppId(appId);
        if (managedApp == null) {
            throw new BadRequestException(String.format("App not exists. AppId = %s", appId));
        }

        managedApp.setName(app.getName());
        managedApp.setOrgId(app.getOrgId());
        managedApp.setOrgName(app.getOrgName());

        String ownerName = app.getOwnerName();
        UserInfo owner = userService.findByUserId(ownerName);
        if (owner == null) {
            throw new BadRequestException(String.format("App's owner not exists. owner = %s", ownerName));
        }
        managedApp.setOwnerName(owner.getUserId());
        managedApp.setOwnerEmail(owner.getEmail());

        String operator = userInfoHolder.getUser().getUserId();
        managedApp.setDataChangeLastModifiedBy(operator);

        return appRepository.save(managedApp);
    }

    public EnvClusterInfo createEnvNavNode(Env env, String appId) {
        EnvClusterInfo node = new EnvClusterInfo(env);
        node.setClusters(clusterService.findClusters(env, appId));
        return node;
    }

    @Transactional
    public App deleteAppInLocal(String appId) {
        App managedApp = appRepository.findByAppId(appId);
        if (managedApp == null) {
            throw new BadRequestException(String.format("App not exists. AppId = %s", appId));
        }
        String operator = userInfoHolder.getUser().getUserId();

        //this operator is passed to com.ctrip.framework.apollo.portal.listener.DeletionListener.onAppDeletionEvent
        managedApp.setDataChangeLastModifiedBy(operator);

        //删除portal数据库中的app
        appRepository.deleteApp(appId, operator);

        //删除portal数据库中的appNamespace
        appNamespaceService.batchDeleteByAppId(appId, operator);

        //删除portal数据库中的收藏表
        favoriteService.batchDeleteByAppId(appId, operator);

        //删除portal数据库中Permission、Role相关数据
        rolePermissionService.deleteRolePermissionsByAppId(appId, operator);

        return managedApp;
    }
}
