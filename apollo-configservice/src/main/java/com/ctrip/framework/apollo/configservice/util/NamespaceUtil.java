package com.ctrip.framework.apollo.configservice.util;

import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.configservice.service.AppNamespaceServiceWithCache;
import org.springframework.stereotype.Component;

/**
 * 命名空间工具类
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Component
public class NamespaceUtil {

    private final AppNamespaceServiceWithCache appNamespaceServiceWithCache;

    public NamespaceUtil(final AppNamespaceServiceWithCache appNamespaceServiceWithCache) {
        this.appNamespaceServiceWithCache = appNamespaceServiceWithCache;
    }

    /**
     * 过滤命名空间，主要是移除 properties 的尾缀
     *
     * @param namespaceName 命名空间名称
     * @return 过滤后的命名空间
     */
    public String filterNamespaceName(String namespaceName) {
        if (namespaceName.toLowerCase().endsWith(".properties")) {
            int dotIndex = namespaceName.lastIndexOf(".");
            return namespaceName.substring(0, dotIndex);
        }

        return namespaceName;
    }

    /**
     * 归一化命名空间名称，转换大小写不规则的命名空间为缓存中的命名空间名称
     *
     * @param appId         应用编号
     * @param namespaceName 原始命名空间名称
     * @return 归一化后的命名空间名称，即全小写
     */
    public String normalizeNamespace(String appId, String namespaceName) {
        // 先用应用命名空间缓存中获取
        AppNamespace appNamespace = appNamespaceServiceWithCache.findByAppIdAndNamespace(appId, namespaceName);
        if (appNamespace != null) {
            return appNamespace.getName();
        }

        // 再从公共命名空间（即关联的公共命名空间）缓存中获取
        appNamespace = appNamespaceServiceWithCache.findPublicNamespaceByName(namespaceName);
        if (appNamespace != null) {
            return appNamespace.getName();
        }

        return namespaceName;
    }
}
