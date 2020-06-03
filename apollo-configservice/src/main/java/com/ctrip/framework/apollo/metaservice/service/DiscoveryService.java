package com.ctrip.framework.apollo.metaservice.service;

import com.ctrip.framework.apollo.core.ServiceNameConsts;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 发现服务
 */
@Service
public class DiscoveryService {

    private final EurekaClient eurekaClient;

    public DiscoveryService(final EurekaClient eurekaClient) {
        this.eurekaClient = eurekaClient;
    }

    /**
     * 获取配置服务实例集合
     *
     * @return 配置服务实例集合
     */
    public List<InstanceInfo> getConfigServiceInstances() {
        // 从eureka拉取配置服务应用
        Application application = eurekaClient.getApplication(ServiceNameConsts.APOLLO_CONFIGSERVICE);

        if (application == null) {
            Tracer.logEvent("Apollo.EurekaDiscovery.NotFound", ServiceNameConsts.APOLLO_CONFIGSERVICE);
        }
        // 实例集合
        return application != null ? application.getInstances() : Collections.emptyList();
    }

    /**
     * 获取元数据服务实例
     *
     * @return 服务实例集合
     */
    public List<InstanceInfo> getMetaServiceInstances() {
        // 获取apollo元数据服务应用
        Application application = eurekaClient.getApplication(ServiceNameConsts.APOLLO_METASERVICE);
        if (application == null) {
            Tracer.logEvent("Apollo.EurekaDiscovery.NotFound", ServiceNameConsts.APOLLO_METASERVICE);
        }
        // 返回对应的实例
        return application != null ? application.getInstances() : Collections.emptyList();
    }

    /**
     * 获取amdin服务实例
     *
     * @return admin服务实例集合
     */
    public List<InstanceInfo> getAdminServiceInstances() {
        Application application = eurekaClient.getApplication(ServiceNameConsts.APOLLO_ADMINSERVICE);
        if (application == null) {
            Tracer.logEvent("Apollo.EurekaDiscovery.NotFound", ServiceNameConsts.APOLLO_ADMINSERVICE);
        }
        return application != null ? application.getInstances() : Collections.emptyList();
    }
}
