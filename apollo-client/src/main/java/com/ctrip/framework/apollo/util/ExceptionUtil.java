package com.ctrip.framework.apollo.util;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * 异常工具类
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ExceptionUtil {
    /**
     * 组装细节消息通过异常栈，最多10个原因
     *
     * @param ex 异常
     * @return 异常原因消息
     */
    public static String getDetailMessage(Throwable ex) {
        if (ex == null
                || Strings.isNullOrEmpty(ex.getMessage())) {
            return "";
        }
        StringBuilder builder = new StringBuilder(ex.getMessage());
        List<Throwable> causes = Lists.newLinkedList();

        int counter = 0;
        Throwable current = ex;
        // 最多获取10个原因
        while (current.getCause() != null && counter < 10) {
            Throwable next = current.getCause();
            causes.add(next);
            current = next;
            counter++;
        }

        for (Throwable cause : causes) {
            if (Strings.isNullOrEmpty(cause.getMessage())) {
                counter--;
                continue;
            }
            builder.append(" [Cause: ").append(cause.getMessage());
        }

        builder.append(Strings.repeat("]", counter));

        return builder.toString();
    }
}
