package com.ctrip.framework.apollo.biz.message;

import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;
import com.ctrip.framework.apollo.biz.repository.ReleaseMessageRepository;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.google.common.collect.Queues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 数据库消息发送实现
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Component
public class DatabaseMessageSender implements MessageSender {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseMessageSender.class);

    /**
     * 清理无用message队列最大容量
     */
    private static final int CLEAN_QUEUE_MAX_SIZE = 100;

    /**
     * 待清理的队列
     */
    private final BlockingQueue<Long> toClean = Queues.newLinkedBlockingQueue(CLEAN_QUEUE_MAX_SIZE);

    /**
     * 清理消息的线程池
     */
    private final ExecutorService cleanExecutorService;

    /**
     * 是否停止清理message
     */
    private final AtomicBoolean cleanStopped;

    private final ReleaseMessageRepository releaseMessageRepository;

    public DatabaseMessageSender(final ReleaseMessageRepository releaseMessageRepository) {
        cleanExecutorService = Executors.newSingleThreadExecutor(
                ApolloThreadFactory.create("DatabaseMessageSender",
                        true));
        cleanStopped = new AtomicBoolean(false);
        this.releaseMessageRepository = releaseMessageRepository;
    }

    @Override
    @Transactional
    public void sendMessage(String message, String channel) {
        logger.info("Sending message {} to channel {}", message, channel);
        // 通道不符，直接返回
        if (!Objects.equals(channel, Topics.APOLLO_RELEASE_TOPIC)) {
            logger.warn("Channel {} not supported by DatabaseMessageSender!", channel);
            return;
        }

        Tracer.logEvent("Apollo.AdminService.ReleaseMessage", message);
        Transaction transaction = Tracer.newTransaction("Apollo.AdminService", "sendMessage");
        try {
            // 保存新消息
            ReleaseMessage newMessage = releaseMessageRepository.save(new ReleaseMessage(message));
            // 清理新消息之前的id，如果队列已满，添加直接失败，不阻塞等待
            toClean.offer(newMessage.getId());
            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            logger.error("Sending message to database failed", ex);
            transaction.setStatus(ex);
            throw ex;
        } finally {
            transaction.complete();
        }
    }

    /**
     * 初始化清理任务
     */
    @PostConstruct
    private void initialize() {
        cleanExecutorService.submit(() -> {
            // 如果未标识停止，并且线程未终端，则一直执行
            while (!cleanStopped.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // 阻塞1S，取出待清理之前的消息id
                    Long rm = toClean.poll(1, TimeUnit.SECONDS);
                    if (rm != null) {
                        cleanMessage(rm);
                    } else {
                        // 不存在，sleep一段时间
                        TimeUnit.SECONDS.sleep(5);
                    }
                } catch (Throwable ex) {
                    Tracer.logError(ex);
                }
            }
        });
    }

    /**
     * 清理小于id的同类型的消息
     *
     * @param id 消息id
     */
    private void cleanMessage(Long id) {
        boolean hasMore = true;
        // 仔细检查发布消息是否回滚
        // 获取最新的消息id，避免消息已删除
        // 因为 DatabaseMessageSender 会在多进程中执行。例如：
        // 1）Config Service + Admin Service ；
        // 2）N * Config Service ；
        // 3）N * Admin Service
        ReleaseMessage releaseMessage = releaseMessageRepository.findById(id).orElse(null);
        if (releaseMessage == null) {
            return;
        }

        // 循环删除相同message内容的老消息
        while (hasMore && !Thread.currentThread().isInterrupted()) {
            // 删除这100条消息
            List<ReleaseMessage> messages = releaseMessageRepository.findFirst100ByMessageAndIdLessThanOrderByIdAsc(
                    releaseMessage.getMessage(), releaseMessage.getId());
            releaseMessageRepository.deleteAll(messages);
            // 如果消息数量不足100，说明没有更多的老消息了
            hasMore = messages.size() == 100;

            messages.forEach(toRemove -> Tracer.logEvent(
                    String.format("ReleaseMessage.Clean.%s", toRemove.getMessage()), String.valueOf(toRemove.getId())));
        }
    }

    /**
     * 停止清理
     */
    void stopClean() {
        cleanStopped.set(true);
    }
}
