package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.common.entity.App;
import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.portal.repository.AppNamespaceRepository;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * portal应用命名空间服务
 */
@Service
public class AppNamespaceService {

    /**
     * 私有命名空间通知数量
     */
    private static final int PRIVATE_APP_NAMESPACE_NOTIFICATION_COUNT = 5;
    private static final Joiner APP_NAMESPACE_JOINER = Joiner.on(",").skipNulls();

    private final UserInfoHolder userInfoHolder;
    private final AppNamespaceRepository appNamespaceRepository;
    private final RoleInitializationService roleInitializationService;
    private final AppService appService;
    private final RolePermissionService rolePermissionService;

    public AppNamespaceService(
            final UserInfoHolder userInfoHolder,
            final AppNamespaceRepository appNamespaceRepository,
            final RoleInitializationService roleInitializationService,
            final @Lazy AppService appService,
            final RolePermissionService rolePermissionService) {
        this.userInfoHolder = userInfoHolder;
        this.appNamespaceRepository = appNamespaceRepository;
        this.roleInitializationService = roleInitializationService;
        this.appService = appService;
        this.rolePermissionService = rolePermissionService;
    }

    /**
     * 公共的app ns,能被其它项目关联到的app ns
     */
    public List<AppNamespace> findPublicAppNamespaces() {
        return appNamespaceRepository.findByIsPublicTrue();
    }

    /**
     * 查找公共命名空间，
     *
     * @param namespaceName 命名空间名称
     * @return 应用命名空间
     */
    public AppNamespace findPublicAppNamespace(String namespaceName) {
        List<AppNamespace> appNamespaces = appNamespaceRepository.findByNameAndIsPublic(namespaceName, true);

        if (CollectionUtils.isEmpty(appNamespaces)) {
            return null;
        }

        return appNamespaces.get(0);
    }

    /**
     * 查找所有私有命名空间
     *
     * @param namespaceName 名称
     * @return 私有命名空间集合
     */
    private List<AppNamespace> findAllPrivateAppNamespaces(String namespaceName) {
        return appNamespaceRepository.findByNameAndIsPublic(namespaceName, false);
    }

    public AppNamespace findByAppIdAndName(String appId, String namespaceName) {
        return appNamespaceRepository.findByAppIdAndName(appId, namespaceName);
    }

    public List<AppNamespace> findByAppId(String appId) {
        return appNamespaceRepository.findByAppId(appId);
    }

    /**
     * 创建默认的应用命名空间
     *
     * @param appId 应用编号
     */
    @Transactional
    public void createDefaultAppNamespace(String appId) {
        // 校验默认命名空间唯一性
        if (!isAppNamespaceNameUnique(appId, ConfigConsts.NAMESPACE_APPLICATION)) {
            throw new BadRequestException(String.format("App already has application namespace. AppId = %s", appId));
        }

        // 保存默认命名空间
        AppNamespace appNs = new AppNamespace();
        appNs.setAppId(appId);
        appNs.setName(ConfigConsts.NAMESPACE_APPLICATION);
        appNs.setComment("default app namespace");
        appNs.setFormat(ConfigFileFormat.Properties.getValue());
        String userId = userInfoHolder.getUser().getUserId();
        appNs.setDataChangeCreatedBy(userId);
        appNs.setDataChangeLastModifiedBy(userId);

        appNamespaceRepository.save(appNs);
    }

    /**
     * 校验命名空间唯一性
     *
     * @param appId         应用编号
     * @param namespaceName 名称
     * @return true唯一，false不唯一
     */
    public boolean isAppNamespaceNameUnique(String appId, String namespaceName) {
        Objects.requireNonNull(appId, "AppId must not be null");
        Objects.requireNonNull(namespaceName, "Namespace must not be null");
        return Objects.isNull(appNamespaceRepository.findByAppIdAndName(appId, namespaceName));
    }

    public AppNamespace createAppNamespaceInLocal(AppNamespace appNamespace) {
        return createAppNamespaceInLocal(appNamespace, true);
    }

    /**
     * 创建本地应用命名空间
     *
     * @param appNamespace          应用命名空间
     * @param appendNamespacePrefix 是否附加命名空间前缀，即部门id
     * @return 创建后的应用命名空间
     */
    @Transactional
    public AppNamespace createAppNamespaceInLocal(AppNamespace appNamespace, boolean appendNamespacePrefix) {
        // 添加部门id作为前缀
        String appId = appNamespace.getAppId();
        App app = appService.load(appId);
        if (app == null) {
            throw new BadRequestException("App not exist. AppId = " + appId);
        }

        StringBuilder appNamespaceName = new StringBuilder();
        appNamespaceName
                // 公开的并且需要附加前缀，则用部门id附加
                .append(appNamespace.isPublic() && appendNamespacePrefix
                        ? app.getOrgId() + "."
                        : "")
                // 应用空间名称
                .append(appNamespace.getName())
                // 属性文件后缀为空
                .append(appNamespace.formatAsEnum() == ConfigFileFormat.Properties ? "" :
                        "." + appNamespace.getFormat());
        appNamespace.setName(appNamespaceName.toString());

        // 备注设置为空字符串
        if (appNamespace.getComment() == null) {
            appNamespace.setComment("");
        }

        // 校验value格式
        if (!ConfigFileFormat.isValidFormat(appNamespace.getFormat())) {
            throw new BadRequestException("Invalid namespace format. format must be properties、json、yaml、yml、xml");
        }

        // 设置创建者和修改者
        String operator = appNamespace.getDataChangeCreatedBy();
        if (StringUtils.isEmpty(operator)) {
            operator = userInfoHolder.getUser().getUserId();
            appNamespace.setDataChangeCreatedBy(operator);
        }
        appNamespace.setDataChangeLastModifiedBy(operator);

        // 公共应用程序名称空间的全局唯一性检查
        if (appNamespace.isPublic()) {
            checkAppNamespaceGlobalUniqueness(appNamespace);
        } else {
            // 检查私有命名空间，如果该应用已有同名的，抛出400异常
            if (appNamespaceRepository.findByAppIdAndName(appNamespace.getAppId(), appNamespace.getName()) != null) {
                throw new BadRequestException("Private AppNamespace " + appNamespace.getName() + " already exists!");
            }
            // 私有名称空间与公共应用程序名称空间不应相同
            checkPublicAppNamespaceGlobalUniqueness(appNamespace);
        }

        // 保存应用命名空间
        AppNamespace createdAppNamespace = appNamespaceRepository.save(appNamespace);

        // 初始化 Namespace 的相关角色和环境角色（修改和发布）
        roleInitializationService.initNamespaceRoles(appNamespace.getAppId(), appNamespace.getName(), operator);
        roleInitializationService.initNamespaceEnvRoles(appNamespace.getAppId(), appNamespace.getName(), operator);

        return createdAppNamespace;
    }

    /**
     * 检查公共命名空间唯一性
     *
     * @param appNamespace 应用命名空间
     */
    private void checkAppNamespaceGlobalUniqueness(AppNamespace appNamespace) {
        // 检查公共命名空间唯一性
        checkPublicAppNamespaceGlobalUniqueness(appNamespace);

        // 获取所有私有命名空间
        List<AppNamespace> privateAppNamespaces = findAllPrivateAppNamespaces(appNamespace.getName());

        // 如果和公共命名空间 同名的私有命名空间已经存在，抛出400异常
        if (!CollectionUtils.isEmpty(privateAppNamespaces)) {
            Set<String> appIds = Sets.newHashSet();
            for (AppNamespace ans : privateAppNamespaces) {
                appIds.add(ans.getAppId());
                if (appIds.size() == PRIVATE_APP_NAMESPACE_NOTIFICATION_COUNT) {
                    break;
                }
            }

            throw new BadRequestException(
                    "Public AppNamespace " + appNamespace.getName() + " already exists as private AppNamespace in " +
                            "appId: "
                            + APP_NAMESPACE_JOINER.join(appIds) + ", etc. Please select another name!");
        }
    }

    /**
     * 检查公共命名空间唯一性
     *
     * @param appNamespace 应用命名空间
     */
    private void checkPublicAppNamespaceGlobalUniqueness(AppNamespace appNamespace) {
        AppNamespace publicAppNamespace = findPublicAppNamespace(appNamespace.getName());
        if (publicAppNamespace != null) {
            throw new BadRequestException("AppNamespace " + appNamespace.getName() + " already exists as public " +
                    "namespace in appId: " + publicAppNamespace.getAppId() + "!");
        }
    }


    @Transactional
    public AppNamespace deleteAppNamespace(String appId, String namespaceName) {
        AppNamespace appNamespace = appNamespaceRepository.findByAppIdAndName(appId, namespaceName);
        if (appNamespace == null) {
            throw new BadRequestException(
                    String.format("AppNamespace not exists. AppId = %s, NamespaceName = %s", appId, namespaceName));
        }

        String operator = userInfoHolder.getUser().getUserId();

        // this operator is passed to com.ctrip.framework.apollo.portal.listener.DeletionListener
        // .onAppNamespaceDeletionEvent
        appNamespace.setDataChangeLastModifiedBy(operator);

        // delete app namespace in portal db
        appNamespaceRepository.delete(appId, namespaceName, operator);

        // delete Permission and Role related data
        rolePermissionService.deleteRolePermissionsByAppIdAndNamespace(appId, namespaceName, operator);

        return appNamespace;
    }

    public void batchDeleteByAppId(String appId, String operator) {
        appNamespaceRepository.batchDeleteByAppId(appId, operator);
    }
}
