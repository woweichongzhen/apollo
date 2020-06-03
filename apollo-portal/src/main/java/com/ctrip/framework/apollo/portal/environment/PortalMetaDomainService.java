package com.ctrip.framework.apollo.portal.environment;

import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.core.utils.NetUtil;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * portal元数据域名服务
 * 提供可用的元数据服务url，如果没有可用的，使用默认的元数据服务url
 * <p>
 * Only use in apollo-portal
 * Provider an available meta server url.
 * If there is no available meta server url for the given environment,
 * the default meta server url will be used(http://apollo.meta).
 *
 * @author wxq
 * @see com.ctrip.framework.apollo.core.MetaDomainConsts
 */
@Service
public class PortalMetaDomainService {

    private static final Logger logger = LoggerFactory.getLogger(PortalMetaDomainService.class);

    /**
     * 刷新周期，60S
     */
    private static final long REFRESH_INTERVAL_IN_SECOND = 60;// 1 min

    /**
     * 默认的元数据服务地址
     */
    static final String DEFAULT_META_URL = "http://apollo.meta";

    /**
     * 元数据服务地址缓存
     * key：环境
     * value：元数据服务地址
     */
    private final Map<Env, String> metaServerAddressCache = Maps.newConcurrentMap();

    /**
     * 初始化元数据服务提供者
     * 多个 {@link PortalMetaServerProvider}
     */
    private final List<PortalMetaServerProvider> portalMetaServerProviders = new ArrayList<>();

    /**
     * 随机选中的一个元数据服务地址缓存
     * key：逗号分隔的元数据服务地址
     * value：选择的单个元数据服务地址
     */
    private final Map<String, String> selectedMetaServerAddressCache = Maps.newConcurrentMap();

    /**
     * 周期刷新任务是否开始
     */
    private final AtomicBoolean periodicRefreshStarted = new AtomicBoolean(false);

    PortalMetaDomainService(final PortalConfig portalConfig) {
        // 数据库元数据服务提供，高优先级
        portalMetaServerProviders.add(new DatabasePortalMetaServerProvider(portalConfig));

        // 系统属性，OS环境，配置文件，低优先级
        portalMetaServerProviders.add(new DefaultPortalMetaServerProvider());
    }

    /**
     * 返回指定环境的域名，如果存在多个，挑选一个
     */
    public String getDomain(Env env) {
        // 获取元数据服务地址
        String metaServerAddress = getMetaServerAddress(env);

        // 如果存在多个，挑选一个
        if (metaServerAddress.contains(",")) {
            return selectMetaServerAddress(metaServerAddress);
        }
        return metaServerAddress;
    }

    /**
     * 返回指定环境的元数据服务地址
     * 如果存在多个，返回带逗号的字符串
     * <p>
     * Return meta server address. If multiple meta server addresses are configured, will return the comma separated
     * string.
     */
    public String getMetaServerAddress(Env env) {
        // 如果不在缓存中，执行获取流程，并放到缓存中
        if (!metaServerAddressCache.containsKey(env)) {
            metaServerAddressCache.put(env, getMetaServerAddressCacheValue(portalMetaServerProviders, env));
        }

        // 从缓存中获取
        return metaServerAddressCache.get(env);
    }

    /**
     * 从元数据供者中获取指定环境的元数据服务，用于缓存
     * 如果给定的不存在，使用默认的 apollo.meta
     * <p>
     * Get the meta server from provider by given environment.
     * If there is no available meta server url for the given environment,
     * the default meta server url will be used(http://apollo.meta).
     *
     * @param providers provide environment's meta server address
     *                  元数据地址提供者
     * @param env       environment
     *                  环境
     * @return meta server address 元数据服务地址
     */
    private String getMetaServerAddressCacheValue(Collection<PortalMetaServerProvider> providers, Env env) {
        String metaAddress = null;

        for (PortalMetaServerProvider portalMetaServerProvider : providers) {
            // 按顺序从元数据服务地址提供者中获取，如果存在，获取
            if (portalMetaServerProvider.exists(env)) {
                metaAddress = portalMetaServerProvider.getMetaServerAddress(env);
                logger.info("Located meta server address [{}] for env [{}]", metaAddress, env);
                break;
            }
        }

        // 如果获取到的为空，使用默认的
        if (Strings.isNullOrEmpty(metaAddress)) {
            // Fallback to default meta address
            metaAddress = DEFAULT_META_URL;
            logger.warn("Meta server address fallback to [{}] for env [{}]," +
                            " because it is not available in MetaServerProvider",
                    metaAddress, env);
        }
        return metaAddress.trim();
    }

    /**
     * 从元数据服务提供者中重载元数据服务地址，清理元数据地址缓存
     * <p>
     * reload all {@link PortalMetaServerProvider}.
     * clear cache {@link this#metaServerAddressCache}
     */
    public void reload() {
        for (PortalMetaServerProvider portalMetaServerProvider : portalMetaServerProviders) {
            portalMetaServerProvider.reload();
        }
        metaServerAddressCache.clear();
    }

    /**
     * 从多个种随机挑选一个
     * <p>
     * Select one available meta server from the comma separated meta server addresses, e.g.
     * http://1.2.3.4:8080,http://2.3.4.5:8080
     * <p>
     * <br />
     * <p>
     * In production environment, we still suggest using one single domain like http://config.xxx.com(backed by software
     * load balancers like nginx) instead of multiple ip addresses
     */
    private String selectMetaServerAddress(String metaServerAddresses) {
        // 获取上一次随机选中的
        String metaAddressSelected = selectedMetaServerAddressCache.get(metaServerAddresses);

        // 如果上一次选中的为空，
        if (metaAddressSelected == null) {
            // 将执行定时任务的变量更改为true，执行定时任务（初始化的执行）
            if (periodicRefreshStarted.compareAndSet(false, true)) {
                schedulePeriodicRefresh();
            }

            // 更新元数据服务地址缓存，然后获取返回
            updateMetaServerAddresses(metaServerAddresses);
            metaAddressSelected = selectedMetaServerAddressCache.get(metaServerAddresses);
        }

        return metaAddressSelected;
    }

    /**
     * 更新元数据服务地址缓存
     *
     * @param metaServerAddresses 完整的元数据服务地址，包含逗号
     */
    private void updateMetaServerAddresses(String metaServerAddresses) {
        logger.debug("Selecting meta server address for: {}", metaServerAddresses);

        Transaction transaction = Tracer.newTransaction("Apollo.MetaService", "refreshMetaServerAddress");
        transaction.addData("Url", metaServerAddresses);

        try {
            List<String> metaServers = Lists.newArrayList(metaServerAddresses.split(","));
            // 打乱元数据服务地址
            Collections.shuffle(metaServers);

            // 按打乱顺序挑选一个，然后判断配置服务地址是否可用，如果可用，将该地址放到缓存中
            boolean serverAvailable = false;
            for (String address : metaServers) {
                address = address.trim();
                //check whether /services/config is accessible
                if (NetUtil.pingUrl(address + "/services/config")) {
                    // select the first available meta server
                    selectedMetaServerAddressCache.put(metaServerAddresses, address);
                    serverAvailable = true;
                    logger.debug("Selected meta server address {} for {}", address, metaServerAddresses);
                    break;
                }
            }

            // 如果缓存中都不包含，说明没有一个可以访问通，从元数据服务地址中取第一个
            if (!selectedMetaServerAddressCache.containsKey(metaServerAddresses)) {
                selectedMetaServerAddressCache.put(metaServerAddresses, metaServers.get(0).trim());
            }

            if (!serverAvailable) {
                logger.warn("Could not find available meta server for configured meta server addresses: {}, fallback " +
                                "to: {}",
                        metaServerAddresses, selectedMetaServerAddressCache.get(metaServerAddresses));
            }

            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            transaction.setStatus(ex);
            throw ex;
        } finally {
            transaction.complete();
        }
    }

    /**
     * 定时执行刷新任务
     */
    private void schedulePeriodicRefresh() {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(
                1, ApolloThreadFactory.create("MetaServiceLocator", true));

        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                // 获取所有带逗号的元数据服务地址，遍历更新，60S执行一次
                for (String metaServerAddresses : selectedMetaServerAddressCache.keySet()) {
                    updateMetaServerAddresses(metaServerAddresses);
                }
            } catch (Throwable ex) {
                logger.warn(String.format("Refreshing meta server address failed, will retry in %d seconds",
                        REFRESH_INTERVAL_IN_SECOND), ex);
            }
        }, REFRESH_INTERVAL_IN_SECOND, REFRESH_INTERVAL_IN_SECOND, TimeUnit.SECONDS);
    }

}
