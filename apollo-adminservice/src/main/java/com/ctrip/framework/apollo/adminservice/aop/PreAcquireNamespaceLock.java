package com.ctrip.framework.apollo.adminservice.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 锁注解
 * 标识方法需要获取到namespace的lock才能执行
 * 一般标识在项修改接口上
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreAcquireNamespaceLock {

}
