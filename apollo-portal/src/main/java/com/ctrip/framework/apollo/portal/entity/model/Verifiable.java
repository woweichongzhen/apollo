package com.ctrip.framework.apollo.portal.entity.model;

/**
 * 验证接口
 */
public interface Verifiable {

    /**
     * 是否验证通过
     *
     * @return true验证不通过，false通过
     */
    boolean isInvalid();

}
