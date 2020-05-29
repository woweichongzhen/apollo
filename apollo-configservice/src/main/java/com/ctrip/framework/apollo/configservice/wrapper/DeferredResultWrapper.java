package com.ctrip.framework.apollo.configservice.wrapper;

import com.ctrip.framework.apollo.core.dto.ApolloConfigNotification;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.Map;

/**
 * 延迟结果包装
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class DeferredResultWrapper implements Comparable<DeferredResultWrapper> {

    /**
     * 返回结果默认未修改过，304
     */
    private static final ResponseEntity<List<ApolloConfigNotification>> NOT_MODIFIED_RESPONSE_LIST =
            new ResponseEntity<>(HttpStatus.NOT_MODIFIED);

    /**
     * 正常命名空间到原始命名空间的转化
     * key：归一命名空间名称
     * value：原始命名空间名称
     */
    private Map<String, String> normalizedNamespaceNameToOriginalNamespaceName;

    /**
     * 要通知的结果
     */
    private DeferredResult<ResponseEntity<List<ApolloConfigNotification>>> result;

    /**
     * 延迟结果包装
     *
     * @param timeoutInMilli 超时时间
     */
    public DeferredResultWrapper(long timeoutInMilli) {
        result = new DeferredResult<>(timeoutInMilli, NOT_MODIFIED_RESPONSE_LIST);
    }

    /**
     * 重新记录命名空间名称为原始名称，否则客户端无法识别
     *
     * @param originalNamespaceName   原始名称
     * @param normalizedNamespaceName 正常名称
     */
    public void recordNamespaceNameNormalizedResult(String originalNamespaceName, String normalizedNamespaceName) {
        if (normalizedNamespaceNameToOriginalNamespaceName == null) {
            normalizedNamespaceNameToOriginalNamespaceName = Maps.newHashMap();
        }
        normalizedNamespaceNameToOriginalNamespaceName.put(normalizedNamespaceName, originalNamespaceName);
    }

    /**
     * 设置结果的超时回调
     *
     * @param timeoutCallback 超时回调
     */
    public void onTimeout(Runnable timeoutCallback) {
        result.onTimeout(timeoutCallback);
    }

    /**
     * 设置完成通知
     *
     * @param completionCallback 完成回调
     */
    public void onCompletion(Runnable completionCallback) {
        result.onCompletion(completionCallback);
    }

    /**
     * 设置通知结果
     *
     * @param notification 通知
     */
    public void setResult(ApolloConfigNotification notification) {
        setResult(Lists.newArrayList(notification));
    }

    /**
     * 名称空间名称用作客户端的键，因此我们必须返回原始名称而不是正确的
     * <p>
     * The namespace name is used as a key in client side, so we have to return the original one instead of the correct
     * one
     */
    public void setResult(List<ApolloConfigNotification> notifications) {
        if (normalizedNamespaceNameToOriginalNamespaceName != null) {
            notifications.stream()
                    .filter(notification ->
                            normalizedNamespaceNameToOriginalNamespaceName.containsKey(notification.getNamespaceName()))
                    .forEach(notification ->
                            notification.setNamespaceName(normalizedNamespaceNameToOriginalNamespaceName.get(notification.getNamespaceName())));
        }

        // 设置200
        result.setResult(new ResponseEntity<>(notifications, HttpStatus.OK));
    }

    public DeferredResult<ResponseEntity<List<ApolloConfigNotification>>> getResult() {
        return result;
    }

    @Override
    public int compareTo(@NonNull DeferredResultWrapper deferredResultWrapper) {
        return Integer.compare(this.hashCode(), deferredResultWrapper.hashCode());
    }
}
