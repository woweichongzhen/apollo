package com.ctrip.framework.apollo.biz.grayReleaseRule;

import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.entity.GrayReleaseRule;
import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;
import com.ctrip.framework.apollo.biz.message.ReleaseMessageListener;
import com.ctrip.framework.apollo.biz.message.Topics;
import com.ctrip.framework.apollo.biz.repository.GrayReleaseRuleRepository;
import com.ctrip.framework.apollo.common.constants.NamespaceBranchStatus;
import com.ctrip.framework.apollo.common.dto.GrayReleaseRuleItemDTO;
import com.ctrip.framework.apollo.common.utils.GrayReleaseRuleItemTransformer;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 灰度发布规则持有
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class GrayReleaseRulesHolder implements ReleaseMessageListener, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(GrayReleaseRulesHolder.class);

    private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR);

    private static final Splitter STRING_SPLITTER =
            Splitter.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR).omitEmptyStrings();

    @Autowired
    private GrayReleaseRuleRepository grayReleaseRuleRepository;

    @Autowired
    private BizConfig bizConfig;

    /**
     * 数据库扫描频率，单位：秒
     */
    private int databaseScanInterval;

    /**
     * ExecutorService 对象
     */
    private final ScheduledExecutorService executorService;

    /**
     * 灰度发布规则缓存
     * <p>
     * key：{@link #assembleGrayReleaseRuleKey(String, String, String)} configAppId+configCluster+configNamespace
     * 在匹配灰度规则时，不关注 branchName 属性
     * <p>
     * value：GrayReleaseRuleCache 数组
     * 因为 branchName 不包含在 KEY 中，而同一个 Namespace 可以创建多次灰度( 创建下一个需要将前一个灰度放弃 )版本，所以就会形成数组
     */
    private final Multimap<String, GrayReleaseRuleCache> grayReleaseRuleCache;

    /**
     * 灰度发布规则反转缓存
     * <p>
     * KEY：{@link #assembleReversedGrayReleaseRuleKey(String, String, String)} 生成, clientAppId+clientNamespace+ip
     * VALUE：{@link GrayReleaseRule#getId} 灰度发布规则id的数组
     */
    private final Multimap<String, Long> reversedGrayReleaseRuleCache;

    /**
     * 加载版本号
     * 自动递增版本以指示规则的使用期限
     */
    private final AtomicLong loadVersion;

    public GrayReleaseRulesHolder() {
        loadVersion = new AtomicLong();
        grayReleaseRuleCache = Multimaps.synchronizedSetMultimap(
                TreeMultimap.create(String.CASE_INSENSITIVE_ORDER, Ordering.natural()));
        reversedGrayReleaseRuleCache = Multimaps.synchronizedSetMultimap(
                TreeMultimap.create(String.CASE_INSENSITIVE_ORDER, Ordering.natural()));
        executorService = Executors.newScheduledThreadPool(
                1, ApolloThreadFactory.create("GrayReleaseRulesHolder", true));
    }

    @Override
    public void afterPropertiesSet() {
        // 拉取定时任务的周期配置
        populateDataBaseInterval();
        // 第一次强制同步扫描规则
        periodicScanRules();
        // 定时同步规则扫描
        executorService.scheduleWithFixedDelay(this::periodicScanRules,
                getDatabaseScanIntervalSecond(), getDatabaseScanIntervalSecond(), getDatabaseScanTimeUnit()
        );
    }

    @Override
    public void handleMessage(ReleaseMessage message, String channel) {
        logger.info("message received - channel: {}, message: {}", channel, message);
        String releaseMessage = message.getMessage();
        // 拒绝不想接受的
        if (!Topics.APOLLO_RELEASE_TOPIC.equals(channel) || Strings.isNullOrEmpty(releaseMessage)) {
            return;
        }

        // 拆分watchkey：appId+cluster+namespace
        List<String> keys = STRING_SPLITTER.splitToList(releaseMessage);
        if (keys.size() != 3) {
            logger.error("message format invalid - {}", releaseMessage);
            return;
        }

        String appId = keys.get(0);
        String cluster = keys.get(1);
        String namespace = keys.get(2);

        // 查找灰度发布规则集合
        List<GrayReleaseRule> rules = grayReleaseRuleRepository.findByAppIdAndClusterNameAndNamespaceName(appId,
                cluster, namespace);

        // 合并灰度规则缓存
        mergeGrayReleaseRules(rules);
    }

    /**
     * 周期性的扫描灰度规则
     */
    private void periodicScanRules() {
        Transaction transaction = Tracer.newTransaction("Apollo.GrayReleaseRulesScanner",
                "scanGrayReleaseRules");
        try {
            // 版本递增
            loadVersion.incrementAndGet();
            // 扫描灰度发布规则
            scanGrayReleaseRules();
            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            transaction.setStatus(ex);
            logger.error("Scan gray release rule failed", ex);
        } finally {
            transaction.complete();
        }
    }

    /**
     * 获取灰度发布规则的发布id
     *
     * @param clientAppId         客户端应用编号
     * @param clientIp            客户端ip
     * @param configAppId         配置的应用编号
     * @param configCluster       配置集群
     * @param configNamespaceName 配置命名空间名称
     * @return 灰度发布id
     */
    public Long findReleaseIdFromGrayReleaseRule(String clientAppId, String clientIp, String
            configAppId, String configCluster, String configNamespaceName) {
        String key = assembleGrayReleaseRuleKey(configAppId, configCluster, configNamespaceName);
        if (!grayReleaseRuleCache.containsKey(key)) {
            return null;
        }
        // 创建新list的灰度发布规则，避免ConcurrentModificationException
        List<GrayReleaseRuleCache> rules = Lists.newArrayList(grayReleaseRuleCache.get(key));
        for (GrayReleaseRuleCache rule : rules) {
            //check branch status
            // 检查规则是否激活中
            if (rule.getBranchStatus() != NamespaceBranchStatus.ACTIVE) {
                continue;
            }
            // 检测客户端应用编号和ip是否符合灰度规则
            if (rule.matches(clientAppId, clientIp)) {
                return rule.getReleaseId();
            }
        }
        return null;
    }

    /**
     * 针对 clientAppId + clientIp + namespaceName ，校验是否有灰度规则
     * 请注意，即使有灰色发布规则，返回为 true，也不意味着调用方能加载到灰度发布的配置
     * <p>
     * 因为，reversedGrayReleaseRuleCache 的 KEY 不包含 branchName ，
     * 所以 reversedGrayReleaseRuleCache 的 VALUE 为多个 branchName 的 Release 编号的集合
     * <p>
     * 因为灰色发布规则实际上适用于另一维度-群集。
     * ConfigFileController 在调用时，是不知道自己使用哪个 clusterName d的，所以这里会查找出来多个分支名（集群名的）发布。
     * 即时返回为true，也不一定能获取到
     * <p>
     * Check whether there are gray release rules for the clientAppId, clientIp, namespace
     * combination. Please note that even there are gray release rules, it doesn't mean it will always
     * load gray releases. Because gray release rules actually apply to one more dimension - cluster.
     */
    public boolean hasGrayReleaseRule(String clientAppId, String clientIp, String namespaceName) {
        return reversedGrayReleaseRuleCache.containsKey(
                assembleReversedGrayReleaseRuleKey(clientAppId, namespaceName, clientIp))
                || reversedGrayReleaseRuleCache.containsKey(
                assembleReversedGrayReleaseRuleKey(clientAppId, namespaceName, GrayReleaseRuleItemDTO.ALL_IP));
    }

    /**
     * 扫描灰度发布规则
     */
    private void scanGrayReleaseRules() {
        long maxIdScanned = 0;
        boolean hasMore = true;

        while (hasMore && !Thread.currentThread().isInterrupted()) {
            // 每次拉取500条灰度规则
            List<GrayReleaseRule> grayReleaseRules = grayReleaseRuleRepository
                    .findFirst500ByIdGreaterThanOrderByIdAsc(maxIdScanned);
            if (CollectionUtils.isEmpty(grayReleaseRules)) {
                break;
            }
            // 合并灰度规则
            mergeGrayReleaseRules(grayReleaseRules);
            int rulesScanned = grayReleaseRules.size();
            // 更新扫描到的最大id
            maxIdScanned = grayReleaseRules.get(rulesScanned - 1).getId();
            // 判断是否满足500，不足则退出循环
            hasMore = rulesScanned == 500;
        }
    }

    /**
     * 合并灰度规则
     *
     * @param grayReleaseRules 灰度规则集合
     */
    private void mergeGrayReleaseRules(List<GrayReleaseRule> grayReleaseRules) {
        if (CollectionUtils.isEmpty(grayReleaseRules)) {
            return;
        }
        for (GrayReleaseRule grayReleaseRule : grayReleaseRules) {

            // 过滤掉没有发布id的灰度，因为子命名空间从来没有发布过
            if (grayReleaseRule.getReleaseId() == null || grayReleaseRule.getReleaseId() == 0) {
                continue;
            }

            // 灰度发布规则key
            String key = assembleGrayReleaseRuleKey(grayReleaseRule.getAppId(), grayReleaseRule
                    .getClusterName(), grayReleaseRule.getNamespaceName());

            // 已缓存的灰度规则列表，创建一个新列表以避免ConcurrentModificationException
            List<GrayReleaseRuleCache> rules = Lists.newArrayList(grayReleaseRuleCache.get(key));
            // 遍历规则，如果存在分支名（即子集群名）一样的规则，拿到老的灰度规则缓存
            GrayReleaseRuleCache oldRule = null;
            for (GrayReleaseRuleCache ruleCache : rules) {
                if (ruleCache.getBranchName().equals(grayReleaseRule.getBranchName())) {
                    oldRule = ruleCache;
                    break;
                }
            }

            // 这是需要新增的情况，即不存在老规则，但新规则又未激活，略过
            if (oldRule == null
                    && grayReleaseRule.getBranchStatus() != NamespaceBranchStatus.ACTIVE) {
                continue;
            }

            // 使用ID比较新老规则以避免同步
            if (oldRule == null
                    || grayReleaseRule.getId() > oldRule.getRuleId()) {
                // 老规则不存在，或者新的灰度规则id大于老规则id，添加新灰度规则的缓存
                addCache(key, this.transformRuleToRuleCache(grayReleaseRule));
                // 并且移除老规则的缓存
                if (oldRule != null) {
                    removeCache(key, oldRule);
                }
            } else {
                // 新老规则相同，刷新有效的老规则的加载版本，维护两个周期未激活的老版本（删除和合并情况）
                if (oldRule.getBranchStatus() == NamespaceBranchStatus.ACTIVE) {
                    // 如果还存在激活的老规则，更新老规则的加载版本
                    // 例如，定时轮询，有可能，早于 `#handleMessage(...)` 拿到对应的新的 GrayReleaseRule
                    // 那么此时规则编号是相等的，不符合上面的条件，但是符合这个条件。
                    // 再例如，两次定时轮询，第二次和第一次的规则编号是相等的，不符合上面的条件，但是符合这个条件。
                    oldRule.setLoadVersion(loadVersion.get());
                } else if ((loadVersion.get() - oldRule.getLoadVersion()) > 1) {
                    // 如果老规则已经不激活了，并且缓存已经维持了两个周期了，移除老规则
                    // 适用于 灰度规则状态 为 DELETED 或 MERGED 的情况。
                    removeCache(key, oldRule);
                }
            }
        }
    }

    /**
     * 添加新规则的缓存
     *
     * @param key       缓存key
     * @param ruleCache 规则缓存
     */
    private void addCache(String key, GrayReleaseRuleCache ruleCache) {
        // 为什么这里判断状态？因为删除灰度，或者灰度全量发布的情况下，是无效的，所以不添加到 reversedGrayReleaseRuleCache 中
        if (ruleCache.getBranchStatus() == NamespaceBranchStatus.ACTIVE) {
            for (GrayReleaseRuleItemDTO ruleItemDTO : ruleCache.getRuleItems()) {
                for (String clientIp : ruleItemDTO.getClientIpList()) {
                    // 先更新反转缓存
                    reversedGrayReleaseRuleCache.put(
                            assembleReversedGrayReleaseRuleKey(ruleItemDTO.getClientAppId(),
                                    ruleCache.getNamespaceName(), clientIp),
                            ruleCache.getRuleId());
                }
            }
        }
        // 再更新正向缓存
        // 这里为什么可以添加？因为添加到 grayReleaseRuleCache 中是个对象，可以判断状态
        grayReleaseRuleCache.put(key, ruleCache);
    }

    /**
     * 移除旧缓存或过期缓存
     *
     * @param key       缓存key
     * @param ruleCache 规则缓存
     */
    private void removeCache(String key, GrayReleaseRuleCache ruleCache) {
        // 先移除正向缓存
        grayReleaseRuleCache.remove(key, ruleCache);
        for (GrayReleaseRuleItemDTO ruleItemDTO : ruleCache.getRuleItems()) {
            for (String clientIp : ruleItemDTO.getClientIpList()) {
                // 再移除反转缓存
                reversedGrayReleaseRuleCache.remove(assembleReversedGrayReleaseRuleKey(ruleItemDTO
                        .getClientAppId(), ruleCache.getNamespaceName(), clientIp), ruleCache.getRuleId());
            }
        }
    }

    /**
     * 转换规则到规则缓存
     *
     * @param grayReleaseRule 灰度规则
     * @return 灰度规则缓存
     */
    private GrayReleaseRuleCache transformRuleToRuleCache(GrayReleaseRule grayReleaseRule) {
        Set<GrayReleaseRuleItemDTO> ruleItems;
        try {
            ruleItems = GrayReleaseRuleItemTransformer.batchTransformFromJSON(grayReleaseRule.getRules());
        } catch (Throwable ex) {
            ruleItems = Sets.newHashSet();
            Tracer.logError(ex);
            logger.error("parse rule for gray release rule {} failed", grayReleaseRule.getId(), ex);
        }

        GrayReleaseRuleCache ruleCache = new GrayReleaseRuleCache(grayReleaseRule.getId(),
                grayReleaseRule.getBranchName(), grayReleaseRule.getNamespaceName(), grayReleaseRule
                .getReleaseId(), grayReleaseRule.getBranchStatus(), loadVersion.get(), ruleItems);

        return ruleCache;
    }

    /**
     * 拉取定时任务的周期配置
     */
    private void populateDataBaseInterval() {
        databaseScanInterval = bizConfig.grayReleaseRuleScanInterval();
    }

    private int getDatabaseScanIntervalSecond() {
        return databaseScanInterval;
    }

    /**
     * 扫描数据库单位，秒
     */
    private TimeUnit getDatabaseScanTimeUnit() {
        return TimeUnit.SECONDS;
    }

    /**
     * 组装灰度发布规则key，不使用分支名作为key，因为可能存在新老规则分支名相同，没有比较的意义
     */
    private String assembleGrayReleaseRuleKey(String configAppId, String configCluster, String
            configNamespaceName) {
        return STRING_JOINER.join(configAppId, configCluster, configNamespaceName);
    }

    /**
     * 组装反转的灰度发布规则key
     */
    private String assembleReversedGrayReleaseRuleKey(String clientAppId, String
            clientNamespaceName, String clientIp) {
        return STRING_JOINER.join(clientAppId, clientNamespaceName, clientIp);
    }

}
