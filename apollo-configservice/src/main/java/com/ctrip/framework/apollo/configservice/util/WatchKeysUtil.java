package com.ctrip.framework.apollo.configservice.util;

import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.configservice.service.AppNamespaceServiceWithCache;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 监控key工具类
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Component
public class WatchKeysUtil {
    private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR);
    private final AppNamespaceServiceWithCache appNamespaceService;

    public WatchKeysUtil(final AppNamespaceServiceWithCache appNamespaceService) {
        this.appNamespaceService = appNamespaceService;
    }

    /**
     * 组装监控key，通过给定数据
     * Assemble watch keys for the given appId, cluster, namespace, dataCenter combination
     */
    public Set<String> assembleAllWatchKeys(String appId, String clusterName, String namespace,
                                            String dataCenter) {
        Multimap<String, String> watchedKeysMap =
                assembleAllWatchKeys(appId, clusterName, Sets.newHashSet(namespace), dataCenter);
        return Sets.newHashSet(watchedKeysMap.get(namespace));
    }

    /**
     * 为给定的appId，集群，名称空间，dataCenter组合组装监视键
     * Assemble watch keys for the given appId, cluster, namespaces, dataCenter combination
     *
     * @return a multimap with namespace as the key and watch keys as the value
     * 以命名空间为键，并以监视键为值的多图
     */
    public Multimap<String, String> assembleAllWatchKeys(String appId,
                                                         String clusterName,
                                                         Set<String> namespaces,
                                                         String dataCenter) {
        // 集群各个命名空间的监控key
        Multimap<String, String> watchedKeysMap =
                assembleWatchKeys(appId, clusterName, namespaces, dataCenter);

        //Every app has an 'application' namespace
        // 每个app都有一个 application 的命名空间，如果不仅仅这一个命名空间，就要处理应用的命名空间，关联的公开的命名空间
        if (!(namespaces.size() == 1
                && namespaces.contains(ConfigConsts.NAMESPACE_APPLICATION))) {
            // 获取实际属于应用的命名空间
            Set<String> namespacesBelongToAppId = namespacesBelongToAppId(appId, namespaces);
            // 比较出关联类型的公开命名空间
            Set<String> publicNamespaces = Sets.difference(namespaces, namespacesBelongToAppId);

            //Listen on more namespaces if it's a public namespace
            // 如果应用存在公共命名空间，监听更多的关联的公共命名空间
            if (!publicNamespaces.isEmpty()) {
                watchedKeysMap.putAll(findPublicConfigWatchKeys(appId, clusterName, publicNamespaces, dataCenter));
            }
        }

        return watchedKeysMap;
    }

    /**
     * 查询公共命名空间的信息
     *
     * @param applicationId 应用编号
     * @param clusterName   集群名称
     * @param namespaces    命名空间名称集合
     * @param dataCenter    数据中心
     * @return 公共命名空间的监控key map
     */
    private Multimap<String, String> findPublicConfigWatchKeys(String applicationId,
                                                               String clusterName,
                                                               Set<String> namespaces,
                                                               String dataCenter) {
        Multimap<String, String> watchedKeysMap = HashMultimap.create();
        // 获取实际上的公共命名空间
        List<AppNamespace> appNamespaces = appNamespaceService.findPublicNamespacesByNames(namespaces);

        for (AppNamespace appNamespace : appNamespaces) {
            //check whether the namespace's appId equals to current one
            // 如果公共命名空间属于当前的应用，略过
            if (Objects.equals(applicationId, appNamespace.getAppId())) {
                continue;
            }

            // 监控其他的关联的公共命名空间
            String publicConfigAppId = appNamespace.getAppId();
            watchedKeysMap.putAll(appNamespace.getName(),
                    assembleWatchKeys(publicConfigAppId, clusterName, appNamespace.getName(), dataCenter));
        }

        return watchedKeysMap;
    }

    /**
     * 组装件key
     *
     * @param appId     应用编号
     * @param cluster   集群名称
     * @param namespace 命名空间名称
     * @return key
     */
    private String assembleKey(String appId, String cluster, String namespace) {
        return STRING_JOINER.join(appId, cluster, namespace);
    }

    /**
     * 建立watch的key，根据cluster和dateCenter（IDC）
     * 指定的，IDC的，默认的
     *
     * @param appId       应用编号
     * @param clusterName 集群名称
     * @param namespace   命名空间
     * @param dataCenter  数据中心
     * @return watch的key
     */
    private Set<String> assembleWatchKeys(String appId, String clusterName, String namespace,
                                          String dataCenter) {
        if (ConfigConsts.NO_APPID_PLACEHOLDER.equalsIgnoreCase(appId)) {
            return Collections.emptySet();
        }
        Set<String> watchedKeys = Sets.newHashSet();

        //watch specified cluster config change
        // 监控具体集群配置的改变，如果不是默认集群，组装key
        if (!Objects.equals(ConfigConsts.CLUSTER_NAME_DEFAULT, clusterName)) {
            watchedKeys.add(assembleKey(appId, clusterName, namespace));
        }

        //watch data center config change
        // 监控数据中心的改变，如果数据中心和集群名称不符，则把数据中心也当做集群组装key
        if (!Strings.isNullOrEmpty(dataCenter)
                && !Objects.equals(dataCenter, clusterName)) {
            watchedKeys.add(assembleKey(appId, dataCenter, namespace));
        }

        //watch default cluster config change
        // 监控默认集群配置的改变
        watchedKeys.add(assembleKey(appId, ConfigConsts.CLUSTER_NAME_DEFAULT, namespace));

        return watchedKeys;
    }

    /**
     * 建立监控key
     *
     * @param appId       应用编号
     * @param clusterName 集群名称
     * @param namespaces  命名空间集合
     * @param dataCenter  数据中心
     * @return 命名空间和监控key的map
     */
    private Multimap<String, String> assembleWatchKeys(String appId, String clusterName,
                                                       Set<String> namespaces,
                                                       String dataCenter) {
        Multimap<String, String> watchedKeysMap = HashMultimap.create();

        // 组装集群的各个命名空间的监控key
        for (String namespace : namespaces) {
            watchedKeysMap.putAll(
                    namespace,
                    assembleWatchKeys(appId, clusterName, namespace, dataCenter));
        }

        return watchedKeysMap;
    }

    /**
     * 获取实际属于应用的命名空间
     *
     * @param appId      应用编号
     * @param namespaces 命名空间集合
     * @return 过滤后的命名空间
     */
    private Set<String> namespacesBelongToAppId(String appId, Set<String> namespaces) {
        if (ConfigConsts.NO_APPID_PLACEHOLDER.equalsIgnoreCase(appId)) {
            return Collections.emptySet();
        }
        List<AppNamespace> appNamespaces =
                appNamespaceService.findByAppIdAndNamespaces(appId, namespaces);

        if (appNamespaces == null || appNamespaces.isEmpty()) {
            return Collections.emptySet();
        }

        return appNamespaces.stream().map(AppNamespace::getName).collect(Collectors.toSet());
    }
}
