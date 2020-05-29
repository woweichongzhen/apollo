package com.ctrip.framework.apollo.portal.api;

import com.ctrip.framework.apollo.portal.component.RetryableRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * api调用，封装可重试的rest模板
 */
public abstract class API {

    @Autowired
    protected RetryableRestTemplate restTemplate;

}
