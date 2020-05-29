package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.common.constants.GsonType;
import com.ctrip.framework.apollo.common.dto.ItemDTO;
import com.ctrip.framework.apollo.common.dto.NamespaceDTO;
import com.ctrip.framework.apollo.common.dto.ReleaseDTO;
import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.portal.api.AdminServiceAPI;
import com.ctrip.framework.apollo.portal.component.PortalSettings;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.constant.RoleType;
import com.ctrip.framework.apollo.portal.constant.TracerEventType;
import com.ctrip.framework.apollo.portal.entity.bo.ItemBO;
import com.ctrip.framework.apollo.portal.entity.bo.NamespaceBO;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.ctrip.framework.apollo.portal.util.RoleUtils;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class NamespaceService {

    private Logger logger = LoggerFactory.getLogger(NamespaceService.class);
    private Gson gson = new Gson();

    private final PortalConfig portalConfig;
    private final PortalSettings portalSettings;
    private final UserInfoHolder userInfoHolder;
    private final AdminServiceAPI.NamespaceAPI namespaceAPI;
    private final ItemService itemService;
    private final ReleaseService releaseService;
    private final AppNamespaceService appNamespaceService;
    private final InstanceService instanceService;
    private final NamespaceBranchService branchService;
    private final RolePermissionService rolePermissionService;

    public NamespaceService(
            final PortalConfig portalConfig,
            final PortalSettings portalSettings,
            final UserInfoHolder userInfoHolder,
            final AdminServiceAPI.NamespaceAPI namespaceAPI,
            final ItemService itemService,
            final ReleaseService releaseService,
            final AppNamespaceService appNamespaceService,
            final InstanceService instanceService,
            final @Lazy NamespaceBranchService branchService,
            final RolePermissionService rolePermissionService) {
        this.portalConfig = portalConfig;
        this.portalSettings = portalSettings;
        this.userInfoHolder = userInfoHolder;
        this.namespaceAPI = namespaceAPI;
        this.itemService = itemService;
        this.releaseService = releaseService;
        this.appNamespaceService = appNamespaceService;
        this.instanceService = instanceService;
        this.branchService = branchService;
        this.rolePermissionService = rolePermissionService;
    }

    /**
     * 创建命名空间
     *
     * @param env       环境
     * @param namespace 命名空间dto
     * @return 创建后的命名空间dto
     */
    public NamespaceDTO createNamespace(Env env, NamespaceDTO namespace) {
        if (StringUtils.isEmpty(namespace.getDataChangeCreatedBy())) {
            namespace.setDataChangeCreatedBy(userInfoHolder.getUser().getUserId());
        }
        namespace.setDataChangeLastModifiedBy(userInfoHolder.getUser().getUserId());
        NamespaceDTO createdNamespace = namespaceAPI.createNamespace(env, namespace);

        Tracer.logEvent(TracerEventType.CREATE_NAMESPACE,
                String.format("%s+%s+%s+%s", namespace.getAppId(), env, namespace.getClusterName(),
                        namespace.getNamespaceName()));
        return createdNamespace;
    }


    @Transactional
    public void deleteNamespace(String appId, Env env, String clusterName, String namespaceName) {

        AppNamespace appNamespace = appNamespaceService.findByAppIdAndName(appId, namespaceName);

        //1. check parent namespace has not instances
        if (namespaceHasInstances(appId, env, clusterName, namespaceName)) {
            throw new BadRequestException(
                    "Can not delete namespace because namespace has active instances");
        }

        //2. check child namespace has not instances
        NamespaceDTO childNamespace = branchService
                .findBranchBaseInfo(appId, env, clusterName, namespaceName);
        if (childNamespace != null &&
                namespaceHasInstances(appId, env, childNamespace.getClusterName(), namespaceName)) {
            throw new BadRequestException(
                    "Can not delete namespace because namespace's branch has active instances");
        }

        //3. check public namespace has not associated namespace
        if (appNamespace != null && appNamespace.isPublic() && publicAppNamespaceHasAssociatedNamespace(
                namespaceName, env)) {
            throw new BadRequestException(
                    "Can not delete public namespace which has associated namespaces");
        }

        String operator = userInfoHolder.getUser().getUserId();

        namespaceAPI.deleteNamespace(env, appId, clusterName, namespaceName, operator);
    }

    public NamespaceDTO loadNamespaceBaseInfo(String appId, Env env, String clusterName,
                                              String namespaceName) {
        NamespaceDTO namespace = namespaceAPI.loadNamespace(appId, env, clusterName, namespaceName);
        if (namespace == null) {
            throw new BadRequestException("namespaces not exist");
        }
        return namespace;
    }

    /**
     * load cluster all namespace info with items
     */
    public List<NamespaceBO> findNamespaceBOs(String appId, Env env, String clusterName) {

        List<NamespaceDTO> namespaces = namespaceAPI.findNamespaceByCluster(appId, env, clusterName);
        if (namespaces == null || namespaces.size() == 0) {
            throw new BadRequestException("namespaces not exist");
        }

        List<NamespaceBO> namespaceBOs = new LinkedList<>();
        for (NamespaceDTO namespace : namespaces) {

            NamespaceBO namespaceBO;
            try {
                namespaceBO = transformNamespace2BO(env, namespace);
                namespaceBOs.add(namespaceBO);
            } catch (Exception e) {
                logger.error("parse namespace error. app id:{}, env:{}, clusterName:{}, namespace:{}",
                        appId, env, clusterName, namespace.getNamespaceName(), e);
                throw e;
            }
        }

        return namespaceBOs;
    }

    public List<NamespaceDTO> findNamespaces(String appId, Env env, String clusterName) {
        return namespaceAPI.findNamespaceByCluster(appId, env, clusterName);
    }

    public List<NamespaceDTO> getPublicAppNamespaceAllNamespaces(Env env, String publicNamespaceName,
                                                                 int page,
                                                                 int size) {
        return namespaceAPI.getPublicAppNamespaceAllNamespaces(env, publicNamespaceName, page, size);
    }

    /**
     * 获取命名空间BO对象，获取不到400
     */
    public NamespaceBO loadNamespaceBO(String appId, Env env, String clusterName,
                                       String namespaceName) {
        NamespaceDTO namespace = namespaceAPI.loadNamespace(appId, env, clusterName, namespaceName);
        if (namespace == null) {
            throw new BadRequestException("namespaces not exist");
        }
        // 转换命名空间为bo，主要包含最新的和最后发布之间的区别
        return transformNamespace2BO(env, namespace);
    }

    public boolean namespaceHasInstances(String appId, Env env, String clusterName,
                                         String namespaceName) {
        return instanceService.getInstanceCountByNamepsace(appId, env, clusterName, namespaceName) > 0;
    }

    public boolean publicAppNamespaceHasAssociatedNamespace(String publicNamespaceName, Env env) {
        return namespaceAPI.countPublicAppNamespaceAssociatedNamespaces(env, publicNamespaceName) > 0;
    }

    public NamespaceBO findPublicNamespaceForAssociatedNamespace(Env env, String appId,
                                                                 String clusterName, String namespaceName) {
        NamespaceDTO namespace =
                namespaceAPI
                        .findPublicNamespaceForAssociatedNamespace(env, appId, clusterName, namespaceName);

        return transformNamespace2BO(env, namespace);
    }

    public Map<String, Map<String, Boolean>> getNamespacesPublishInfo(String appId) {
        Map<String, Map<String, Boolean>> result = Maps.newHashMap();

        Set<Env> envs = portalConfig.publishTipsSupportedEnvs();
        for (Env env : envs) {
            if (portalSettings.isEnvActive(env)) {
                result.put(env.toString(), namespaceAPI.getNamespacePublishInfo(env, appId));
            }
        }

        return result;
    }

    /**
     * 转换dto为bo
     */
    private NamespaceBO transformNamespace2BO(Env env, NamespaceDTO namespace) {
        NamespaceBO namespaceBO = new NamespaceBO();
        // 基本的命名空间dto信息
        namespaceBO.setBaseInfo(namespace);

        String appId = namespace.getAppId();
        String clusterName = namespace.getClusterName();
        String namespaceName = namespace.getNamespaceName();

        // 填充命名空间bo属性
        fillAppNamespaceProperties(namespaceBO);

        // 最新的命名空间发布配置dto
        ReleaseDTO latestRelease;
        Map<String, String> releaseItems = new HashMap<>();
        Map<String, ItemDTO> deletedItemDTOs = new HashMap<>();
        latestRelease = releaseService.loadLatestRelease(appId, env, clusterName, namespaceName);
        if (latestRelease != null) {
            releaseItems = gson.fromJson(latestRelease.getConfigurations(), GsonType.CONFIG);
        }

        // 获取最新的配置项
        List<ItemDTO> items = itemService.findItems(appId, env, clusterName, namespaceName);
        int modifiedItemCnt = 0;
        List<ItemBO> itemBOs = new LinkedList<>();
        namespaceBO.setItems(itemBOs);
        for (ItemDTO itemDTO : items) {
            // 比较最新的和最后发布的版本
            ItemBO itemBO = transformItem2BO(itemDTO, releaseItems);

            // 如果修改过，计数+1
            if (itemBO.isModified()) {
                modifiedItemCnt++;
            }

            itemBOs.add(itemBO);
        }

        //获取删除的项，添加到已删除的dto map集合中
        itemService.findDeletedItems(appId, env, clusterName, namespaceName).forEach(item -> {
            deletedItemDTOs.put(item.getKey(), item);
        });
        // 解析已删除项，变更为bo，增加修改次数
        List<ItemBO> deletedItems = parseDeletedItems(items, releaseItems, deletedItemDTOs);
        itemBOs.addAll(deletedItems);
        modifiedItemCnt += deletedItems.size();

        // 设置命名空间bo的最终修改次数
        namespaceBO.setItemModifiedCnt(modifiedItemCnt);

        return namespaceBO;
    }

    /**
     * 填充命名空间bo属性
     */
    private void fillAppNamespaceProperties(NamespaceBO namespace) {

        NamespaceDTO namespaceDTO = namespace.getBaseInfo();
        //先从当前appId下面找,包含私有的和公共的
        AppNamespace appNamespace =
                appNamespaceService
                        .findByAppIdAndName(namespaceDTO.getAppId(), namespaceDTO.getNamespaceName());
        //再从公共的app namespace里面找
        if (appNamespace == null) {
            appNamespace = appNamespaceService.findPublicAppNamespace(namespaceDTO.getNamespaceName());
        }

        String format;
        boolean isPublic;
        if (appNamespace == null) {
            //dirty data
            format = ConfigFileFormat.Properties.getValue();
            isPublic = true; // set to true, because public namespace allowed to delete by user
        } else {
            format = appNamespace.getFormat();
            isPublic = appNamespace.isPublic();
            namespace.setParentAppId(appNamespace.getAppId());
            namespace.setComment(appNamespace.getComment());
        }
        namespace.setFormat(format);
        namespace.setPublic(isPublic);
    }

    /**
     * 解析已删除的项
     *
     * @param newItems        新项
     * @param releaseItems    已发布项
     * @param deletedItemDTOs 删除的项
     * @return 删除过的bo集合（标记为修改，删除）
     */
    private List<ItemBO> parseDeletedItems(List<ItemDTO> newItems, Map<String, String> releaseItems, Map<String,
            ItemDTO> deletedItemDTOs) {
        Map<String, ItemDTO> newItemMap = BeanUtils.mapByKey("key", newItems);

        List<ItemBO> deletedItems = new LinkedList<>();
        // 遍历已发布项
        for (Map.Entry<String, String> entry : releaseItems.entrySet()) {
            String key = entry.getKey();
            // 如果新项中没有则，说明已删除
            if (newItemMap.get(key) == null) {
                // 设置删除项
                ItemBO deletedItem = new ItemBO();
                deletedItem.setDeleted(true);
                ItemDTO deletedItemDto = deletedItemDTOs.computeIfAbsent(key, k -> new ItemDTO());
                deletedItemDto.setKey(key);
                String oldValue = entry.getValue();
                deletedItem.setItem(deletedItemDto);

                // 设置老值和修改过的标记
                deletedItemDto.setValue(oldValue);
                deletedItem.setModified(true);
                deletedItem.setOldValue(oldValue);
                deletedItem.setNewValue("");
                deletedItems.add(deletedItem);
            }
        }
        return deletedItems;
    }

    /**
     * 转换项dto为bo
     * 包含一个项的新值和老值
     */
    private ItemBO transformItem2BO(ItemDTO itemDTO, Map<String, String> releaseItems) {
        // 新项key
        String key = itemDTO.getKey();

        ItemBO itemBO = new ItemBO();
        itemBO.setItem(itemDTO);

        // 新项值，已发布的值
        String newValue = itemDTO.getValue();
        String oldValue = releaseItems.get(key);
        // 为新配置项并且修改过，标记为变更项
        if (!StringUtils.isEmpty(key)
                && (oldValue == null || !newValue.equals(oldValue))) {
            itemBO.setModified(true);
            itemBO.setOldValue(oldValue == null ? "" : oldValue);
            itemBO.setNewValue(newValue);
        }
        return itemBO;
    }

    /**
     * 把命名空间的权限赋予操作者（修改、发布）
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @param operator      操作者
     */
    public void assignNamespaceRoleToOperator(String appId, String namespaceName, String operator) {
        rolePermissionService.assignRoleToUsers(
                RoleUtils.buildNamespaceRoleName(appId, namespaceName, RoleType.MODIFY_NAMESPACE),
                Sets.newHashSet(operator),
                operator);
        rolePermissionService.assignRoleToUsers(
                RoleUtils.buildNamespaceRoleName(appId, namespaceName, RoleType.RELEASE_NAMESPACE),
                Sets.newHashSet(operator),
                operator);
    }
}
