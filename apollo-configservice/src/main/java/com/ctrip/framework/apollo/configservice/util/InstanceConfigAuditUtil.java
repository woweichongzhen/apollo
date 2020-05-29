package com.ctrip.framework.apollo.configservice.util;

import com.ctrip.framework.apollo.biz.entity.Instance;
import com.ctrip.framework.apollo.biz.entity.InstanceConfig;
import com.ctrip.framework.apollo.biz.service.InstanceService;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实例配置审计工具
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Service
public class InstanceConfigAuditUtil implements InitializingBean {

    private static final long OFFER_TIME_LAST_MODIFIED_TIME_THRESHOLD_IN_MILLI = TimeUnit.MINUTES.toMillis(10);

    // minutes
    private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR);

    /**
     * 审计定时任务
     */
    private final ExecutorService auditExecutorService;

    /**
     * 审计是否停止
     */
    private final AtomicBoolean auditStopped;

    /**
     * 审计阻塞队列
     */
    private final BlockingQueue<InstanceConfigAuditModel> audits =
            Queues.newLinkedBlockingQueue(INSTANCE_CONFIG_AUDIT_MAX_SIZE);
    private static final int INSTANCE_CONFIG_AUDIT_MAX_SIZE = 10000;

    /**
     * 实例编号缓存，1小时过期
     * <p>
     * KEY：{@link #assembleInstanceKey(String, String, String, String)} Instace的唯一索引
     * VALUE：{@link Instance#getId}
     */
    private Cache<String, Long> instanceCache;
    private static final int INSTANCE_CACHE_MAX_SIZE = 50000;

    /**
     * 实例配置发布key缓存，1天过期
     * <p>
     * KEY：{@link #assembleInstanceConfigKey(long, String, String)} InstanceConfig唯一索引
     * VALUE：{@link InstanceConfig#getId}
     */
    private Cache<String, String> instanceConfigReleaseKeyCache;
    private static final int INSTANCE_CONFIG_CACHE_MAX_SIZE = 50000;

    private final InstanceService instanceService;

    public InstanceConfigAuditUtil(final InstanceService instanceService) {
        this.instanceService = instanceService;

        auditExecutorService = Executors.newSingleThreadExecutor(
                ApolloThreadFactory.create("InstanceConfigAuditUtil", true));
        auditStopped = new AtomicBoolean(false);

        instanceCache = CacheBuilder.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(INSTANCE_CACHE_MAX_SIZE)
                .build();

        instanceConfigReleaseKeyCache = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.DAYS)
                .maximumSize(INSTANCE_CONFIG_CACHE_MAX_SIZE)
                .build();
    }

    /**
     * 客户端读取配置时，要审计
     *
     * @param appId             应用编号
     * @param clusterName       集群名称
     * @param dataCenter        数据中心
     * @param ip                ip地址
     * @param configAppId       配置的应用编号
     * @param configClusterName 配置集群名称
     * @param configNamespace   配置命名空间
     * @param releaseKey        发布key
     * @return true审计ok，false审计不行
     */
    public boolean audit(String appId, String clusterName, String dataCenter, String
            ip, String configAppId, String configClusterName, String configNamespace, String releaseKey) {
        return audits.offer(new InstanceConfigAuditModel(appId, clusterName, dataCenter, ip,
                configAppId, configClusterName, configNamespace, releaseKey));
    }

    /**
     * 执行审计
     *
     * @param auditModel 实例配置审计模型
     */
    void doAudit(InstanceConfigAuditModel auditModel) {
        // 组装实例key
        String instanceCacheKey = assembleInstanceKey(auditModel.getAppId(), auditModel
                .getClusterName(), auditModel.getIp(), auditModel.getDataCenter());
        // 通过唯一键获取实例id
        Long instanceId = instanceCache.getIfPresent(instanceCacheKey);
        // 如果不存在，通过审计模型获取实例id（查找或者创建），并放到缓存中
        if (instanceId == null) {
            instanceId = prepareInstanceId(auditModel);
            instanceCache.put(instanceCacheKey, instanceId);
        }

        //load instance config release key from cache, and check if release key is the same
        // 组装实例配置唯一key，然后从缓存获取发布key，并检查发布key是否相同
        String instanceConfigCacheKey = assembleInstanceConfigKey(instanceId, auditModel
                .getConfigAppId(), auditModel.getConfigNamespace());
        String cacheReleaseKey = instanceConfigReleaseKeyCache.getIfPresent(instanceConfigCacheKey);
        // 如果发布key相同，则不处理
        if (cacheReleaseKey != null
                && Objects.equals(cacheReleaseKey, auditModel.getReleaseKey())) {
            return;
        }
        // 不同则重新缓存
        instanceConfigReleaseKeyCache.put(instanceConfigCacheKey, auditModel.getReleaseKey());

        //if release key is not the same or cannot find in cache, then do audit
        // 如果发布key不同，执行审计，获取实例配置，获取到执行更新
        InstanceConfig instanceConfig = instanceService.findInstanceConfig(
                instanceId,
                auditModel.getConfigAppId(),
                auditModel.getConfigNamespace());
        if (instanceConfig != null) {
            if (!Objects.equals(instanceConfig.getReleaseKey(), auditModel.getReleaseKey())) {
                // 发布key不同，设置releaseKey更新的相关信息
                instanceConfig.setConfigClusterName(auditModel.getConfigClusterName());
                instanceConfig.setReleaseKey(auditModel.getReleaseKey());
                instanceConfig.setReleaseDeliveryTime(auditModel.getOfferTime());
            } else if (this.offerTimeAndLastModifiedTimeCloseEnough(auditModel.getOfferTime(),
                    instanceConfig.getDataChangeLastModifiedTime())) {
                // 入队时间和上次修改时间相差过近
                // 例如 Client 先请求的 Config Service A 节点，再请求 Config Service B 节点的情况，而B还未更新，无需更新，直接返回
                // when releaseKey is the same, optimize to reduce writes if the record was updated not long ago
                return;
            }
            //we need to update no matter the release key is the same or not, to ensure the
            //last modified time is updated each day
            // 无论发布密钥是否相同，都需要更新，以确保最近一次修改的时间每天都在更新
            instanceConfig.setDataChangeLastModifiedTime(auditModel.getOfferTime());
            instanceService.updateInstanceConfig(instanceConfig);
            return;
        }

        // 如果不存在实例配置，新增一个
        instanceConfig = new InstanceConfig();
        instanceConfig.setInstanceId(instanceId);
        instanceConfig.setConfigAppId(auditModel.getConfigAppId());
        instanceConfig.setConfigClusterName(auditModel.getConfigClusterName());
        instanceConfig.setConfigNamespaceName(auditModel.getConfigNamespace());
        instanceConfig.setReleaseKey(auditModel.getReleaseKey());
        instanceConfig.setReleaseDeliveryTime(auditModel.getOfferTime());
        instanceConfig.setDataChangeCreatedTime(auditModel.getOfferTime());

        try {
            instanceService.createInstanceConfig(instanceConfig);
        } catch (DataIntegrityViolationException ex) {
            //concurrent insertion, safe to ignore
        }
    }

    /**
     * 时间过近，仅相差 10 分钟。
     * 例如，Client 先请求的 Config Service A 节点，再请求 Config Service B 节点的情况。
     * 此时，InstanceConfig 在 DB 中是已经更新了，但是在 Config Service B 节点的缓存是未更新的
     *
     * @param offerTime        入队时间
     * @param lastModifiedTime 最后修改时间
     * @return true即入队时间和上次修改时间相差很近，无需修改
     */
    private boolean offerTimeAndLastModifiedTimeCloseEnough(Date offerTime, Date lastModifiedTime) {
        return (offerTime.getTime() - lastModifiedTime.getTime()) <
                OFFER_TIME_LAST_MODIFIED_TIME_THRESHOLD_IN_MILLI;
    }

    /**
     * 准备实例id
     *
     * @param auditModel 审计模型
     * @return 处理后的实例id
     */
    private long prepareInstanceId(InstanceConfigAuditModel auditModel) {
        // 先从数据库中查找相关实例
        Instance instance = instanceService.findInstance(auditModel.getAppId(), auditModel
                .getClusterName(), auditModel.getDataCenter(), auditModel.getIp());
        if (instance != null) {
            return instance.getId();
        }

        // 实例不存在，是新增的，执行插入
        instance = new Instance();
        instance.setAppId(auditModel.getAppId());
        instance.setClusterName(auditModel.getClusterName());
        instance.setDataCenter(auditModel.getDataCenter());
        instance.setIp(auditModel.getIp());

        try {
            return instanceService.createInstance(instance).getId();
        } catch (DataIntegrityViolationException ex) {
            //return the one exists
            return instanceService.findInstance(instance.getAppId(), instance.getClusterName(),
                    instance.getDataCenter(), instance.getIp()).getId();
        }
    }

    @Override
    public void afterPropertiesSet() {
        // 服务启动后，提交任务定时审计
        auditExecutorService.submit(() -> {
            while (!auditStopped.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // 非阻塞
                    InstanceConfigAuditModel model = audits.poll();
                    // 取出为空，等待1S，继续拉取
                    if (model == null) {
                        TimeUnit.SECONDS.sleep(1);
                        continue;
                    }
                    doAudit(model);
                } catch (Throwable ex) {
                    Tracer.logError(ex);
                }
            }
        });
    }

    /**
     * 组装实例key
     *
     * @param appId      应用编号
     * @param cluster    集群名称
     * @param ip         ip
     * @param datacenter 数据中心
     * @return 实例key
     */
    private String assembleInstanceKey(String appId, String cluster, String ip, String datacenter) {
        List<String> keyParts = Lists.newArrayList(appId, cluster, ip);
        if (!Strings.isNullOrEmpty(datacenter)) {
            keyParts.add(datacenter);
        }
        return STRING_JOINER.join(keyParts);
    }

    /**
     * 组装实例配置key
     *
     * @param instanceId      实例id
     * @param configAppId     配置应用编号
     * @param configNamespace 配置命名空间名称
     * @return 实例配置key
     */
    private String assembleInstanceConfigKey(long instanceId, String configAppId, String configNamespace) {
        return STRING_JOINER.join(instanceId, configAppId, configNamespace);
    }

    /**
     * 实例配置审计模型
     */
    public static class InstanceConfigAuditModel {

        private final String appId;
        private final String clusterName;
        private final String dataCenter;
        private final String ip;
        private final String configAppId;
        private final String configClusterName;
        private final String configNamespace;
        private final String releaseKey;
        private final Date offerTime;

        public InstanceConfigAuditModel(String appId, String clusterName, String dataCenter, String
                clientIp, String configAppId, String configClusterName, String configNamespace, String
                                                releaseKey) {
            this.offerTime = new Date();
            this.appId = appId;
            this.clusterName = clusterName;
            this.dataCenter = Strings.isNullOrEmpty(dataCenter) ? "" : dataCenter;
            this.ip = clientIp;
            this.configAppId = configAppId;
            this.configClusterName = configClusterName;
            this.configNamespace = configNamespace;
            this.releaseKey = releaseKey;
        }

        public String getAppId() {
            return appId;
        }

        public String getClusterName() {
            return clusterName;
        }

        public String getDataCenter() {
            return dataCenter;
        }

        public String getIp() {
            return ip;
        }

        public String getConfigAppId() {
            return configAppId;
        }

        public String getConfigNamespace() {
            return configNamespace;
        }

        public String getReleaseKey() {
            return releaseKey;
        }

        public String getConfigClusterName() {
            return configClusterName;
        }

        public Date getOfferTime() {
            return offerTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            InstanceConfigAuditModel model = (InstanceConfigAuditModel) o;
            return Objects.equals(appId, model.appId) &&
                    Objects.equals(clusterName, model.clusterName) &&
                    Objects.equals(dataCenter, model.dataCenter) &&
                    Objects.equals(ip, model.ip) &&
                    Objects.equals(configAppId, model.configAppId) &&
                    Objects.equals(configClusterName, model.configClusterName) &&
                    Objects.equals(configNamespace, model.configNamespace) &&
                    Objects.equals(releaseKey, model.releaseKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(appId, clusterName, dataCenter, ip, configAppId, configClusterName,
                    configNamespace,
                    releaseKey);
        }
    }
}
