package com.ctrip.framework.apollo.portal.component;


import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.portal.api.AdminServiceAPI;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.environment.PortalMetaDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 前端设置
 */
@Component
public class PortalSettings {

    private static final Logger logger = LoggerFactory.getLogger(PortalSettings.class);

    /**
     * 健康检查周期
     */
    private static final int HEALTH_CHECK_INTERVAL = 10 * 1000;

    private final ApplicationContext applicationContext;

    private final PortalConfig portalConfig;

    private final PortalMetaDomainService portalMetaDomainService;

    /**
     * 所有环境
     */
    private List<Env> allEnvs = new ArrayList<>();

    /**
     * 标记环境是否在线
     */
    private final Map<Env, Boolean> envStatusMark = new ConcurrentHashMap<>();

    public PortalSettings(
            final ApplicationContext applicationContext,
            final PortalConfig portalConfig,
            final PortalMetaDomainService portalMetaDomainService
    ) {
        this.applicationContext = applicationContext;
        this.portalConfig = portalConfig;
        this.portalMetaDomainService = portalMetaDomainService;
    }

    @PostConstruct
    private void postConstruct() {
        // 获取支持的环境
        allEnvs = portalConfig.portalSupportedEnvs();

        // 默认标记所有环境在线
        for (Env env : allEnvs) {
            envStatusMark.put(env, true);
        }

        // 健康检查定时任务提交，延迟1S，间隔10S
        ScheduledExecutorService healthCheckService = Executors.newScheduledThreadPool(
                1, ApolloThreadFactory.create("EnvHealthChecker", true));
        healthCheckService.scheduleWithFixedDelay(
                new HealthCheckTask(applicationContext),
                1000,
                HEALTH_CHECK_INTERVAL,
                TimeUnit.MILLISECONDS);

    }

    /**
     * 获取所有支持的环境
     *
     * @return 环境集合
     */
    public List<Env> getAllEnvs() {
        return allEnvs;
    }

    /**
     * 获取健康的环境
     *
     * @return 健康的环境
     */
    public List<Env> getActiveEnvs() {
        List<Env> activeEnvs = new LinkedList<>();
        for (Env env : allEnvs) {
            if (envStatusMark.get(env)) {
                activeEnvs.add(env);
            }
        }
        return activeEnvs;
    }

    /**
     * 判断环境是否健康
     *
     * @param env 环境
     * @return true健康，false不健康
     */
    public boolean isEnvActive(Env env) {
        Boolean mark = envStatusMark.get(env);
        return mark == null ? false : mark;
    }

    /**
     * 健康检查定时任务
     */
    private class HealthCheckTask implements Runnable {

        /**
         * 环境下线阈值
         */
        private static final int ENV_DOWN_THRESHOLD = 2;

        /**
         * 环境检查失败计数器
         */
        private final Map<Env, Integer> healthCheckFailedCounter = new HashMap<>();

        /**
         * admin服务健康检查api
         */
        private final AdminServiceAPI.HealthAPI healthAPI;

        public HealthCheckTask(ApplicationContext context) {
            healthAPI = context.getBean(AdminServiceAPI.HealthAPI.class);
            for (Env env : allEnvs) {
                healthCheckFailedCounter.put(env, 0);
            }
        }

        @Override
        public void run() {
            // 遍历所有环境
            for (Env env : allEnvs) {
                try {
                    if (isUp(env)) {
                        // 如果在线，把map中标记为true，计数器重置为0
                        if (!envStatusMark.get(env)) {
                            envStatusMark.put(env, true);
                            healthCheckFailedCounter.put(env, 0);
                            logger.info("Env revived because env health check success. env: {}", env);
                        }
                    } else {
                        logger.error("Env health check failed, maybe because of admin server down. env: {}, meta " +
                                        "server address: {}", env,
                                portalMetaDomainService.getDomain(env));
                        // 如果不在线，处理环境下线
                        handleEnvDown(env);
                    }

                } catch (Exception e) {
                    logger.error("Env health check failed, maybe because of meta server down "
                                    + "or configure wrong meta server address. env: {}, meta server address: {}", env,
                            portalMetaDomainService.getDomain(env), e);
                    // 发生异常，处理环境下线
                    handleEnvDown(env);
                }
            }

        }

        /**
         * 发送http请求，判断返回状态是否为UP
         *
         * @param env 环境
         * @return true为在线，false异常
         */
        private boolean isUp(Env env) {
            Health health = healthAPI.health(env);
            return "UP".equals(health.getStatus().getCode());
        }

        /**
         * 处理环境下线
         *
         * @param env 环境
         */
        private void handleEnvDown(Env env) {
            // 更新失败次数
            int failedTimes = healthCheckFailedCounter.get(env);
            healthCheckFailedCounter.put(env, ++failedTimes);

            if (!envStatusMark.get(env)) {
                // 如果该环境已经下线，打印日志
                logger.error("Env is down. env: {}, failed times: {}, meta server address: {}", env, failedTimes,
                        portalMetaDomainService.getDomain(env));
            } else {
                // 如果环境仍然在线，判断是否失败次数达到阈值2次，如果达到标记为false，否则打印异常日志
                if (failedTimes >= ENV_DOWN_THRESHOLD) {
                    envStatusMark.put(env, false);
                    logger.error("Env is down because health check failed for {} times, "
                                    + "which equals to down threshold. env: {}, meta server address: {}",
                            ENV_DOWN_THRESHOLD, env,
                            portalMetaDomainService.getDomain(env));
                } else {
                    logger.error(
                            "Env health check failed for {} times which less than down threshold. down threshold:{}, " +
                                    "env: {}, meta server address: {}",
                            failedTimes, ENV_DOWN_THRESHOLD, env, portalMetaDomainService.getDomain(env));
                }
            }

        }

    }
}
