package com.ctrip.framework.apollo.portal.listener;

import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.google.common.base.Preconditions;
import org.springframework.context.ApplicationEvent;

/**
 * 应用命名空间创建时间
 */
public class AppNamespaceCreationEvent extends ApplicationEvent {

    public AppNamespaceCreationEvent(Object source) {
        super(source);
    }

    public AppNamespace getAppNamespace() {
        Preconditions.checkState(source != null);
        return (AppNamespace) this.source;
    }
}
