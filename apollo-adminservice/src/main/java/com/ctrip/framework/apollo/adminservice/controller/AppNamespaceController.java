package com.ctrip.framework.apollo.adminservice.controller;

import com.ctrip.framework.apollo.biz.entity.Namespace;
import com.ctrip.framework.apollo.biz.service.AppNamespaceService;
import com.ctrip.framework.apollo.biz.service.NamespaceService;
import com.ctrip.framework.apollo.common.dto.AppNamespaceDTO;
import com.ctrip.framework.apollo.common.dto.NamespaceDTO;
import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * adminservice应用命名空间API
 */
@RestController
public class AppNamespaceController {

    private final AppNamespaceService appNamespaceService;
    private final NamespaceService namespaceService;

    public AppNamespaceController(
            final AppNamespaceService appNamespaceService,
            final NamespaceService namespaceService) {
        this.appNamespaceService = appNamespaceService;
        this.namespaceService = namespaceService;
    }

    /**
     * 创建应用命名空间
     *
     * @param appNamespace   应用命名空间dto
     * @param silentCreation 是否静默创建，静默在所有集群上创建命名空间，而不创建应用命名空间
     * @return 创建后的命名空间dto
     */
    @PostMapping("/apps/{appId}/appnamespaces")
    public AppNamespaceDTO create(@RequestBody AppNamespaceDTO appNamespace,
                                  @RequestParam(defaultValue = "false") boolean silentCreation) {
        // 转换dto为实体
        AppNamespace entity = BeanUtils.transform(AppNamespace.class, appNamespace);
        // 校验是否存在
        AppNamespace managedEntity = appNamespaceService.findOne(entity.getAppId(), entity.getName());

        if (managedEntity == null) {
            // 不存在创建
            if (StringUtils.isEmpty(entity.getFormat())) {
                entity.setFormat(ConfigFileFormat.Properties.getValue());
            }
            entity = appNamespaceService.createAppNamespace(entity);
        } else if (silentCreation) {
            // 静默创建，在所有集群上创建
            appNamespaceService.createNamespaceForAppNamespaceInAllCluster(appNamespace.getAppId(),
                    appNamespace.getName(),
                    appNamespace.getDataChangeCreatedBy());

            entity = managedEntity;
        } else {
            // 存在，400异常
            throw new BadRequestException("app namespaces already exist.");
        }

        return BeanUtils.transform(AppNamespaceDTO.class, entity);
    }

    @DeleteMapping("/apps/{appId}/appnamespaces/{namespaceName:.+}")
    public void delete(@PathVariable("appId") String appId, @PathVariable("namespaceName") String namespaceName,
                       @RequestParam String operator) {
        AppNamespace entity = appNamespaceService.findOne(appId, namespaceName);
        if (entity == null) {
            throw new BadRequestException("app namespace not found for appId: " + appId + " namespace: " + namespaceName);
        }
        appNamespaceService.deleteAppNamespace(entity, operator);
    }

    @GetMapping("/appnamespaces/{publicNamespaceName}/namespaces")
    public List<NamespaceDTO> findPublicAppNamespaceAllNamespaces(@PathVariable String publicNamespaceName,
                                                                  Pageable pageable) {

        List<Namespace> namespaces = namespaceService.findPublicAppNamespaceAllNamespaces(publicNamespaceName,
                pageable);

        return BeanUtils.batchTransform(NamespaceDTO.class, namespaces);
    }

    @GetMapping("/appnamespaces/{publicNamespaceName}/associated-namespaces/count")
    public int countPublicAppNamespaceAssociatedNamespaces(@PathVariable String publicNamespaceName) {
        return namespaceService.countPublicAppNamespaceAssociatedNamespaces(publicNamespaceName);
    }

    @GetMapping("/apps/{appId}/appnamespaces")
    public List<AppNamespaceDTO> getAppNamespaces(@PathVariable("appId") String appId) {

        List<AppNamespace> appNamespaces = appNamespaceService.findByAppId(appId);

        return BeanUtils.batchTransform(AppNamespaceDTO.class, appNamespaces);
    }
}
