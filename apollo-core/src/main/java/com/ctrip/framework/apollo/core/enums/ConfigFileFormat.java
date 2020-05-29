package com.ctrip.framework.apollo.core.enums;

import com.ctrip.framework.apollo.core.utils.StringUtils;

/**
 * 配置文件格式
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public enum ConfigFileFormat {

    /**
     * 属性文件
     */
    Properties("properties"),

    /**
     * xml文件
     */
    XML("xml"),

    /**
     * json文件
     */
    JSON("json"),

    /**
     * yml文件
     */
    YML("yml"),

    /**
     * yaml文件
     */
    YAML("yaml"),

    /**
     * txt文件
     */
    TXT("txt");

    /**
     * 值
     */
    private final String value;

    ConfigFileFormat(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 转换value为枚举
     *
     * @param value 值
     * @return 枚举
     */
    public static ConfigFileFormat fromString(String value) {
        if (StringUtils.isEmpty(value)) {
            throw new IllegalArgumentException("value can not be empty");
        }
        switch (value.toLowerCase()) {
            case "properties":
                return Properties;
            case "xml":
                return XML;
            case "json":
                return JSON;
            case "yml":
                return YML;
            case "yaml":
                return YAML;
            case "txt":
                return TXT;
            default:
                break;
        }
        throw new IllegalArgumentException(value + " can not map enum");
    }

    /**
     * 校验value是否正确
     *
     * @param value value
     * @return true正确，falsue不正确
     */
    public static boolean isValidFormat(String value) {
        try {
            fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 是否是兼容属性
     *
     * @param format 格式
     * @return yaml或yml返回true，否则false
     */
    public static boolean isPropertiesCompatible(ConfigFileFormat format) {
        return format == YAML || format == YML;
    }
}
