package com.ctrip.framework.apollo.configservice.controller;

import com.ctrip.framework.apollo.biz.entity.Release;
import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.configservice.service.AppNamespaceServiceWithCache;
import com.ctrip.framework.apollo.configservice.service.config.ConfigService;
import com.ctrip.framework.apollo.configservice.util.InstanceConfigAuditUtil;
import com.ctrip.framework.apollo.configservice.util.NamespaceUtil;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.dto.ApolloConfig;
import com.ctrip.framework.apollo.core.dto.ApolloNotificationMessages;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 对客户端暴露的配置API
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@RestController
@RequestMapping("/configs")
public class ConfigController {

    /**
     * 地址请求头分割器
     */
    private static final Splitter X_FORWARDED_FOR_SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();

    private final ConfigService configService;
    private final AppNamespaceServiceWithCache appNamespaceService;
    private final NamespaceUtil namespaceUtil;
    private final InstanceConfigAuditUtil instanceConfigAuditUtil;
    private final Gson gson;

    private static final Type configurationTypeReference = new TypeToken<Map<String, String>>() {
    }.getType();

    public ConfigController(
            final ConfigService configService,
            final AppNamespaceServiceWithCache appNamespaceService,
            final NamespaceUtil namespaceUtil,
            final InstanceConfigAuditUtil instanceConfigAuditUtil,
            final Gson gson) {
        this.configService = configService;
        this.appNamespaceService = appNamespaceService;
        this.namespaceUtil = namespaceUtil;
        this.instanceConfigAuditUtil = instanceConfigAuditUtil;
        this.gson = gson;
    }

    /**
     * 提供给客户端配置读取功能
     *
     * @param appId                应用编号
     * @param clusterName          集群名称
     * @param namespace            命名空间名称
     * @param dataCenter           数据中心
     * @param clientSideReleaseKey 客户端发布key，用于和服务端的key进行对比，判断配置是否更新
     * @param clientIp             客户端ip，用于灰度发布功能
     * @param messagesAsString     消息，客户端当前请求的 Namespace 的通知消息明细
     * @param request              请求
     * @param response             返回
     * @return apollo配置
     */
    @GetMapping(value = "/{appId}/{clusterName}/{namespace:.+}")
    public ApolloConfig queryConfig(@PathVariable String appId,
                                    @PathVariable String clusterName,
                                    @PathVariable String namespace,
                                    @RequestParam(value = "dataCenter", required = false) String dataCenter,
                                    @RequestParam(value = "releaseKey", defaultValue = "-1") String clientSideReleaseKey,
                                    @RequestParam(value = "ip", required = false) String clientIp,
                                    @RequestParam(value = "messages", required = false) String messagesAsString,
                                    HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        // 原始命名空间
        String originalNamespace = namespace;
        //strip out .properties suffix
        // 去除 .properties 尾缀
        namespace = namespaceUtil.filterNamespaceName(namespace);
        //fix the character case issue, such as FX.apollo <-> fx.apollo
        // 归一化后的命名空间名称
        namespace = namespaceUtil.normalizeNamespace(appId, namespace);

        // 尝试获取客户端ip
        if (Strings.isNullOrEmpty(clientIp)) {
            clientIp = this.tryToGetClientIp(request);
        }

        // 转换客户端消息为实体类
        ApolloNotificationMessages clientMessages = transformMessages(messagesAsString);

        List<Release> releases = Lists.newLinkedList();
        String appClusterNameLoaded = clusterName;
        // 获取当前应用的发布配置
        if (!ConfigConsts.NO_APPID_PLACEHOLDER.equalsIgnoreCase(appId)) {
            // 加载最新的发布配置
            // 根据 clusterName 和 dataCenter 分别查询 Release 直到找到一个，所以需要根据结果的 Release 获取真正的 Cluster 名
            Release currentAppRelease = configService.loadConfig(
                    appId,
                    clientIp,
                    appId,
                    clusterName,
                    namespace,
                    dataCenter,
                    clientMessages);

            if (currentAppRelease != null) {
                releases.add(currentAppRelease);
                //we have cluster search process, so the cluster name might be overridden
                // 有集群搜索过程，因此集群名称可能会被覆盖
                appClusterNameLoaded = currentAppRelease.getClusterName();
            }
        }

        //if namespace does not belong to this appId, should check if there is a public configuration
        // 如果命名空间不属于此应用，则应检查是否存在公共配置，即获取关联的公共配置
        if (!this.namespaceBelongsToAppId(appId, namespace)) {
            Release publicRelease = this.findPublicConfig(
                    appId,
                    clientIp,
                    clusterName,
                    namespace,
                    dataCenter,
                    clientMessages);
            if (!Objects.isNull(publicRelease)) {
                releases.add(publicRelease);
            }
        }

        // 不存在发布的配置，返回404
        if (releases.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    String.format("Could not load configurations with appId: %s, clusterName: %s, namespace: %s",
                            appId, clusterName, originalNamespace));
            Tracer.logEvent("Apollo.Config.NotFound",
                    assembleKey(appId, clusterName, originalNamespace, dataCenter));
            return null;
        }

        // 审计发布版本
        auditReleases(appId, clusterName, dataCenter, clientIp, releases);

        // 发布key用 + 号连接起来
        String mergedReleaseKey = releases.stream()
                .map(Release::getReleaseKey)
                .collect(Collectors.joining(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR));
        // 如果发布的key 和 客户端的发布key相等，则说明未修改过，返回304即可
        if (mergedReleaseKey.equals(clientSideReleaseKey)) {
            // Client side configuration is the same with server side, return 304
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            Tracer.logEvent("Apollo.Config.NotModified",
                    assembleKey(appId, appClusterNameLoaded, originalNamespace, dataCenter));
            return null;
        }

        // 返回最新的配置
        ApolloConfig apolloConfig = new ApolloConfig(appId, appClusterNameLoaded, originalNamespace,
                mergedReleaseKey);
        apolloConfig.setConfigurations(this.mergeReleaseConfigurations(releases));

        Tracer.logEvent("Apollo.Config.Found", assembleKey(appId, appClusterNameLoaded,
                originalNamespace, dataCenter));
        return apolloConfig;
    }

    /**
     * 检查命名空间是否属于此应用
     *
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @return true属于，false不属于
     */
    private boolean namespaceBelongsToAppId(String appId, String namespaceName) {
        //Every app has an 'application' namespace
        // 默认命名空间，属于
        if (Objects.equals(ConfigConsts.NAMESPACE_APPLICATION, namespaceName)) {
            return true;
        }

        //if no appId is present, then no other namespace belongs to it
        // 如果不存在appId，则不存在其他名称空间
        if (ConfigConsts.NO_APPID_PLACEHOLDER.equalsIgnoreCase(appId)) {
            return false;
        }

        // 忽视大小写，获取属于其他应用的公共命名空间
        AppNamespace appNamespace = appNamespaceService.findByAppIdAndNamespace(appId, namespaceName);

        return appNamespace != null;
    }

    /**
     * 查找公共配置（关联配置）
     *
     * @param clientAppId the application which uses public config 应用使用的什么公共配置
     * @param namespace   the namespace 命名空间名称
     * @param dataCenter  the datacenter 数据中心
     */
    private Release findPublicConfig(String clientAppId, String clientIp, String clusterName,
                                     String namespace, String dataCenter, ApolloNotificationMessages clientMessages) {
        // 获取公共命名空间
        AppNamespace appNamespace = appNamespaceService.findPublicNamespaceByName(namespace);

        //check whether the namespace's appId equals to current one
        // 检查命名空间是否属于此应用
        if (Objects.isNull(appNamespace) || Objects.equals(clientAppId, appNamespace.getAppId())) {
            return null;
        }

        // 不属于此应用则加载对应的公共配置，即关联配置
        String publicConfigAppId = appNamespace.getAppId();
        return configService.loadConfig(clientAppId, clientIp, publicConfigAppId, clusterName, namespace, dataCenter,
                clientMessages);
    }

    /**
     * 合并配置到一个map中
     * 较低索引版本的释放优先于较高索引版本
     * Merge configurations of releases.
     * Release in lower index override those in higher index
     */
    Map<String, String> mergeReleaseConfigurations(List<Release> releases) {
        Map<String, String> result = Maps.newLinkedHashMap();
        // 翻转数组，因为关联类型的 Release 后添加到 Release 数组中。但是，App 下 的 Release 的优先级更高，所以进行反转
        // 先添加公共的，后添加私有的
        for (Release release : Lists.reverse(releases)) {
            result.putAll(gson.fromJson(release.getConfigurations(), configurationTypeReference));
        }
        return result;
    }

    private String assembleKey(String appId, String cluster, String namespace, String dataCenter) {
        List<String> keyParts = Lists.newArrayList(appId, cluster, namespace);
        if (!Strings.isNullOrEmpty(dataCenter)) {
            keyParts.add(dataCenter);
        }
        return keyParts.stream().collect(Collectors.joining(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR));
    }

    /**
     * 审计发布信息
     *
     * @param appId      应用编号
     * @param cluster    集群名称
     * @param dataCenter 数据中心
     * @param clientIp   客户端ip
     * @param releases   发布版本
     */
    private void auditReleases(String appId, String cluster, String dataCenter, String clientIp,
                               List<Release> releases) {
        if (Strings.isNullOrEmpty(clientIp)) {
            //no need to audit instance config when there is no ip
            // 没有IP时无需审核实例配置
            return;
        }

        // 发布版本执行审计
        for (Release release : releases) {
            instanceConfigAuditUtil.audit(appId, cluster, dataCenter, clientIp, release.getAppId(),
                    release.getClusterName(),
                    release.getNamespaceName(), release.getReleaseKey());
        }
    }

    /**
     * 尝试根据请求获取客户端ip
     * X-FORWARDED-FOR 表示 HTTP 请求端真实 IP
     * X-Forwarded-For: client, proxy1, proxy2
     * <p>
     * 直接对外提供服务的 Web 应用，在进行与安全有关的操作时，只能通过 Remote Address 获取 IP，不能相信任何请求头
     * 使用 Nginx 等 Web Server 进行反向代理的 Web 应用，在配置正确的前提下，要用 X-Forwarded-For 最后一节 或 X-Real-IP 来获取 IP
     * 因为 Remote Address 得到的是 Nginx 所在服务器的内网 IP
     * 同时还应该禁止 Web 应用直接对外提供服务
     * 在与安全无关的场景，例如通过 IP 显示所在地天气，可以从 X-Forwarded-For 靠前的位置获取 IP，但是需要校验 IP 格式合法性
     *
     * @param request 请求
     * @return 客户端ip
     */
    private String tryToGetClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-FORWARDED-FOR");
        if (!Strings.isNullOrEmpty(forwardedFor)) {
            return X_FORWARDED_FOR_SPLITTER.splitToList(forwardedFor).get(0);
        }
        return request.getRemoteAddr();
    }

    /**
     * 转换传输消息
     *
     * @param messagesAsString 消息
     * @return apollo通知消息
     */
    ApolloNotificationMessages transformMessages(String messagesAsString) {
        ApolloNotificationMessages notificationMessages = null;
        if (!Strings.isNullOrEmpty(messagesAsString)) {
            try {
                notificationMessages = gson.fromJson(messagesAsString, ApolloNotificationMessages.class);
            } catch (Throwable ex) {
                Tracer.logError(ex);
            }
        }

        return notificationMessages;
    }
}
