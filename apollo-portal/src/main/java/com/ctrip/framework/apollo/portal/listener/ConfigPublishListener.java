package com.ctrip.framework.apollo.portal.listener;

import com.ctrip.framework.apollo.common.constants.ReleaseOperation;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.component.emailbuilder.GrayPublishEmailBuilder;
import com.ctrip.framework.apollo.portal.component.emailbuilder.MergeEmailBuilder;
import com.ctrip.framework.apollo.portal.component.emailbuilder.NormalPublishEmailBuilder;
import com.ctrip.framework.apollo.portal.component.emailbuilder.RollbackEmailBuilder;
import com.ctrip.framework.apollo.portal.entity.bo.Email;
import com.ctrip.framework.apollo.portal.entity.bo.ReleaseHistoryBO;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.service.ReleaseHistoryService;
import com.ctrip.framework.apollo.portal.spi.EmailService;
import com.ctrip.framework.apollo.portal.spi.MQService;
import com.ctrip.framework.apollo.tracer.Tracer;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 配置发布监听器
 */
@Component
public class ConfigPublishListener {

    private final ReleaseHistoryService releaseHistoryService;
    private final EmailService emailService;
    private final NormalPublishEmailBuilder normalPublishEmailBuilder;
    private final GrayPublishEmailBuilder grayPublishEmailBuilder;
    private final RollbackEmailBuilder rollbackEmailBuilder;
    private final MergeEmailBuilder mergeEmailBuilder;
    private final PortalConfig portalConfig;
    private final MQService mqService;

    private ExecutorService executorService;

    public ConfigPublishListener(
            final ReleaseHistoryService releaseHistoryService,
            final EmailService emailService,
            final NormalPublishEmailBuilder normalPublishEmailBuilder,
            final GrayPublishEmailBuilder grayPublishEmailBuilder,
            final RollbackEmailBuilder rollbackEmailBuilder,
            final MergeEmailBuilder mergeEmailBuilder,
            final PortalConfig portalConfig,
            final MQService mqService) {
        this.releaseHistoryService = releaseHistoryService;
        this.emailService = emailService;
        this.normalPublishEmailBuilder = normalPublishEmailBuilder;
        this.grayPublishEmailBuilder = grayPublishEmailBuilder;
        this.rollbackEmailBuilder = rollbackEmailBuilder;
        this.mergeEmailBuilder = mergeEmailBuilder;
        this.portalConfig = portalConfig;
        this.mqService = mqService;
    }

    @PostConstruct
    public void init() {
        // 创建单任务线程池
        executorService = Executors.newSingleThreadExecutor(
                ApolloThreadFactory.create("ConfigPublishNotify", true));
    }

    /**
     * 事件监听器，监听到时间后，异步处理
     *
     * @param event 配置发布事件
     */
    @EventListener
    public void onConfigPublish(ConfigPublishEvent event) {
        executorService.submit(new ConfigPublishNotifyTask(event.getConfigPublishInfo()));
    }

    /**
     * 配置发布事件
     */
    private class ConfigPublishNotifyTask implements Runnable {

        /**
         * 配置发布信息
         */
        private final ConfigPublishEvent.ConfigPublishInfo publishInfo;

        ConfigPublishNotifyTask(ConfigPublishEvent.ConfigPublishInfo publishInfo) {
            this.publishInfo = publishInfo;
        }

        @Override
        public void run() {
            // 根据发布类型获取对应的发布历史
            ReleaseHistoryBO releaseHistory = getReleaseHistory();
            if (releaseHistory == null) {
                Tracer.logError("Load release history failed", null);
                return;
            }

            // 发送发布邮件
            sendPublishEmail(releaseHistory);

            // 发送发布消息
            sendPublishMsg(releaseHistory);
        }

        /**
         * 获取发布历史
         *
         * @return 发布历史
         */
        private ReleaseHistoryBO getReleaseHistory() {
            Env env = publishInfo.getEnv();

            int operation = publishInfo.isMergeEvent()
                    // 合并发布判断
                    ? ReleaseOperation.GRAY_RELEASE_MERGE_TO_MASTER
                    // 回滚发布判断
                    : publishInfo.isRollbackEvent()
                    ? ReleaseOperation.ROLLBACK
                    // 主干发布判断
                    : publishInfo.isNormalPublishEvent()
                    ? ReleaseOperation.NORMAL_RELEASE
                    : publishInfo.isGrayPublishEvent()
                    // 灰度发布判断
                    ? ReleaseOperation.GRAY_RELEASE
                    : -1;

            // 操作不符
            if (operation == -1) {
                return null;
            }

            if (publishInfo.isRollbackEvent()) {
                // 回滚事件查找上一次发布历史bo
                return releaseHistoryService.findLatestByPreviousReleaseIdAndOperation(
                        env,
                        publishInfo.getPreviousReleaseId(),
                        operation);
            }
            // 非回滚查找最后一次发布历史bo
            return releaseHistoryService.findLatestByReleaseIdAndOperation(env, publishInfo.getReleaseId(), operation);
        }

        /**
         * 发送发布邮件
         *
         * @param releaseHistory 发布历史
         */
        private void sendPublishEmail(ReleaseHistoryBO releaseHistory) {
            Env env = publishInfo.getEnv();

            // 支持的邮件环境是否包括该环境
            if (!portalConfig.emailSupportedEnvs().contains(env)) {
                return;
            }

            // 发送邮件
            int realOperation = releaseHistory.getOperation();
            Email email = null;
            try {
                email = buildEmail(env, releaseHistory, realOperation);
            } catch (Throwable e) {
                Tracer.logError("build email failed.", e);
            }

            // 发送邮件
            if (email != null) {
                emailService.send(email);
            }
        }

        /**
         * 发送发布消息
         *
         * @param releaseHistory 发布历史
         */
        private void sendPublishMsg(ReleaseHistoryBO releaseHistory) {
            mqService.sendPublishMsg(publishInfo.getEnv(), releaseHistory);
        }

        /**
         * 构建邮件发送
         *
         * @param env            环境
         * @param releaseHistory 发布历史
         * @param operation      操作者
         * @return 邮件
         */
        private Email buildEmail(Env env, ReleaseHistoryBO releaseHistory, int operation) {
            switch (operation) {
                case ReleaseOperation.GRAY_RELEASE: {
                    return grayPublishEmailBuilder.build(env, releaseHistory);
                }
                case ReleaseOperation.NORMAL_RELEASE: {
                    return normalPublishEmailBuilder.build(env, releaseHistory);
                }
                case ReleaseOperation.ROLLBACK: {
                    return rollbackEmailBuilder.build(env, releaseHistory);
                }
                case ReleaseOperation.GRAY_RELEASE_MERGE_TO_MASTER: {
                    return mergeEmailBuilder.build(env, releaseHistory);
                }
                default:
                    return null;
            }
        }
    }

}
