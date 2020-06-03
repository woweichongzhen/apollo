package com.ctrip.framework.apollo.portal.component;

import com.ctrip.framework.apollo.core.dto.ServiceDTO;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.environment.PortalMetaDomainService;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * adminService地址定位器
 */
@Component
public class AdminServiceAddressLocator {

    /**
     * 刷新正常定时任务的周期，5分钟一次
     */
    private static final long NORMAL_REFRESH_INTERVAL = 5 * 60 * 1000;

    /**
     * 刷新地址失败提交定时任务的周期
     */
    private static final long OFFLINE_REFRESH_INTERVAL = 10 * 1000;

    /**
     * 刷新admin服务地址重试次数
     */
    private static final int RETRY_TIMES = 3;

    /**
     * admin服务dtouri
     */
    private static final String ADMIN_SERVICE_URL_PATH = "/services/admin";

    private static final Logger logger = LoggerFactory.getLogger(AdminServiceAddressLocator.class);

    /**
     * 刷新admin服务地址的线程池
     */
    private ScheduledExecutorService refreshServiceAddressService;

    /**
     * rest模板
     */
    private RestTemplate restTemplate;

    /**
     * 所有环境
     */
    private List<Env> allEnvs;

    /**
     * 环境的服务dto缓存
     * key：环境
     * value：服务dto集合
     */
    private final Map<Env, List<ServiceDTO>> cache = new ConcurrentHashMap<>();

    private final PortalSettings portalSettings;

    private final RestTemplateFactory restTemplateFactory;

    private final PortalMetaDomainService portalMetaDomainService;

    public AdminServiceAddressLocator(
            final HttpMessageConverters httpMessageConverters,
            final PortalSettings portalSettings,
            final RestTemplateFactory restTemplateFactory,
            final PortalMetaDomainService portalMetaDomainService
    ) {
        this.portalSettings = portalSettings;
        this.restTemplateFactory = restTemplateFactory;
        this.portalMetaDomainService = portalMetaDomainService;
    }

    @PostConstruct
    public void init() {
        // 获取所有环境地址
        allEnvs = portalSettings.getAllEnvs();

        // 初始化rest模板
        restTemplate = restTemplateFactory.getObject();

        // 执行刷新服务地址的定时任务，延迟1毫秒执行
        refreshServiceAddressService = Executors.newScheduledThreadPool(
                1, ApolloThreadFactory.create("ServiceLocator", true));
        refreshServiceAddressService.schedule(new RefreshAdminServerAddressTask(), 1, TimeUnit.MILLISECONDS);
    }

    /**
     * 获取指定环境的admin服务地址
     *
     * @param env 环境
     * @return 服务dto集合
     */
    public List<ServiceDTO> getServiceList(Env env) {
        List<ServiceDTO> services = cache.get(env);
        if (CollectionUtils.isEmpty(services)) {
            return Collections.emptyList();
        }
        List<ServiceDTO> randomConfigServices = Lists.newArrayList(services);
        // 随机打乱
        Collections.shuffle(randomConfigServices);
        return randomConfigServices;
    }

    /**
     * 刷新admin服务地址定时任务
     */
    private class RefreshAdminServerAddressTask implements Runnable {

        @Override
        public void run() {
            boolean refreshSuccess = true;
            //refresh fail if get any env address fail
            // 如果获取一个环境地址失败，则刷新失败
            for (Env env : allEnvs) {
                boolean currentEnvRefreshResult = refreshServerAddressCache(env);
                refreshSuccess = refreshSuccess && currentEnvRefreshResult;
            }

            if (refreshSuccess) {
                // 刷新成功继续提交定时任务，5分钟一次
                refreshServiceAddressService.schedule(
                        new RefreshAdminServerAddressTask(),
                        NORMAL_REFRESH_INTERVAL,
                        TimeUnit.MILLISECONDS);
            } else {
                // 刷新失败提交定时任务的周期，10秒一次
                refreshServiceAddressService.schedule(
                        new RefreshAdminServerAddressTask(),
                        OFFLINE_REFRESH_INTERVAL,
                        TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * 刷新服务地址缓存
     *
     * @param env 环境
     * @return true刷新成功，false刷新失败
     */
    private boolean refreshServerAddressCache(Env env) {
        for (int i = 0; i < RETRY_TIMES; i++) {

            try {
                // 获取admin服务dto集合，并放到缓存中
                ServiceDTO[] services = getAdminServerAddress(env);
                if (services == null || services.length == 0) {
                    continue;
                }
                cache.put(env, Arrays.asList(services));
                return true;
            } catch (Throwable e) {
                logger.error(String.format("Get admin server address from meta server failed. env: %s, meta server " +
                                "address:%s",
                        env, portalMetaDomainService.getDomain(env)), e);
                Tracer
                        .logError(String.format("Get admin server address from meta server failed. env: %s, meta " +
                                        "server address:%s",
                                env, portalMetaDomainService.getDomain(env)), e);
            }
        }
        return false;
    }

    /**
     * 获取admin服务地址
     *
     * @param env 环境
     * @return 服务dto
     */
    private ServiceDTO[] getAdminServerAddress(Env env) {
        String domainName = portalMetaDomainService.getDomain(env);
        String url = domainName + ADMIN_SERVICE_URL_PATH;
        return restTemplate.getForObject(url, ServiceDTO[].class);
    }


}
