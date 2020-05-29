package com.ctrip.framework.apollo.portal.controller;

import com.ctrip.framework.apollo.common.dto.AppNamespaceDTO;
import com.ctrip.framework.apollo.common.dto.NamespaceDTO;
import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.http.MultiResponseEntity;
import com.ctrip.framework.apollo.common.http.RichResponseEntity;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.common.utils.InputValidator;
import com.ctrip.framework.apollo.common.utils.RequestPrecondition;
import com.ctrip.framework.apollo.portal.api.AdminServiceAPI;
import com.ctrip.framework.apollo.portal.component.PermissionValidator;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.entity.bo.NamespaceBO;
import com.ctrip.framework.apollo.portal.entity.model.NamespaceCreationModel;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.listener.AppNamespaceCreationEvent;
import com.ctrip.framework.apollo.portal.listener.AppNamespaceDeletionEvent;
import com.ctrip.framework.apollo.portal.service.AppNamespaceService;
import com.ctrip.framework.apollo.portal.service.NamespaceService;
import com.ctrip.framework.apollo.portal.service.RoleInitializationService;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ctrip.framework.apollo.common.utils.RequestPrecondition.checkModel;

/**
 * 管理 AppNamespace 和 Namespace 的 API
 */
@RestController
public class NamespaceController {

    private static final Logger logger = LoggerFactory.getLogger(NamespaceController.class);

    private final ApplicationEventPublisher publisher;
    private final UserInfoHolder userInfoHolder;
    private final NamespaceService namespaceService;
    private final AppNamespaceService appNamespaceService;
    private final RoleInitializationService roleInitializationService;
    private final PortalConfig portalConfig;
    private final PermissionValidator permissionValidator;
    private final AdminServiceAPI.NamespaceAPI namespaceAPI;

    public NamespaceController(
            final ApplicationEventPublisher publisher,
            final UserInfoHolder userInfoHolder,
            final NamespaceService namespaceService,
            final AppNamespaceService appNamespaceService,
            final RoleInitializationService roleInitializationService,
            final PortalConfig portalConfig,
            final PermissionValidator permissionValidator,
            final AdminServiceAPI.NamespaceAPI namespaceAPI) {
        this.publisher = publisher;
        this.userInfoHolder = userInfoHolder;
        this.namespaceService = namespaceService;
        this.appNamespaceService = appNamespaceService;
        this.roleInitializationService = roleInitializationService;
        this.portalConfig = portalConfig;
        this.permissionValidator = permissionValidator;
        this.namespaceAPI = namespaceAPI;
    }

    @GetMapping("/appnamespaces/public")
    public List<AppNamespace> findPublicAppNamespaces() {
        return appNamespaceService.findPublicAppNamespaces();
    }

    @GetMapping("/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces")
    public List<NamespaceBO> findNamespaces(@PathVariable String appId, @PathVariable String env,
                                            @PathVariable String clusterName) {

        List<NamespaceBO> namespaceBOs = namespaceService.findNamespaceBOs(appId, Env.valueOf(env), clusterName);

        for (NamespaceBO namespaceBO : namespaceBOs) {
            if (permissionValidator.shouldHideConfigToCurrentUser(appId, env,
                    namespaceBO.getBaseInfo().getNamespaceName())) {
                namespaceBO.hideItems();
            }
        }

        return namespaceBOs;
    }

    @GetMapping("/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName:.+}")
    public NamespaceBO findNamespace(@PathVariable String appId, @PathVariable String env,
                                     @PathVariable String clusterName, @PathVariable String namespaceName) {

        NamespaceBO namespaceBO = namespaceService.loadNamespaceBO(appId, Env.valueOf(env), clusterName, namespaceName);

        if (namespaceBO != null && permissionValidator.shouldHideConfigToCurrentUser(appId, env, namespaceName)) {
            namespaceBO.hideItems();
        }

        return namespaceBO;
    }

    @GetMapping("/envs/{env}/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/associated-public" +
            "-namespace")
    public NamespaceBO findPublicNamespaceForAssociatedNamespace(@PathVariable String env,
                                                                 @PathVariable String appId,
                                                                 @PathVariable String namespaceName,
                                                                 @PathVariable String clusterName) {

        return namespaceService.findPublicNamespaceForAssociatedNamespace(Env.valueOf(env), appId, clusterName,
                namespaceName);
    }

    /**
     * 创建命名空间
     *
     * @param appId  应用编号
     * @param models 要创建的命名空间集合，每个环境对应一个命名空间
     * @return 返回实体
     */
    @PreAuthorize(value = "@permissionValidator.hasCreateNamespacePermission(#appId)")
    @PostMapping("/apps/{appId}/namespaces")
    public ResponseEntity<Void> createNamespace(@PathVariable String appId,
                                                @RequestBody List<NamespaceCreationModel> models) {
        // 创建命名空间集合不能为空，否则抛出400异常
        checkModel(!CollectionUtils.isEmpty(models));

        // 操作者
        String operator = userInfoHolder.getUser().getUserId();

        // 初始化默认和不同环境的命名空间的角色（修改和发布）
        String namespaceName = models.get(0).getNamespace().getNamespaceName();
        roleInitializationService.initNamespaceRoles(appId, namespaceName, operator);
        roleInitializationService.initNamespaceEnvRoles(appId, namespaceName, operator);

        // 创建不同环境的命名空间，失败仅打印日志
        for (NamespaceCreationModel model : models) {
            NamespaceDTO namespace = model.getNamespace();
            RequestPrecondition.checkArgumentsNotEmpty(
                    model.getEnv(),
                    namespace.getAppId(),
                    namespace.getClusterName(),
                    namespace.getNamespaceName());

            try {
                namespaceService.createNamespace(Env.valueOf(model.getEnv()), namespace);
            } catch (Exception e) {
                logger.error("create namespace fail.", e);
                Tracer.logError(
                        String.format("create namespace fail. (env=%s namespace=%s)", model.getEnv(),
                                namespace.getNamespaceName()), e);
            }
        }

        // 把命名空间的权限赋予操作者（修改、发布）
        namespaceService.assignNamespaceRoleToOperator(appId, namespaceName, userInfoHolder.getUser().getUserId());

        return ResponseEntity.ok().build();
    }

    @PreAuthorize(value = "@permissionValidator.hasDeleteNamespacePermission(#appId)")
    @DeleteMapping("/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName:.+}")
    public ResponseEntity<Void> deleteNamespace(@PathVariable String appId, @PathVariable String env,
                                                @PathVariable String clusterName, @PathVariable String namespaceName) {

        namespaceService.deleteNamespace(appId, Env.valueOf(env), clusterName, namespaceName);

        return ResponseEntity.ok().build();
    }

    @PreAuthorize(value = "@permissionValidator.isSuperAdmin()")
    @DeleteMapping("/apps/{appId}/appnamespaces/{namespaceName:.+}")
    public ResponseEntity<Void> deleteAppNamespace(@PathVariable String appId, @PathVariable String namespaceName) {

        AppNamespace appNamespace = appNamespaceService.deleteAppNamespace(appId, namespaceName);

        publisher.publishEvent(new AppNamespaceDeletionEvent(appNamespace));

        return ResponseEntity.ok().build();
    }

    @GetMapping("/apps/{appId}/appnamespaces/{namespaceName:.+}")
    public AppNamespaceDTO findAppNamespace(@PathVariable String appId, @PathVariable String namespaceName) {
        AppNamespace appNamespace = appNamespaceService.findByAppIdAndName(appId, namespaceName);

        if (appNamespace == null) {
            throw new BadRequestException(
                    String.format("AppNamespace not exists. AppId = %s, NamespaceName = %s", appId, namespaceName));
        }

        return BeanUtils.transform(AppNamespaceDTO.class, appNamespace);
    }

    /**
     * 创建应用命名空间
     *
     * @param appId                 应用编号
     * @param appendNamespacePrefix 是否添加命名空间前缀，即部门id
     * @param appNamespace          应用命名空间
     * @return 创建完成的应用命名空间
     */
    @PreAuthorize(value = "@permissionValidator.hasCreateAppNamespacePermission(#appId, #appNamespace)")
    @PostMapping("/apps/{appId}/appnamespaces")
    public AppNamespace createAppNamespace(@PathVariable String appId,
                                           @RequestParam(defaultValue = "true") boolean appendNamespacePrefix,
                                           @Valid @RequestBody AppNamespace appNamespace) {
        // 校验应用命名空间名称，不通过抛出400异常
        if (!InputValidator.isValidAppNamespace(appNamespace.getName())) {
            throw new BadRequestException(String.format("Invalid Namespace format: %s",
                    InputValidator.INVALID_CLUSTER_NAMESPACE_MESSAGE + " & " + InputValidator.INVALID_NAMESPACE_NAMESPACE_MESSAGE));
        }

        // 创建portal本地应用命名空间
        AppNamespace createdAppNamespace = appNamespaceService.createAppNamespaceInLocal(appNamespace,
                appendNamespacePrefix);

        // 赋予权限，若满足如下任一条件授予命名空间的角色：
        // 私有类型的 AppNamespace ，并且允许 App 管理员创建私有类型的 AppNamespace，通过配置提供
        // 公开类型的 AppNamespace
        if (portalConfig.canAppAdminCreatePrivateNamespace() || createdAppNamespace.isPublic()) {
            namespaceService.assignNamespaceRoleToOperator(appId, appNamespace.getName(),
                    userInfoHolder.getUser().getUserId());
        }

        // 发布应用命名空间创建事件，让adminservice为不同环境创建命名空间
        publisher.publishEvent(new AppNamespaceCreationEvent(createdAppNamespace));

        return createdAppNamespace;
    }

    /**
     * env -> cluster -> cluster has not published namespace?
     * Example:
     * dev ->
     * default -> true   (default cluster has not published namespace)
     * customCluster -> false (customCluster cluster's all namespaces had published)
     */
    @GetMapping("/apps/{appId}/namespaces/publish_info")
    public Map<String, Map<String, Boolean>> getNamespacesPublishInfo(@PathVariable String appId) {
        return namespaceService.getNamespacesPublishInfo(appId);
    }

    @GetMapping("/envs/{env}/appnamespaces/{publicNamespaceName}/namespaces")
    public List<NamespaceDTO> getPublicAppNamespaceAllNamespaces(@PathVariable String env,
                                                                 @PathVariable String publicNamespaceName,
                                                                 @RequestParam(name = "page", defaultValue = "0") int page,
                                                                 @RequestParam(name = "size", defaultValue = "10") int size) {

        return namespaceService.getPublicAppNamespaceAllNamespaces(Env.fromString(env), publicNamespaceName, page,
                size);

    }

    @GetMapping("/apps/{appId}/envs/{env}/clusters/{clusterName}/missing-namespaces")
    public MultiResponseEntity<String> findMissingNamespaces(@PathVariable String appId, @PathVariable String env,
                                                             @PathVariable String clusterName) {

        MultiResponseEntity<String> response = MultiResponseEntity.ok();

        Set<String> missingNamespaces = findMissingNamespaceNames(appId, env, clusterName);

        for (String missingNamespace : missingNamespaces) {
            response.addResponseEntity(RichResponseEntity.ok(missingNamespace));
        }

        return response;
    }

    @PostMapping("/apps/{appId}/envs/{env}/clusters/{clusterName}/missing-namespaces")
    public ResponseEntity<Void> createMissingNamespaces(@PathVariable String appId, @PathVariable String env,
                                                        @PathVariable String clusterName) {

        Set<String> missingNamespaces = findMissingNamespaceNames(appId, env, clusterName);

        for (String missingNamespace : missingNamespaces) {
            namespaceAPI.createMissingAppNamespace(Env.fromString(env), findAppNamespace(appId, missingNamespace));
        }

        return ResponseEntity.ok().build();
    }

    private Set<String> findMissingNamespaceNames(String appId, String env, String clusterName) {
        List<AppNamespaceDTO> configDbAppNamespaces = namespaceAPI.getAppNamespaces(appId, Env.fromString(env));
        List<NamespaceDTO> configDbNamespaces = namespaceService.findNamespaces(appId, Env.fromString(env),
                clusterName);
        List<AppNamespace> portalDbAppNamespaces = appNamespaceService.findByAppId(appId);

        Set<String> configDbAppNamespaceNames = configDbAppNamespaces.stream().map(AppNamespaceDTO::getName)
                .collect(Collectors.toSet());
        Set<String> configDbNamespaceNames = configDbNamespaces.stream().map(NamespaceDTO::getNamespaceName)
                .collect(Collectors.toSet());

        Set<String> portalDbAllAppNamespaceNames = Sets.newHashSet();
        Set<String> portalDbPrivateAppNamespaceNames = Sets.newHashSet();

        for (AppNamespace appNamespace : portalDbAppNamespaces) {
            portalDbAllAppNamespaceNames.add(appNamespace.getName());
            if (!appNamespace.isPublic()) {
                portalDbPrivateAppNamespaceNames.add(appNamespace.getName());
            }
        }

        // AppNamespaces should be the same
        Set<String> missingAppNamespaceNames = Sets.difference(portalDbAllAppNamespaceNames, configDbAppNamespaceNames);
        // Private namespaces should all exist
        Set<String> missingNamespaceNames = Sets.difference(portalDbPrivateAppNamespaceNames, configDbNamespaceNames);

        return Sets.union(missingAppNamespaceNames, missingNamespaceNames);
    }
}
