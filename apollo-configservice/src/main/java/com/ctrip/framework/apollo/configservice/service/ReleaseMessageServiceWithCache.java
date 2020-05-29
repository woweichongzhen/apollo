package com.ctrip.framework.apollo.configservice.service;

import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;
import com.ctrip.framework.apollo.biz.message.ReleaseMessageListener;
import com.ctrip.framework.apollo.biz.message.Topics;
import com.ctrip.framework.apollo.biz.repository.ReleaseMessageRepository;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 发布消息服务缓存
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Service
public class ReleaseMessageServiceWithCache implements ReleaseMessageListener, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(ReleaseMessageServiceWithCache.class);

    private final ReleaseMessageRepository releaseMessageRepository;

    private final BizConfig bizConfig;

    /**
     * 扫描消息周期
     */
    private int scanInterval;

    /**
     * 扫描消息单位
     */
    private TimeUnit scanIntervalTimeUnit;

    /**
     * volatile，扫描到的最大消息id
     */
    private volatile long maxIdScanned;

    /**
     * 发布消息缓存
     * key：message内容，appId + clusterName + namespaceName
     * value：对应的发布消息
     */
    private ConcurrentMap<String, ReleaseMessage> releaseMessageCache;

    /**
     * 是否允许定时扫描
     */
    private AtomicBoolean doScan;

    /**
     * 扫描线程
     */
    private ExecutorService executorService;

    public ReleaseMessageServiceWithCache(
            final ReleaseMessageRepository releaseMessageRepository,
            final BizConfig bizConfig) {
        this.releaseMessageRepository = releaseMessageRepository;
        this.bizConfig = bizConfig;
        initialize();
    }

    /**
     * 初始化扫描线程池等
     */
    private void initialize() {
        releaseMessageCache = Maps.newConcurrentMap();
        doScan = new AtomicBoolean(true);
        executorService = Executors.newSingleThreadExecutor(
                ApolloThreadFactory.create("ReleaseMessageServiceWithCache", true));
    }

    public ReleaseMessage findLatestReleaseMessageForMessages(Set<String> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return null;
        }

        long maxReleaseMessageId = 0;
        ReleaseMessage result = null;
        for (String message : messages) {
            ReleaseMessage releaseMessage = releaseMessageCache.get(message);
            if (releaseMessage != null && releaseMessage.getId() > maxReleaseMessageId) {
                maxReleaseMessageId = releaseMessage.getId();
                result = releaseMessage;
            }
        }

        return result;
    }

    /**
     * 查找最新的发布消息
     *
     * @param messages 监控key，即消息内容
     * @return 监控内容的最新发布消息
     */
    public List<ReleaseMessage> findLatestReleaseMessagesGroupByMessages(Set<String> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return Collections.emptyList();
        }
        List<ReleaseMessage> releaseMessages = Lists.newArrayList();

        // 遍历监控的发布消息，返回
        for (String message : messages) {
            ReleaseMessage releaseMessage = releaseMessageCache.get(message);
            if (releaseMessage != null) {
                releaseMessages.add(releaseMessage);
            }
        }

        return releaseMessages;
    }

    @Override
    public void handleMessage(ReleaseMessage message, String channel) {
        // ReleaseMessageScanner开始工作后可能会停止
        // 开始收到监听的消息时，说明已经开始从数据库扫描到消息，无需继续从定时任务扫描
        doScan.set(false);
        logger.info("message received - channel: {}, message: {}", channel, message);

        String content = message.getMessage();
        Tracer.logEvent("Apollo.ReleaseMessageService.UpdateCache", String.valueOf(message.getId()));

        // 非自己监听的通道，或内容为空，直接返回
        if (!Topics.APOLLO_RELEASE_TOPIC.equals(channel)
                || Strings.isNullOrEmpty(content)) {
            return;
        }

        long gap = message.getId() - maxIdScanned;
        if (gap == 1) {
            // 存在一条发布消息，直接合并
            mergeReleaseMessage(message);
        } else if (gap > 1) {
            /*
             * 存在更多发布消息，即中间可能有遗漏的，执行批量增量加载
             * 有可能是定时任务还没来得及执行，就已经通知，此时会出现代沟
             */
            loadReleaseMessages(maxIdScanned);
        }
    }

    @Override
    public void afterPropertiesSet() {
        // 填充扫描定时任务数据
        populateDataBaseInterval();

        /*
         * block the startup process until load finished
         * this should happen before ReleaseMessageScanner due to autowire
         *
         * 阻塞启动过程，直到加载完成
         * 由于自动装配，这应该在ReleaseMessageScanner之前发生
         */
        loadReleaseMessages(0);

        /*
         * 定时任务扫描的作用：
         * 20:00:00 程序启动过程中，当前 release message 有 5 条
         * 20:00:01 loadReleaseMessages(0); 执行完成，获取到 5 条记录
         * 20:00:02 有一条 release message 新产生，但是因为程序还没启动完，所以不会触发 handle message 操作
         * 20:00:05 程序启动完成，但是第三步的这条新的 release message 漏了
         * 20:10:00 假设这时又有一条 release message 产生，这次会触发 handle message ，同时会把第三步的那条 release message 加载到
         *
         * 所以，定期刷的机制就是为了解决第三步中产生的release message问题。
         *
         * 当程序启动完，handleMessage生效后，就不需要再定期扫了
         *
         * 即 ReleaseMessageServiceWithCache 初始化完成之后，ReleaseMessageScanner 初始化之前，产生了一条新消息，
         * 会导致 ReleaseMessageScanner.maxIdScanned 大于 ReleaseMessageServiceWithCache.maxIdScanned ，
         * 从而导致消息缓存的遗漏，依靠启动后的扫描机制去补偿
         */
        executorService.submit(() -> {
            // 如果允许扫描并且线程未终端，则while true
            while (doScan.get() && !Thread.currentThread().isInterrupted()) {
                Transaction transaction = Tracer.newTransaction("Apollo.ReleaseMessageServiceWithCache",
                        "scanNewReleaseMessages");
                try {
                    // 基于扫描到的id，继续增量拉取
                    loadReleaseMessages(maxIdScanned);
                    transaction.setStatus(Transaction.SUCCESS);
                } catch (Throwable ex) {
                    transaction.setStatus(ex);
                    logger.error("Scan new release messages failed", ex);
                } finally {
                    transaction.complete();
                }

                // 等待一定周期继续
                try {
                    scanIntervalTimeUnit.sleep(scanInterval);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        });
    }

    /**
     * 合并消息到本地缓存中
     *
     * @param releaseMessage 消息内容
     */
    private synchronized void mergeReleaseMessage(ReleaseMessage releaseMessage) {
        // 如果已经缓存中已经有了，并且新的比老的新，更新
        ReleaseMessage old = releaseMessageCache.get(releaseMessage.getMessage());
        if (old == null
                || releaseMessage.getId() > old.getId()) {
            releaseMessageCache.put(releaseMessage.getMessage(), releaseMessage);
            maxIdScanned = releaseMessage.getId();
        }
    }

    /**
     * 增量拉取最大消息
     *
     * @param startId 拉取起始id
     */
    private void loadReleaseMessages(long startId) {
        boolean hasMore = true;
        while (hasMore && !Thread.currentThread().isInterrupted()) {
            //current batch is 500
            // 每次基于开始id，拉取500条
            List<ReleaseMessage> releaseMessages =
                    releaseMessageRepository.findFirst500ByIdGreaterThanOrderByIdAsc(startId);
            if (CollectionUtils.isEmpty(releaseMessages)) {
                break;
            }

            // 遍历合并
            releaseMessages.forEach(this::mergeReleaseMessage);
            int scanned = releaseMessages.size();

            // 每次拉取500后，更新起始id
            startId = releaseMessages.get(scanned - 1).getId();

            // 判断本次是否够500，不够说明是最后一批
            hasMore = scanned == 500;
            logger.info("Loaded {} release messages with startId {}", scanned, startId);
        }
    }

    /**
     * 填充扫描定时任务的数据
     */
    private void populateDataBaseInterval() {
        scanInterval = bizConfig.releaseMessageCacheScanInterval();
        scanIntervalTimeUnit = bizConfig.releaseMessageCacheScanIntervalTimeUnit();
    }

    //only for test use
    private void reset() throws Exception {
        executorService.shutdownNow();
        initialize();
        afterPropertiesSet();
    }
}
