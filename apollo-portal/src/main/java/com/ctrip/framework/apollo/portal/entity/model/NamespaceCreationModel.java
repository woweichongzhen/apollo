package com.ctrip.framework.apollo.portal.entity.model;

import com.ctrip.framework.apollo.common.dto.NamespaceDTO;

/**
 * 命名空间创建model
 */
public class NamespaceCreationModel {

    /**
     * 环境
     */
    private String env;

    /**
     * 命名空间dto
     */
    private NamespaceDTO namespace;

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public NamespaceDTO getNamespace() {
        return namespace;
    }

    public void setNamespace(NamespaceDTO namespace) {
        this.namespace = namespace;
    }
}
