package com.ctrip.framework.apollo.util.http;

/**
 * http请求返回
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class HttpResponse<T> {

    private final int statusCode;

    private final T body;

    public HttpResponse(int statusCode, T body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public T getBody() {
        return body;
    }
}
