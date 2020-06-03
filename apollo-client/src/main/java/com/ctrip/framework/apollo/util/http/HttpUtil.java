package com.ctrip.framework.apollo.util.http;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.exceptions.ApolloConfigStatusCodeException;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.google.common.base.Function;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * http工具类
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class HttpUtil {

    private final ConfigUtil configUtil;

    private final Gson gson;

    public HttpUtil() {
        configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        gson = new Gson();
    }

    /**
     * get请求
     *
     * @param httpRequest  请求
     * @param responseType 返回类型
     * @return 序列化后的返回
     * @throws ApolloConfigException 异常发生或返回码非200和304
     */
    public <T> HttpResponse<T> doGet(HttpRequest httpRequest, final Class<T> responseType) {
        Function<String, T> convertResponse = new Function<String, T>() {
            @Override
            public T apply(String input) {
                return gson.fromJson(input, responseType);
            }
        };

        return doGetWithSerializeFunction(httpRequest, convertResponse);
    }

    /**
     * Do get operation for the http request.
     *
     * @param httpRequest  the request
     * @param responseType the response type
     * @return the response
     * @throws ApolloConfigException if any error happened or response code is neither 200 nor 304
     */
    public <T> HttpResponse<T> doGet(HttpRequest httpRequest, final Type responseType) {
        Function<String, T> convertResponse = new Function<String, T>() {
            @Override
            public T apply(String input) {
                return gson.fromJson(input, responseType);
            }
        };

        return doGetWithSerializeFunction(httpRequest, convertResponse);
    }

    /**
     * 执行get请求并序列化
     *
     * @param httpRequest       http请求
     * @param serializeFunction 序列化函数
     * @param <T>               序列化类型
     * @return http返回
     */
    private <T> HttpResponse<T> doGetWithSerializeFunction(HttpRequest httpRequest,
                                                           Function<String, T> serializeFunction) {
        InputStreamReader isr = null;
        InputStreamReader esr = null;
        int statusCode;
        try {
            // 开启http连接
            HttpURLConnection conn = (HttpURLConnection) new URL(httpRequest.getUrl()).openConnection();

            // 设置get请求
            conn.setRequestMethod("GET");

            // 组装请求头
            Map<String, String> headers = httpRequest.getHeaders();
            if (headers != null && headers.size() > 0) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            // 设置连接超时时间 默认1S
            int connectTimeout = httpRequest.getConnectTimeout();
            if (connectTimeout < 0) {
                connectTimeout = configUtil.getConnectTimeout();
            }

            // 设置读超时时间 5S
            int readTimeout = httpRequest.getReadTimeout();
            if (readTimeout < 0) {
                readTimeout = configUtil.getReadTimeout();
            }

            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);

            // 开启连接
            conn.connect();

            // http返回码
            statusCode = conn.getResponseCode();

            // 返回流转字符串
            String response;
            try {
                isr = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
                response = CharStreams.toString(isr);
            } catch (IOException ex) {
                // https://docs.oracle.com/javase/7/docs/technotes/guides/net/http-keepalive.html
                // 如果出现错误，获取错误流并使用，以进行连接复用
                InputStream errorStream = conn.getErrorStream();

                if (errorStream != null) {
                    esr = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                    try {
                        CharStreams.toString(esr);
                    } catch (IOException ioe) {
                        //ignore
                    }
                }

                // 200和304直接抛出原始异常
                if (statusCode == 200 || statusCode == 304) {
                    throw ex;
                }

                // 其他异常，比如404 ，当获取io流时的io异常，包装抛出
                throw new ApolloConfigStatusCodeException(statusCode, ex);
            }

            // 200返回码
            if (statusCode == 200) {
                return new HttpResponse<>(statusCode, serializeFunction.apply(response));
            }

            // 304返回码
            if (statusCode == 304) {
                return new HttpResponse<>(statusCode, null);
            }
        } catch (ApolloConfigStatusCodeException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new ApolloConfigException("Could not complete get operation", ex);
        } finally {
            if (isr != null) {
                try {
                    isr.close();
                } catch (IOException ex) {
                    // ignore
                }
            }

            if (esr != null) {
                try {
                    esr.close();
                } catch (IOException ex) {
                    // ignore
                }
            }
        }

        throw new ApolloConfigStatusCodeException(statusCode,
                String.format("Get operation failed for %s", httpRequest.getUrl()));
    }

}
