package com.ctrip.framework.apollo.util.parser;

/**
 * 解析异常
 */
public class ParserException extends Exception {
    public ParserException(String message) {
        super(message);
    }

    public ParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
