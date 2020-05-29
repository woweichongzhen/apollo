package com.ctrip.framework.apollo.common.utils;

import com.ctrip.framework.apollo.core.utils.StringUtils;

import java.util.regex.Pattern;

/**
 * 输入校验
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class InputValidator {

    /**
     * 校验名称message
     */
    public static final String INVALID_CLUSTER_NAMESPACE_MESSAGE = "Only digits, alphabets and symbol - _ . are " +
            "allowed";

    /**
     * 尾缀信息
     */
    public static final String INVALID_NAMESPACE_NAMESPACE_MESSAGE = "not allowed to end with .json, .yml, .yaml, " +
            ".xml, .properties";

    /**
     * 名称校验
     */
    public static final String CLUSTER_NAMESPACE_VALIDATOR = "[0-9a-zA-Z_.-]+";

    /**
     * 应用命名空间格式校验
     */
    private static final String APP_NAMESPACE_VALIDATOR = "[a-zA-Z0-9._-]+(?<!\\.(json|yml|yaml|xml|properties))$";
    private static final Pattern CLUSTER_NAMESPACE_PATTERN = Pattern.compile(CLUSTER_NAMESPACE_VALIDATOR);

    /**
     * 应用命名空间格式校验Pattern
     */
    private static final Pattern APP_NAMESPACE_PATTERN = Pattern.compile(APP_NAMESPACE_VALIDATOR);

    /**
     * 校验集群名称是否符合输入
     *
     * @param name 集群名称
     * @return true符合输入，false不符合
     */
    public static boolean isValidClusterNamespace(String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        return CLUSTER_NAMESPACE_PATTERN.matcher(name).matches();
    }

    /**
     * 校验应用命名空间名称
     *
     * @param name 应用命名空间名称
     * @return true校验成功，false校验不成功
     */
    public static boolean isValidAppNamespace(String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        return APP_NAMESPACE_PATTERN.matcher(name).matches();
    }
}
