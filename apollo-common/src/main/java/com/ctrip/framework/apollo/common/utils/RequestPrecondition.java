package com.ctrip.framework.apollo.common.utils;

import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.core.utils.StringUtils;

/**
 * 请求校验
 */
public class RequestPrecondition {

    /**
     * 包含空参数
     */
    private static String CONTAIN_EMPTY_ARGUMENT = "request payload should not be contain empty.";

    /**
     * 400异常默认抛出信息
     */
    private static final String ILLEGAL_MODEL = "request model is invalid";

    /**
     * 校验参数非空
     *
     * @param args 参数集合
     */
    public static void checkArgumentsNotEmpty(String... args) {
        checkArguments(!StringUtils.isContainEmpty(args), CONTAIN_EMPTY_ARGUMENT);
    }

    /**
     * 校验
     *
     * @param valid 是否校验通过，需要抛出异常
     *              true不抛出
     *              false抛出
     */
    public static void checkModel(boolean valid) {
        checkArguments(valid, ILLEGAL_MODEL);
    }

    /**
     * 检查参数，不通过抛出异常
     *
     * @param expression   是否抛出400异常，true不抛出，false抛出
     * @param errorMessage 错误信息
     */
    public static void checkArguments(boolean expression, Object errorMessage) {
        if (!expression) {
            throw new BadRequestException(String.valueOf(errorMessage));
        }
    }
}
