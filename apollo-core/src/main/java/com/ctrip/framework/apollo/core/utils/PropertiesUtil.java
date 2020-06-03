package com.ctrip.framework.apollo.core.utils;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Properties;

/**
 * 属性工具
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class PropertiesUtil {
    /**
     * 转换属性对象为字符串格式
     *
     * @param properties 属性对象
     * @return 属性包含的所有内容
     * @throws IOException 转换io异常
     */
    public static String toString(Properties properties) throws IOException {
        // 输出流
        StringWriter writer = new StringWriter();
        properties.store(writer, null);

        // 获取输出流的StringBuffer
        StringBuffer stringBuffer = writer.getBuffer();

        // 过滤自动添加的注释行
        filterPropertiesComment(stringBuffer);
        return stringBuffer.toString();
    }

    /**
     * 过滤第一行注释（自动添加的）
     * {@link Properties#store(Writer, String)} store方法会自动在第一行加上时间戳注释
     *
     * @param stringBuffer the string buffer
     * @return true过滤成功
     */
    static boolean filterPropertiesComment(StringBuffer stringBuffer) {
        // 检查第一行是否包含注释
        if (stringBuffer.charAt(0) != '#') {
            return false;
        }
        // 包含根据换行符，把第一注释行删掉
        int commentLineIndex = stringBuffer.indexOf("\n");
        if (commentLineIndex == -1) {
            return false;
        }
        stringBuffer.delete(0, commentLineIndex + 1);
        return true;
    }
}
