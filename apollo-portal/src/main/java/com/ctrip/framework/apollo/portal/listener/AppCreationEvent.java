package com.ctrip.framework.apollo.portal.listener;

import com.ctrip.framework.apollo.common.entity.App;
import com.google.common.base.Preconditions;
import org.springframework.context.ApplicationEvent;

/**
 * 应用创建事件
 */
public class AppCreationEvent extends ApplicationEvent {

    public AppCreationEvent(Object source) {
        super(source);
    }

    /**
     * 获取应用对象
     *
     * @return 应用对象
     */
    public App getApp() {
        Preconditions.checkState(source != null);
        return (App) this.source;
    }
}
