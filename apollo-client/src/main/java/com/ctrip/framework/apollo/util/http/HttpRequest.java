package com.ctrip.framework.apollo.util.http;

import java.util.Map;

/**
 * http请求
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class HttpRequest {

    private final String url;

    private Map<String, String> headers;

    private int connectTimeout;

    private int readTimeout;

    /**
     * 创建请求url
     *
     * @param url url
     */
    public HttpRequest(String url) {
        this.url = url;
        connectTimeout = -1;
        readTimeout = -1;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
}
