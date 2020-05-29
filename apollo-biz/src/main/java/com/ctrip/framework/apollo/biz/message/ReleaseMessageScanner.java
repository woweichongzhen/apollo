package com.ctrip.framework.apollo.biz.message;

import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;
import com.ctrip.framework.apollo.biz.repository.ReleaseMessageRepository;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 发布消息监听器
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ReleaseMessageScanner implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(ReleaseMessageScanner.class);

    @Autowired
    private BizConfig bizConfig;

    @Autowired
    private ReleaseMessageRepository releaseMessageRepository;

    /**
     * 从数据库扫描的周期
     */
    private int databaseScanInterval;

    /**
     * configservice监听器数组
     */
    private List<ReleaseMessageListener> listeners;

    /**
     * 定时任务扫描
     */
    private ScheduledExecutorService executorService;

    /**
     * 扫描到的最大id，即最后的消息
     */
    private long maxIdScanned;

    public ReleaseMessageScanner() {
        listeners = Lists.newCopyOnWriteArrayList();
        executorService = Executors.newScheduledThreadPool(1,
                ApolloThreadFactory.create("ReleaseMessageScanner", true));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 扫描周期
        databaseScanInterval = bizConfig.releaseMessageScanIntervalInMilli();
        // 扫描到的最大的消息id
        maxIdScanned = loadLargestMessageId();
        executorService.scheduleWithFixedDelay(() -> {
            Transaction transaction = Tracer.newTransaction("Apollo.ReleaseMessageScanner", "scanMessage");
            try {
                // 扫描消息
                scanMessages();
                transaction.setStatus(Transaction.SUCCESS);
            } catch (Throwable ex) {
                transaction.setStatus(ex);
                logger.error("Scan and send message failed", ex);
            } finally {
                transaction.complete();
            }
        }, databaseScanInterval, databaseScanInterval, TimeUnit.MILLISECONDS);

    }

    /**
     * add message listeners for release message
     *
     * @param listener
     */
    public void addMessageListener(ReleaseMessageListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 扫描消息，继续扫描直到没有更多消息
     * Scan messages, continue scanning until there is no more messages
     */
    private void scanMessages() {
        boolean hasMoreMessages = true;
        while (hasMoreMessages && !Thread.currentThread().isInterrupted()) {
            hasMoreMessages = scanAndSendMessages();
        }
    }

    /**
     * 扫描消息并发送
     * scan messages and send
     *
     * @return whether there are more messages 是否还有更多的消息
     */
    private boolean scanAndSendMessages() {
        //current batch is 500
        // 获取最新的500条消息
        List<ReleaseMessage> releaseMessages =
                releaseMessageRepository.findFirst500ByIdGreaterThanOrderByIdAsc(maxIdScanned);
        if (CollectionUtils.isEmpty(releaseMessages)) {
            return false;
        }
        // 触发消息监听器
        fireMessageScanned(releaseMessages);

        // 更新最新的消息id
        int messageScanned = releaseMessages.size();
        maxIdScanned = releaseMessages.get(messageScanned - 1).getId();

        // 拉取的消息不足500，说明没有更多的消息了
        return messageScanned == 500;
    }

    /**
     * 找到最大的消息ID作为当前起点
     * find largest message id as the current start point
     *
     * @return current largest message id 当前最大的消息id
     */
    private long loadLargestMessageId() {
        ReleaseMessage releaseMessage = releaseMessageRepository.findTopByOrderByIdDesc();
        return releaseMessage == null
                ? 0
                : releaseMessage.getId();
    }

    /**
     * 通知监听器当消息被拉取到时
     * Notify listeners with messages loaded
     *
     * @param messages 最新的一批消息
     */
    private void fireMessageScanned(List<ReleaseMessage> messages) {
        for (ReleaseMessage message : messages) {
            // 遍历监听器，执行处理指定通道消息的逻辑
            for (ReleaseMessageListener listener : listeners) {
                try {
                    listener.handleMessage(message, Topics.APOLLO_RELEASE_TOPIC);
                } catch (Throwable ex) {
                    Tracer.logError(ex);
                    logger.error("Failed to invoke message listener {}", listener.getClass(), ex);
                }
            }
        }
    }
}
