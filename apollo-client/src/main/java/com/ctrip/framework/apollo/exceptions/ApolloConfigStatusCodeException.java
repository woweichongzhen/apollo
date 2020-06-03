package com.ctrip.framework.apollo.exceptions;

/**
 * apollo配置状态码异常，http请求失败时传入状态码和消息
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ApolloConfigStatusCodeException extends RuntimeException {

    private final int statusCode;

    public ApolloConfigStatusCodeException(int statusCode, String message) {
        super(String.format("[status code: %d] %s", statusCode, message));
        this.statusCode = statusCode;
    }

    public ApolloConfigStatusCodeException(int statusCode, Throwable cause) {
        super(cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
