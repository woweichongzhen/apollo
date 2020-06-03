package com.ctrip.framework.apollo.openapi.util;

import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.openapi.entity.ConsumerAudit;
import com.ctrip.framework.apollo.openapi.service.ConsumerService;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 第三方应用审计工具类
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Service
public class ConsumerAuditUtil implements InitializingBean {

    /**
     * 最大第三方应用阻塞审计大笑
     */
    private static final int CONSUMER_AUDIT_MAX_SIZE = 10000;

    /**
     * 审计阻塞队列，最大10000条
     */
    private final BlockingQueue<ConsumerAudit> audits = Queues.newLinkedBlockingQueue(CONSUMER_AUDIT_MAX_SIZE);

    /**
     * 审计线程池
     */
    private final ExecutorService auditExecutorService;

    /**
     * 审计任务是否停止
     */
    private final AtomicBoolean auditStopped;

    /**
     * 批量插入大小
     */
    private final int BATCH_SIZE = 100;

    /**
     * 批量读取等待超时时间
     */
    private final long BATCH_TIMEOUT = 5;

    /**
     * 批量读取等待超时单位
     */
    private final TimeUnit BATCH_TIMEUNIT = TimeUnit.SECONDS;

    private final ConsumerService consumerService;

    public ConsumerAuditUtil(final ConsumerService consumerService) {
        this.consumerService = consumerService;
        auditExecutorService = Executors.newSingleThreadExecutor(
                ApolloThreadFactory.create("ConsumerAuditUtil", true));
        auditStopped = new AtomicBoolean(false);
    }

    /**
     * 审计
     *
     * @param request    请求
     * @param consumerId 应用id
     * @return true添加成功
     */
    public boolean audit(HttpServletRequest request, long consumerId) {
        // get请求无需审计
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 获取uri和查询参数
        String uri = request.getRequestURI();
        if (!Strings.isNullOrEmpty(request.getQueryString())) {
            uri += "?" + request.getQueryString();
        }

        // 插入审计
        ConsumerAudit consumerAudit = new ConsumerAudit();
        Date now = new Date();
        consumerAudit.setConsumerId(consumerId);
        consumerAudit.setUri(uri);
        consumerAudit.setMethod(request.getMethod());
        consumerAudit.setDataChangeCreatedTime(now);
        consumerAudit.setDataChangeLastModifiedTime(now);

        // 如果超过最大大小，则放弃审核
        return audits.offer(consumerAudit);
    }

    @Override
    public void afterPropertiesSet() {
        auditExecutorService.submit(() -> {
            while (!auditStopped.get() && !Thread.currentThread().isInterrupted()) {
                List<ConsumerAudit> toAudit = Lists.newArrayList();
                try {
                    // 从阻塞队列拿取任务，直到达到上限100条，或超时5S
                    Queues.drain(audits, toAudit, BATCH_SIZE, BATCH_TIMEOUT, BATCH_TIMEUNIT);

                    // 批量插入
                    if (!toAudit.isEmpty()) {
                        consumerService.createConsumerAudits(toAudit);
                    }
                } catch (Throwable ex) {
                    Tracer.logError(ex);
                }
            }
        });
    }

    /**
     * 停止审计定时任务
     */
    public void stopAudit() {
        auditStopped.set(true);
    }
}
