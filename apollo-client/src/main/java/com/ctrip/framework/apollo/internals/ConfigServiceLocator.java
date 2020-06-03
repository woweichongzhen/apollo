package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.ServiceNameConsts;
import com.ctrip.framework.apollo.core.dto.ServiceDTO;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.ctrip.framework.apollo.util.http.HttpRequest;
import com.ctrip.framework.apollo.util.http.HttpResponse;
import com.ctrip.framework.apollo.util.http.HttpUtil;
import com.ctrip.framework.foundation.Foundation;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 配置服务定位器
 */
public class ConfigServiceLocator {

    private static final Logger logger = LoggerFactory.getLogger(ConfigServiceLocator.class);

    /**
     * http工具
     */
    private final HttpUtil httpUtil;

    /**
     * 配置工具
     */
    private final ConfigUtil configUtil;

    /**
     * 配置服务地址的引用
     */
    private final AtomicReference<List<ServiceDTO>> configServices;

    /**
     * http请求返回序列化类型
     */
    private final Type responseType;

    /**
     * 定时拉取配置服务地址
     */
    private final ScheduledExecutorService executorService;

    /**
     * map拼接，键值对之间用&连接，键值对用=连接
     */
    private static final Joiner.MapJoiner MAP_JOINER = Joiner.on("&").withKeyValueSeparator("=");

    private static final Escaper queryParamEscaper = UrlEscapers.urlFormParameterEscaper();

    public ConfigServiceLocator() {
        List<ServiceDTO> initial = Lists.newArrayList();
        configServices = new AtomicReference<>(initial);
        responseType = new TypeToken<List<ServiceDTO>>() {
        }.getType();
        httpUtil = ApolloInjector.getInstance(HttpUtil.class);
        configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        executorService = Executors.newScheduledThreadPool(
                1, ApolloThreadFactory.create("ConfigServiceLocator", true));

        // 初始化配置服务
        initConfigServices();
    }

    /**
     * 初始化配置服务
     */
    private void initConfigServices() {
        // 获取自定义配置服务地址，缓存起来
        List<ServiceDTO> customizedConfigServices = getCustomizedConfigService();

        if (customizedConfigServices != null) {
            setConfigServices(customizedConfigServices);
            return;
        }

        // 尝试更新配置服务
        this.tryUpdateConfigServices();

        // 启动定时刷新任务
        this.schedulePeriodicRefresh();
    }

    /**
     * 获取自定义配置服务
     *
     * @return 服务dto
     */
    private List<ServiceDTO> getCustomizedConfigService() {
        // 从JVM参数中获取配置服务地址
        String configServices = System.getProperty("apollo.configService");
        if (Strings.isNullOrEmpty(configServices)) {
            // 从环境变量中获取配置服务地址
            configServices = System.getenv("APOLLO_CONFIGSERVICE");
        }
        if (Strings.isNullOrEmpty(configServices)) {
            // 从属性文件中获取
            configServices = Foundation.server().getProperty("apollo.configService", null);
        }

        if (Strings.isNullOrEmpty(configServices)) {
            return null;
        }

        logger.warn("Located config services from apollo.configService configuration: {}, will not refresh config " +
                "services from remote meta service!", configServices);

        // 分割出dto url List
        String[] configServiceUrls = configServices.split(",");
        List<ServiceDTO> serviceDTOS = Lists.newArrayList();

        for (String configServiceUrl : configServiceUrls) {
            configServiceUrl = configServiceUrl.trim();
            ServiceDTO serviceDTO = new ServiceDTO();
            serviceDTO.setHomepageUrl(configServiceUrl);
            serviceDTO.setAppName(ServiceNameConsts.APOLLO_CONFIGSERVICE);
            serviceDTO.setInstanceId(configServiceUrl);
            serviceDTOS.add(serviceDTO);
        }

        return serviceDTOS;
    }

    /**
     * 获取配置服务地址
     *
     * @return 配置服务dto
     */
    public List<ServiceDTO> getConfigServices() {
        if (configServices.get().isEmpty()) {
            updateConfigServices();
        }

        return configServices.get();
    }

    /**
     * 尝试更新配置服务
     *
     * @return true更新成功，false更新失败
     */
    private boolean tryUpdateConfigServices() {
        try {
            updateConfigServices();
            return true;
        } catch (Throwable ex) {
            //ignore
        }
        return false;
    }

    /**
     * 定时刷新配置服务
     */
    private void schedulePeriodicRefresh() {
        this.executorService.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        logger.debug("refresh config services");
                        Tracer.logEvent("Apollo.MetaService", "periodicRefresh");
                        tryUpdateConfigServices();
                    }
                }, configUtil.getRefreshInterval(),
                configUtil.getRefreshInterval(),
                configUtil.getRefreshIntervalTimeUnit());
    }

    /**
     * 同步更新配置服务地址
     */
    private synchronized void updateConfigServices() {
        // 拼接元数据服务地址
        String url = assembleMetaServiceUrl();

        HttpRequest request = new HttpRequest(url);
        int maxRetries = 2;
        Throwable exception = null;

        for (int i = 0; i < maxRetries; i++) {
            Transaction transaction = Tracer.newTransaction("Apollo.MetaService", "getConfigService");
            transaction.addData("Url", url);
            try {
                // 执行get请求
                HttpResponse<List<ServiceDTO>> response = httpUtil.doGet(request, responseType);
                transaction.setStatus(Transaction.SUCCESS);
                List<ServiceDTO> services = response.getBody();
                if (services == null || services.isEmpty()) {
                    logConfigService("Empty response!");
                    continue;
                }

                // 缓存配置服务地址
                setConfigServices(services);
                return;
            } catch (Throwable ex) {
                Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
                transaction.setStatus(ex);
                exception = ex;
            } finally {
                transaction.complete();
            }

            // 获取不到报错睡眠1S，继续重试
            try {
                configUtil.getOnErrorRetryIntervalTimeUnit().sleep(configUtil.getOnErrorRetryInterval());
            } catch (InterruptedException ex) {
                //ignore
            }
        }

        throw new ApolloConfigException(String.format("Get config services failed from %s", url), exception);
    }

    /**
     * 缓存配置服务
     *
     * @param services 配置服务dto
     */
    private void setConfigServices(List<ServiceDTO> services) {
        configServices.set(services);
        logConfigServices(services);
    }

    /**
     * 组装配置服务地址
     *
     * @return 配置服务地址
     */
    private String assembleMetaServiceUrl() {
        // 元数据服务域名
        String domainName = configUtil.getMetaServerDomainName();
        String appId = configUtil.getAppId();
        String localIp = configUtil.getLocalIp();

        Map<String, String> queryParams = Maps.newHashMap();
        queryParams.put("appId", queryParamEscaper.escape(appId));
        if (!Strings.isNullOrEmpty(localIp)) {
            queryParams.put("ip", queryParamEscaper.escape(localIp));
        }

        return domainName + "/services/config?" + MAP_JOINER.join(queryParams);
    }

    private void logConfigServices(List<ServiceDTO> serviceDtos) {
        for (ServiceDTO serviceDto : serviceDtos) {
            logConfigService(serviceDto.getHomepageUrl());
        }
    }

    private void logConfigService(String serviceUrl) {
        Tracer.logEvent("Apollo.Config.Services", serviceUrl);
    }
}
