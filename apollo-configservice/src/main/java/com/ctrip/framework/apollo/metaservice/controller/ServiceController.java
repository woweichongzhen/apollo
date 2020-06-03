package com.ctrip.framework.apollo.metaservice.controller;

import com.ctrip.framework.apollo.core.dto.ServiceDTO;
import com.ctrip.framework.apollo.metaservice.service.DiscoveryService;
import com.netflix.appinfo.InstanceInfo;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * configservice服务地址API
 */
@RestController
@RequestMapping("/services")
public class ServiceController {

    private final DiscoveryService discoveryService;

    /**
     * 转换实例配置为服务dto
     */
    private static Function<InstanceInfo, ServiceDTO> instanceInfoToServiceDTOFunc = instance -> {
        ServiceDTO service = new ServiceDTO();
        service.setAppName(instance.getAppName());
        service.setInstanceId(instance.getInstanceId());
        service.setHomepageUrl(instance.getHomePageUrl());
        return service;
    };

    public ServiceController(final DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * 获取元数据服务列表，该接口无用，因为metaService和configService在一个服务中。
     *
     * @return 元数据服务列表
     */
    @RequestMapping("/meta")
    public List<ServiceDTO> getMetaService() {
        List<InstanceInfo> instances = discoveryService.getMetaServiceInstances();
        return instances.stream()
                .map(instanceInfoToServiceDTOFunc)
                .collect(Collectors.toList());
    }

    /**
     * 获取配置服务列表
     *
     * @param appId    应用编号
     * @param clientIp 客户端ip
     * @return 配置服务列表
     */
    @RequestMapping("/config")
    public List<ServiceDTO> getConfigService(
            @RequestParam(value = "appId", defaultValue = "") String appId,
            @RequestParam(value = "ip", required = false) String clientIp) {
        List<InstanceInfo> instances = discoveryService.getConfigServiceInstances();
        return instances.stream()
                .map(instanceInfoToServiceDTOFunc)
                .collect(Collectors.toList());
    }

    /**
     * 获取admin服务列表
     *
     * @return admin服务列表
     */
    @RequestMapping("/admin")
    public List<ServiceDTO> getAdminService() {
        List<InstanceInfo> instances = discoveryService.getAdminServiceInstances();
        return instances.stream()
                .map(instanceInfoToServiceDTOFunc)
                .collect(Collectors.toList());
    }
}
