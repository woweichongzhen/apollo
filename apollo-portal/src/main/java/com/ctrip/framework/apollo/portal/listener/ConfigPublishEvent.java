package com.ctrip.framework.apollo.portal.listener;

import com.ctrip.framework.apollo.portal.environment.Env;
import org.springframework.context.ApplicationEvent;

/**
 * 配置发布事件
 */
public class ConfigPublishEvent extends ApplicationEvent {

    /**
     * 配置发布元信息
     */
    private ConfigPublishInfo configPublishInfo;

    public ConfigPublishEvent(Object source) {
        super(source);
        configPublishInfo = (ConfigPublishInfo) source;
    }

    /**
     * 创建配置发布事件对象
     *
     * @return 配置发布事件对象
     */
    public static ConfigPublishEvent instance() {
        ConfigPublishInfo info = new ConfigPublishInfo();
        return new ConfigPublishEvent(info);
    }

    public ConfigPublishInfo getConfigPublishInfo() {
        return configPublishInfo;
    }

    public ConfigPublishEvent withAppId(String appId) {
        configPublishInfo.setAppId(appId);
        return this;
    }

    public ConfigPublishEvent withCluster(String clusterName) {
        configPublishInfo.setClusterName(clusterName);
        return this;
    }

    public ConfigPublishEvent withNamespace(String namespaceName) {
        configPublishInfo.setNamespaceName(namespaceName);
        return this;
    }

    public ConfigPublishEvent withReleaseId(long releaseId) {
        configPublishInfo.setReleaseId(releaseId);
        return this;
    }

    public ConfigPublishEvent withPreviousReleaseId(long previousReleaseId) {
        configPublishInfo.setPreviousReleaseId(previousReleaseId);
        return this;
    }

    public ConfigPublishEvent setNormalPublishEvent(boolean isNormalPublishEvent) {
        configPublishInfo.setNormalPublishEvent(isNormalPublishEvent);
        return this;
    }

    public ConfigPublishEvent setGrayPublishEvent(boolean isGrayPublishEvent) {
        configPublishInfo.setGrayPublishEvent(isGrayPublishEvent);
        return this;
    }

    public ConfigPublishEvent setRollbackEvent(boolean isRollbackEvent) {
        configPublishInfo.setRollbackEvent(isRollbackEvent);
        return this;
    }

    public ConfigPublishEvent setMergeEvent(boolean isMergeEvent) {
        configPublishInfo.setMergeEvent(isMergeEvent);
        return this;
    }

    public ConfigPublishEvent setEnv(Env env) {
        configPublishInfo.setEnv(env);
        return this;
    }

    /**
     * 配置发布信息
     */
    public static class ConfigPublishInfo {

        private String env;
        private String appId;
        private String clusterName;
        private String namespaceName;

        /**
         * 本次发布id
         */
        private long releaseId;

        /**
         * 上一次发布id
         */
        private long previousReleaseId;

        /**
         * 是否为回滚事件
         */
        private boolean isRollbackEvent;

        /**
         * 是否为合并事件
         */
        private boolean isMergeEvent;

        /**
         * 是否为主干发布事件
         */
        private boolean isNormalPublishEvent;

        /**
         * 是否为灰度发布事件
         */
        private boolean isGrayPublishEvent;

        public Env getEnv() {
            return Env.valueOf(env);
        }

        public void setEnv(Env env) {
            this.env = env.toString();
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getClusterName() {
            return clusterName;
        }

        public void setClusterName(String clusterName) {
            this.clusterName = clusterName;
        }

        public String getNamespaceName() {
            return namespaceName;
        }

        public void setNamespaceName(String namespaceName) {
            this.namespaceName = namespaceName;
        }

        public long getReleaseId() {
            return releaseId;
        }

        public void setReleaseId(long releaseId) {
            this.releaseId = releaseId;
        }

        public long getPreviousReleaseId() {
            return previousReleaseId;
        }

        public void setPreviousReleaseId(long previousReleaseId) {
            this.previousReleaseId = previousReleaseId;
        }

        public boolean isRollbackEvent() {
            return isRollbackEvent;
        }

        public void setRollbackEvent(boolean rollbackEvent) {
            isRollbackEvent = rollbackEvent;
        }

        public boolean isMergeEvent() {
            return isMergeEvent;
        }

        public void setMergeEvent(boolean mergeEvent) {
            isMergeEvent = mergeEvent;
        }

        public boolean isNormalPublishEvent() {
            return isNormalPublishEvent;
        }

        public void setNormalPublishEvent(boolean normalPublishEvent) {
            isNormalPublishEvent = normalPublishEvent;
        }

        public boolean isGrayPublishEvent() {
            return isGrayPublishEvent;
        }

        public void setGrayPublishEvent(boolean grayPublishEvent) {
            isGrayPublishEvent = grayPublishEvent;
        }
    }
}
