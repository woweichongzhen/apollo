package com.ctrip.framework.apollo.portal.api;

import com.ctrip.framework.apollo.common.dto.*;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.google.common.base.Joiner;
import org.springframework.boot.actuate.health.Health;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.*;

/**
 * Admin Service API 集合，包含 Admin Service 所有模块 API 的调用封装
 */
@Service
public class AdminServiceAPI {

    /**
     * 健康检查api
     */
    @Service
    public static class HealthAPI extends API {

        /**
         * 执行健康检查
         *
         * @param env 环境
         * @return 健康结果
         */
        public Health health(Env env) {
            return restTemplate.get(env, "/health", Health.class);
        }
    }

    /**
     * 应用api
     * 封装对adminservice的应用信息相关的rest调用
     */
    @Service
    public static class AppAPI extends API {

        public AppDTO loadApp(Env env, String appId) {
            return restTemplate.get(env, "apps/{appId}", AppDTO.class, appId);
        }

        /**
         * 发送请求，创建某个环境的应用
         *
         * @param env 环境
         * @param app 应用信息
         * @return 创建后的返回结果
         */
        public AppDTO createApp(Env env, AppDTO app) {
            return restTemplate.post(env, "apps", app, AppDTO.class);
        }

        public void updateApp(Env env, AppDTO app) {
            restTemplate.put(env, "apps/{appId}", app, app.getAppId());
        }

        public void deleteApp(Env env, String appId, String operator) {
            restTemplate.delete(env, "/apps/{appId}?operator={operator}", appId, operator);
        }
    }

    /**
     * 命名空间API
     * 封装对 Admin Service 的 AppNamespace 和 Namespace 两个模块的 API 调用
     */
    @Service
    public static class NamespaceAPI extends API {

        private ParameterizedTypeReference<Map<String, Boolean>>
                typeReference = new ParameterizedTypeReference<Map<String, Boolean>>() {
        };

        public List<NamespaceDTO> findNamespaceByCluster(String appId, Env env, String clusterName) {
            NamespaceDTO[] namespaceDTOs = restTemplate.get(env, "apps/{appId}/clusters/{clusterName}/namespaces",
                    NamespaceDTO[].class, appId,
                    clusterName);
            return Arrays.asList(namespaceDTOs);
        }

        /**
         * 获取命名空间dto
         *
         * @param appId         应用编号
         * @param env           环境
         * @param clusterName   集群名称
         * @param namespaceName 命名空间名称
         * @return 命名空间dto
         */
        public NamespaceDTO loadNamespace(String appId, Env env, String clusterName,
                                          String namespaceName) {
            return restTemplate.get(env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}",
                    NamespaceDTO.class, appId, clusterName, namespaceName);
        }

        public NamespaceDTO findPublicNamespaceForAssociatedNamespace(Env env, String appId, String clusterName,
                                                                      String namespaceName) {
            return
                    restTemplate
                            .get(env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/associated" +
                                            "-public-namespace",
                                    NamespaceDTO.class, appId, clusterName, namespaceName);
        }

        /**
         * 创建指定环境的命名空间
         *
         * @param env       环境
         * @param namespace 命名空间dto
         * @return 创建后的命名空间dto
         */
        public NamespaceDTO createNamespace(Env env, NamespaceDTO namespace) {
            return restTemplate
                    .post(env, "apps/{appId}/clusters/{clusterName}/namespaces", namespace, NamespaceDTO.class,
                            namespace.getAppId(), namespace.getClusterName());
        }

        /**
         * 创建应用命名空间
         *
         * @param env          环境
         * @param appNamespace 名称
         * @return 创建后的dto
         */
        public AppNamespaceDTO createAppNamespace(Env env, AppNamespaceDTO appNamespace) {
            return restTemplate
                    .post(env, "apps/{appId}/appnamespaces", appNamespace, AppNamespaceDTO.class,
                            appNamespace.getAppId());
        }

        public AppNamespaceDTO createMissingAppNamespace(Env env, AppNamespaceDTO appNamespace) {
            return restTemplate
                    .post(env, "apps/{appId}/appnamespaces?silentCreation=true", appNamespace, AppNamespaceDTO.class,
                            appNamespace.getAppId());
        }

        public List<AppNamespaceDTO> getAppNamespaces(String appId, Env env) {
            AppNamespaceDTO[] appNamespaceDTOs = restTemplate.get(env, "apps/{appId}/appnamespaces",
                    AppNamespaceDTO[].class, appId);
            return Arrays.asList(appNamespaceDTOs);
        }

        public void deleteNamespace(Env env, String appId, String clusterName, String namespaceName, String operator) {
            restTemplate
                    .delete(env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}?operator={operator}"
                            , appId,
                            clusterName,
                            namespaceName, operator);
        }

        public Map<String, Boolean> getNamespacePublishInfo(Env env, String appId) {
            return restTemplate.get(env, "apps/{appId}/namespaces/publish_info", typeReference, appId).getBody();
        }

        public List<NamespaceDTO> getPublicAppNamespaceAllNamespaces(Env env, String publicNamespaceName,
                                                                     int page, int size) {
            NamespaceDTO[] namespaceDTOs =
                    restTemplate.get(env, "/appnamespaces/{publicNamespaceName}/namespaces?page={page}&size={size}",
                            NamespaceDTO[].class, publicNamespaceName, page, size);
            return Arrays.asList(namespaceDTOs);
        }

        public int countPublicAppNamespaceAssociatedNamespaces(Env env, String publicNamesapceName) {
            Integer count =
                    restTemplate.get(env, "/appnamespaces/{publicNamespaceName}/associated-namespaces/count",
                            Integer.class,
                            publicNamesapceName);

            return count == null ? 0 : count;
        }

        public void deleteAppNamespace(Env env, String appId, String namespaceName, String operator) {
            restTemplate.delete(env, "/apps/{appId}/appnamespaces/{namespaceName}?operator={operator}", appId,
                    namespaceName,
                    operator);
        }
    }

    /**
     * 调用adminservice的项API
     */
    @Service
    public static class ItemAPI extends API {

        /**
         * 获取历史项
         *
         * @param appId         应用编号
         * @param env           环境
         * @param clusterName   集群名称
         * @param namespaceName 命名空间名称
         * @return 历史项
         */
        public List<ItemDTO> findItems(String appId, Env env, String clusterName, String namespaceName) {
            ItemDTO[] itemDTOs =
                    restTemplate.get(env,
                            "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/items",
                            ItemDTO[].class,
                            appId,
                            clusterName,
                            namespaceName);
            return Arrays.asList(itemDTOs);
        }

        /**
         * 获取删除的项
         */
        public List<ItemDTO> findDeletedItems(String appId, Env env, String clusterName, String namespaceName) {
            ItemDTO[] itemDTOs =
                    restTemplate.get(env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/items" +
                                    "/deleted",
                            ItemDTO[].class, appId, clusterName, namespaceName);
            return Arrays.asList(itemDTOs);
        }

        public ItemDTO loadItem(Env env, String appId, String clusterName, String namespaceName, String key) {
            return restTemplate.get(env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/items/{key}",
                    ItemDTO.class, appId, clusterName, namespaceName, key);
        }

        public ItemDTO loadItemById(Env env, long itemId) {
            return restTemplate.get(env, "items/{itemId}", ItemDTO.class, itemId);
        }

        /**
         * 通过改变集合更新指定环境和集群的命名空间
         *
         * @param appId       应用编号
         * @param env         环境
         * @param clusterName 集群名称
         * @param namespace   命名空间
         * @param changeSets  改变集合
         */
        public void updateItemsByChangeSet(String appId, Env env, String clusterName, String namespace,
                                           ItemChangeSets changeSets) {
            restTemplate.post(env,
                    "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/itemset",
                    changeSets,
                    Void.class,
                    appId,
                    clusterName,
                    namespace);
        }

        public void updateItem(String appId, Env env, String clusterName, String namespace, long itemId, ItemDTO item) {
            restTemplate.put(env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/items/{itemId}",
                    item, appId, clusterName, namespace, itemId);

        }

        /**
         * 创建一项
         *
         * @param appId       应用编号
         * @param env         环境
         * @param clusterName 集群名称
         * @param namespace   命名空间
         * @param item        项
         * @return 创建后的一项
         */
        public ItemDTO createItem(String appId, Env env, String clusterName, String namespace, ItemDTO item) {
            return restTemplate.post(env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/items",
                    item, ItemDTO.class, appId, clusterName, namespace);
        }

        public void deleteItem(Env env, long itemId, String operator) {

            restTemplate.delete(env, "items/{itemId}?operator={operator}", itemId, operator);
        }
    }

    @Service
    public static class ClusterAPI extends API {

        public List<ClusterDTO> findClustersByApp(String appId, Env env) {
            ClusterDTO[] clusterDTOs = restTemplate.get(env, "apps/{appId}/clusters", ClusterDTO[].class,
                    appId);
            return Arrays.asList(clusterDTOs);
        }

        public ClusterDTO loadCluster(String appId, Env env, String clusterName) {
            return restTemplate.get(env, "apps/{appId}/clusters/{clusterName}", ClusterDTO.class,
                    appId, clusterName);
        }

        /**
         * 校验指定环境的集群唯一性
         */
        public boolean isClusterUnique(String appId, Env env, String clusterName) {
            return restTemplate
                    .get(env, "apps/{appId}/cluster/{clusterName}/unique", Boolean.class,
                            appId, clusterName);

        }

        /**
         * 创建指定环境的集群
         */
        public ClusterDTO create(Env env, ClusterDTO cluster) {
            return restTemplate.post(env, "apps/{appId}/clusters", cluster, ClusterDTO.class,
                    cluster.getAppId());
        }


        public void delete(Env env, String appId, String clusterName, String operator) {
            restTemplate.delete(env, "apps/{appId}/clusters/{clusterName}?operator={operator}", appId, clusterName,
                    operator);
        }
    }

    @Service
    public static class AccessKeyAPI extends API {

        public AccessKeyDTO create(Env env, AccessKeyDTO accessKey) {
            return restTemplate.post(env, "apps/{appId}/accesskeys",
                    accessKey, AccessKeyDTO.class, accessKey.getAppId());
        }

        public List<AccessKeyDTO> findByAppId(Env env, String appId) {
            AccessKeyDTO[] accessKeys = restTemplate.get(env, "apps/{appId}/accesskeys",
                    AccessKeyDTO[].class, appId);
            return Arrays.asList(accessKeys);
        }

        public void delete(Env env, String appId, long id, String operator) {
            restTemplate.delete(env, "apps/{appId}/accesskeys/{id}?operator={operator}",
                    appId, id, operator);
        }

        public void enable(Env env, String appId, long id, String operator) {
            restTemplate.put(env, "apps/{appId}/accesskeys/{id}/enable?operator={operator}",
                    null, appId, id, operator);
        }

        public void disable(Env env, String appId, long id, String operator) {
            restTemplate.put(env, "apps/{appId}/accesskeys/{id}/disable?operator={operator}",
                    null, appId, id, operator);
        }
    }

    /**
     * 请求adminservice发布
     */
    @Service
    public static class ReleaseAPI extends API {

        private static final Joiner JOINER = Joiner.on(",");

        public ReleaseDTO loadRelease(Env env, long releaseId) {
            return restTemplate.get(env, "releases/{releaseId}", ReleaseDTO.class, releaseId);
        }

        /**
         * 查找发布信息集合
         *
         * @param env        环境
         * @param releaseIds 发布信息id集合
         * @return 发布信息集合
         */
        public List<ReleaseDTO> findReleaseByIds(Env env, Set<Long> releaseIds) {
            if (CollectionUtils.isEmpty(releaseIds)) {
                return Collections.emptyList();
            }

            ReleaseDTO[] releases = restTemplate.get(
                    env,
                    "/releases?releaseIds={releaseIds}",
                    ReleaseDTO[].class,
                    JOINER.join(releaseIds));
            return Arrays.asList(releases);

        }

        public List<ReleaseDTO> findAllReleases(String appId, Env env, String clusterName, String namespaceName,
                                                int page,
                                                int size) {
            ReleaseDTO[] releaseDTOs = restTemplate.get(
                    env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/releases/all?page={page" +
                            "}&size={size}",
                    ReleaseDTO[].class,
                    appId, clusterName, namespaceName, page, size);
            return Arrays.asList(releaseDTOs);
        }

        public List<ReleaseDTO> findActiveReleases(String appId, Env env, String clusterName, String namespaceName,
                                                   int page,
                                                   int size) {
            ReleaseDTO[] releaseDTOs = restTemplate.get(
                    env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/releases/active?page={page" +
                            "}&size={size}",
                    ReleaseDTO[].class,
                    appId, clusterName, namespaceName, page, size);
            return Arrays.asList(releaseDTOs);
        }

        /**
         * 加载最新的发布配置
         */
        public ReleaseDTO loadLatestRelease(String appId, Env env, String clusterName,
                                            String namespace) {
            ReleaseDTO releaseDTO = restTemplate
                    .get(env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/releases/latest",
                            ReleaseDTO.class, appId, clusterName, namespace);
            return releaseDTO;
        }

        /**
         * 创建发布信息
         *
         * @param appId              应用编号
         * @param env                环境
         * @param clusterName        集群名称
         * @param namespace          命名空间
         * @param releaseName        发宝名称
         * @param releaseComment     发布备注
         * @param operator           操作者
         * @param isEmergencyPublish 是否紧急发布
         * @return 发布后的dto
         */
        public ReleaseDTO createRelease(String appId, Env env, String clusterName, String namespace,
                                        String releaseName, String releaseComment, String operator,
                                        boolean isEmergencyPublish) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8"));

            MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("name", releaseName);
            parameters.add("comment", releaseComment);
            parameters.add("operator", operator);
            parameters.add("isEmergencyPublish", String.valueOf(isEmergencyPublish));

            // http请求实体
            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(parameters, headers);

            return restTemplate.post(
                    env,
                    "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/releases",
                    entity,
                    ReleaseDTO.class,
                    appId,
                    clusterName,
                    namespace);
        }

        public ReleaseDTO createGrayDeletionRelease(String appId, Env env, String clusterName, String namespace,
                                                    String releaseName, String releaseComment, String operator,
                                                    boolean isEmergencyPublish, Set<String> grayDelKeys) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset" +
                    "=UTF-8"));
            MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("releaseName", releaseName);
            parameters.add("comment", releaseComment);
            parameters.add("operator", operator);
            parameters.add("isEmergencyPublish", String.valueOf(isEmergencyPublish));
            grayDelKeys.forEach(key -> parameters.add("grayDelKeys", key));
            HttpEntity<MultiValueMap<String, String>> entity =
                    new HttpEntity<>(parameters, headers);
            ReleaseDTO response = restTemplate.post(
                    env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/gray-del-releases", entity,
                    ReleaseDTO.class, appId, clusterName, namespace);
            return response;
        }

        /**
         * 更新子到父，并发布配置
         */
        public ReleaseDTO updateAndPublish(String appId, Env env, String clusterName, String namespace,
                                           String releaseName, String releaseComment, String branchName,
                                           boolean isEmergencyPublish, boolean deleteBranch,
                                           ItemChangeSets changeSets) {

            return restTemplate.post(env,
                    "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/updateAndPublish?"
                            + "releaseName={releaseName}&releaseComment={releaseComment}&branchName={branchName}"
                            + "&deleteBranch={deleteBranch}&isEmergencyPublish={isEmergencyPublish}",
                    changeSets, ReleaseDTO.class, appId, clusterName, namespace,
                    releaseName, releaseComment, branchName, deleteBranch, isEmergencyPublish);

        }

        public void rollback(Env env, long releaseId, String operator) {
            restTemplate.put(env,
                    "releases/{releaseId}/rollback?operator={operator}",
                    null, releaseId, operator);
        }
    }

    @Service
    public static class CommitAPI extends API {

        public List<CommitDTO> find(String appId, Env env, String clusterName, String namespaceName, int page,
                                    int size) {

            CommitDTO[] commitDTOs = restTemplate.get(env,
                    "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/commit?page={page}&size={size}",
                    CommitDTO[].class,
                    appId, clusterName, namespaceName, page, size);

            return Arrays.asList(commitDTOs);
        }
    }

    @Service
    public static class NamespaceLockAPI extends API {

        public NamespaceLockDTO getNamespaceLockOwner(String appId, Env env, String clusterName, String namespaceName) {
            return restTemplate.get(env, "apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/lock",
                    NamespaceLockDTO.class,
                    appId, clusterName, namespaceName);

        }
    }

    @Service
    public static class InstanceAPI extends API {

        private Joiner joiner = Joiner.on(",");
        private ParameterizedTypeReference<PageDTO<InstanceDTO>>
                pageInstanceDtoType =
                new ParameterizedTypeReference<PageDTO<InstanceDTO>>() {
                };

        public PageDTO<InstanceDTO> getByRelease(Env env, long releaseId, int page, int size) {
            ResponseEntity<PageDTO<InstanceDTO>>
                    entity =
                    restTemplate
                            .get(env, "/instances/by-release?releaseId={releaseId}&page={page}&size={size}",
                                    pageInstanceDtoType,
                                    releaseId, page, size);
            return entity.getBody();

        }

        public List<InstanceDTO> getByReleasesNotIn(String appId, Env env, String clusterName, String namespaceName,
                                                    Set<Long> releaseIds) {

            InstanceDTO[]
                    instanceDTOs =
                    restTemplate.get(env,
                            "/instances/by-namespace-and-releases-not-in?appId={appId}&clusterName={clusterName" +
                                    "}&namespaceName={namespaceName}&releaseIds={releaseIds}",
                            InstanceDTO[].class, appId, clusterName, namespaceName, joiner.join(releaseIds));

            return Arrays.asList(instanceDTOs);
        }

        public PageDTO<InstanceDTO> getByNamespace(String appId, Env env, String clusterName, String namespaceName,
                                                   String instanceAppId,
                                                   int page, int size) {
            ResponseEntity<PageDTO<InstanceDTO>>
                    entity =
                    restTemplate.get(env,
                            "/instances/by-namespace?appId={appId}"
                                    + "&clusterName={clusterName}&namespaceName={namespaceName}&instanceAppId" +
                                    "={instanceAppId}"
                                    + "&page={page}&size={size}",
                            pageInstanceDtoType, appId, clusterName, namespaceName, instanceAppId, page, size);
            return entity.getBody();
        }

        public int getInstanceCountByNamespace(String appId, Env env, String clusterName, String namespaceName) {
            Integer
                    count =
                    restTemplate.get(env,
                            "/instances/by-namespace/count?appId={appId}&clusterName={clusterName}&namespaceName" +
                                    "={namespaceName}",
                            Integer.class, appId, clusterName, namespaceName);
            if (count == null) {
                return 0;
            }
            return count;
        }
    }

    /**
     * 调用AdminService的分支API
     */
    @Service
    public static class NamespaceBranchAPI extends API {

        /**
         * 创建分支
         */
        public NamespaceDTO createBranch(String appId, Env env, String clusterName,
                                         String namespaceName, String operator) {
            return restTemplate.post(
                    env,
                    "/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches?operator={operator}",
                    null,
                    NamespaceDTO.class,
                    appId,
                    clusterName,
                    namespaceName,
                    operator);
        }

        public NamespaceDTO findBranch(String appId, Env env, String clusterName,
                                       String namespaceName) {
            return restTemplate.get(env, "/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches",
                    NamespaceDTO.class, appId, clusterName, namespaceName);
        }

        public GrayReleaseRuleDTO findBranchGrayRules(String appId, Env env, String clusterName,
                                                      String namespaceName, String branchName) {
            return restTemplate
                    .get(env, "/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName" +
                                    "}/rules",
                            GrayReleaseRuleDTO.class, appId, clusterName, namespaceName, branchName);

        }

        /**
         * 请求adminService更新灰度规则
         */
        public void updateBranchGrayRules(String appId, Env env, String clusterName,
                                          String namespaceName, String branchName, GrayReleaseRuleDTO rules) {
            restTemplate.put(env,
                    "/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName}/rules",
                    rules, appId, clusterName, namespaceName, branchName);
        }

        public void deleteBranch(String appId, Env env, String clusterName,
                                 String namespaceName, String branchName, String operator) {
            restTemplate.delete(env,
                    "/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName}?operator" +
                            "={operator}",
                    appId, clusterName, namespaceName, branchName, operator);
        }
    }

    /**
     * adminService发布历史API
     */
    @Service
    public static class ReleaseHistoryAPI extends API {

        private ParameterizedTypeReference<PageDTO<ReleaseHistoryDTO>> type =
                new ParameterizedTypeReference<PageDTO<ReleaseHistoryDTO>>() {
                };


        public PageDTO<ReleaseHistoryDTO> findReleaseHistoriesByNamespace(String appId, Env env, String clusterName,
                                                                          String namespaceName, int page, int size) {
            return restTemplate.get(env,
                    "/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/releases/histories?page={page" +
                            "}&size={size}",
                    type, appId, clusterName, namespaceName, page, size).getBody();
        }

        /**
         * 查找发布历史
         *
         * @param env       环境
         * @param releaseId 发布id
         * @param operation 操作者
         * @param page      页数
         * @param size      每页数量
         * @return 发布历史分页结果
         */
        public PageDTO<ReleaseHistoryDTO> findByReleaseIdAndOperation(Env env, long releaseId, int operation, int page,
                                                                      int size) {
            return restTemplate.get(env,
                    "/releases/histories/by_release_id_and_operation?releaseId={releaseId}&operation={operation}&page" +
                            "={page}&size={size}",
                    type, releaseId, operation, page, size).getBody();
        }

        /**
         * 查找上一次的发布历史
         *
         * @param env               环境
         * @param previousReleaseId 上一次发布id
         * @param operation         操作者
         * @param page              分页
         * @param size              每页条数
         * @return 发布历史分页
         */
        public PageDTO<ReleaseHistoryDTO> findByPreviousReleaseIdAndOperation(Env env, long previousReleaseId,
                                                                              int operation, int page, int size) {
            return restTemplate.get(env,
                    "/releases/histories/by_previous_release_id_and_operation?previousReleaseId={releaseId}&operation" +
                            "={operation}&page={page}&size={size}",
                    type, previousReleaseId, operation, page, size).getBody();
        }

    }

}
