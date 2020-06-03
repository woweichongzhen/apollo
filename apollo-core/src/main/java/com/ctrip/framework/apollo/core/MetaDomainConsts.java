package com.ctrip.framework.apollo.core;

import com.ctrip.framework.apollo.core.enums.Env;
import com.ctrip.framework.apollo.core.spi.MetaServerProvider;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.core.utils.NetUtil;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.foundation.internals.ServiceBootstrap;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 元数据域名常量
 * 加载元数据服务地址通过 元数据服务提供者
 * <p>
 * The meta domain will try to load the meta server address from MetaServerProviders, the default ones are:
 *
 * <ul>
 * <li>com.ctrip.framework.apollo.core.internals.LegacyMetaServerProvider</li>
 * </ul>
 * <p>
 * If no provider could provide the meta server url, the default meta url will be used(http://apollo.meta).
 * <br />
 * <p>
 * 3rd party MetaServerProvider could be injected by typical Java Service Loader pattern.
 *
 * @see com.ctrip.framework.apollo.core.internals.LegacyMetaServerProvider
 */
public class MetaDomainConsts {
    public static final String DEFAULT_META_URL = "http://apollo.meta";

    /**
     * 原始的元数据服务地址缓存
     */
    private static final Map<Env, String> META_SERVER_ADDRESS_CACHE = Maps.newConcurrentMap();
    private static volatile List<MetaServerProvider> metaServerProviders = null;

    /**
     * 刷新定时任务的周期，60S
     */
    private static final long REFRESH_INTERVAL_IN_SECOND = 60;// 1 min
    private static final Logger logger = LoggerFactory.getLogger(MetaDomainConsts.class);

    /**
     * 随机地址缓存
     * key：完整的元数据服务地址
     * value：上一次随机挑选的
     */
    private static final Map<String, String> SELECTED_META_SERVER_ADDRESS_CACHE = Maps.newConcurrentMap();
    private static final AtomicBoolean PERIODIC_REFRESH_STARTED = new AtomicBoolean(false);

    private static final Object LOCK = new Object();

    /**
     * 返回一个元数据服务地址，如果存在多个，随机挑选一个
     * Return one meta server address. If multiple meta server addresses are configured, will select one.
     */
    public static String getDomain(Env env) {
        String metaServerAddress = getMetaServerAddress(env);
        // if there is more than one address, need to select one
        if (metaServerAddress.contains(",")) {
            return selectMetaServerAddress(metaServerAddress);
        }
        return metaServerAddress;
    }

    /**
     * 获取指定环境的元数据服务地址，如果存在多个，返回带逗号的
     * Return meta server address. If multiple meta server addresses are configured, will return the comma separated
     * string.
     */
    public static String getMetaServerAddress(Env env) {
        if (!META_SERVER_ADDRESS_CACHE.containsKey(env)) {
            initMetaServerAddress(env);
        }

        return META_SERVER_ADDRESS_CACHE.get(env);
    }

    /**
     * 初始化元数据服务地址
     *
     * @param env 环境
     */
    private static void initMetaServerAddress(Env env) {
        if (metaServerProviders == null) {
            synchronized (LOCK) {
                if (metaServerProviders == null) {
                    metaServerProviders = initMetaServerProviders();
                }
            }
        }

        String metaAddress = null;

        // 遍历获取元数据服务提供者，知道获取一个不为空的
        for (MetaServerProvider provider : metaServerProviders) {
            metaAddress = provider.getMetaServerAddress(env);
            if (!Strings.isNullOrEmpty(metaAddress)) {
                logger.info("Located meta server address {} for env {} from {}", metaAddress, env,
                        provider.getClass().getName());
                break;
            }
        }

        // 如果都没获取到，使用默认的
        if (Strings.isNullOrEmpty(metaAddress)) {
            // Fallback to default meta address
            metaAddress = DEFAULT_META_URL;
            logger.warn(
                    "Meta server address fallback to {} for env {}, because it is not available in all " +
                            "MetaServerProviders",
                    metaAddress, env);
        }

        // 缓存
        META_SERVER_ADDRESS_CACHE.put(env, metaAddress.trim());
    }

    /**
     * 初始化元数据服务提供者
     *
     * @return 元数据服务提供者集合
     */
    private static List<MetaServerProvider> initMetaServerProviders() {
        // spi获取所有元数据服务提供者
        Iterator<MetaServerProvider> metaServerProviderIterator = ServiceBootstrap.loadAll(MetaServerProvider.class);

        List<MetaServerProvider> metaServerProviders = Lists.newArrayList(metaServerProviderIterator);
        Collections.sort(metaServerProviders, new Comparator<MetaServerProvider>() {
            @Override
            public int compare(MetaServerProvider o1, MetaServerProvider o2) {
                // the smaller order has higher priority
                // 根据顺序排序，小的有高的优先级
                return Integer.compare(o1.getOrder(), o2.getOrder());
            }
        });

        return metaServerProviders;
    }

    /**
     * 随机挑选一个元数据服务
     * <p>
     * Select one available meta server from the comma separated meta server addresses, e.g.
     * http://1.2.3.4:8080,http://2.3.4.5:8080
     * <p>
     * <br />
     * <p>
     * In production environment, we still suggest using one single domain like http://config.xxx.com(backed by software
     * load balancers like nginx) instead of multiple ip addresses
     */
    private static String selectMetaServerAddress(String metaServerAddresses) {
        // 获取上次选中的，如果没有，初始化定时任务拉取
        String metaAddressSelected = SELECTED_META_SERVER_ADDRESS_CACHE.get(metaServerAddresses);
        if (metaAddressSelected == null) {
            // initialize
            if (PERIODIC_REFRESH_STARTED.compareAndSet(false, true)) {
                schedulePeriodicRefresh();
            }

            // 根据当前配置的更新随机地址缓存
            updateMetaServerAddresses(metaServerAddresses);
            metaAddressSelected = SELECTED_META_SERVER_ADDRESS_CACHE.get(metaServerAddresses);
        }

        return metaAddressSelected;
    }

    /**
     * 更新随机地址缓存
     *
     * @param metaServerAddresses 元数据服务地址
     */
    private static void updateMetaServerAddresses(String metaServerAddresses) {
        logger.debug("Selecting meta server address for: {}", metaServerAddresses);

        Transaction transaction = Tracer.newTransaction("Apollo.MetaService", "refreshMetaServerAddress");
        transaction.addData("Url", metaServerAddresses);

        try {
            List<String> metaServers = Lists.newArrayList(metaServerAddresses.split(","));
            // 随机负载均衡
            Collections.shuffle(metaServers);

            boolean serverAvailable = false;

            // 按顺序请求，返回第一个正常的放到缓存中
            for (String address : metaServers) {
                address = address.trim();
                //check whether /services/config is accessible
                if (NetUtil.pingUrl(address + "/services/config")) {
                    // select the first available meta server
                    SELECTED_META_SERVER_ADDRESS_CACHE.put(metaServerAddresses, address);
                    serverAvailable = true;
                    logger.debug("Selected meta server address {} for {}", address, metaServerAddresses);
                    break;
                }
            }

            // we need to make sure the map is not empty, e.g. the first update might be failed
            // 如果都失败了，随便拿第一个用
            if (!SELECTED_META_SERVER_ADDRESS_CACHE.containsKey(metaServerAddresses)) {
                SELECTED_META_SERVER_ADDRESS_CACHE.put(metaServerAddresses, metaServers.get(0).trim());
            }

            if (!serverAvailable) {
                logger.warn("Could not find available meta server for configured meta server addresses: {}, fallback " +
                                "to: {}",
                        metaServerAddresses, SELECTED_META_SERVER_ADDRESS_CACHE.get(metaServerAddresses));
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
     * 定时刷新元数据服务地址
     */
    private static void schedulePeriodicRefresh() {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(
                1, ApolloThreadFactory.create("MetaServiceLocator", true));

        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                // 完整的元数据服务地址缓存，60S一次更新，把第一个可用的放到缓存中
                try {
                    for (String metaServerAddresses : SELECTED_META_SERVER_ADDRESS_CACHE.keySet()) {
                        updateMetaServerAddresses(metaServerAddresses);
                    }
                } catch (Throwable ex) {
                    logger.warn(String.format("Refreshing meta server address failed, will retry in %d seconds",
                            REFRESH_INTERVAL_IN_SECOND), ex);
                }
            }
        }, REFRESH_INTERVAL_IN_SECOND, REFRESH_INTERVAL_IN_SECOND, TimeUnit.SECONDS);
    }
}
