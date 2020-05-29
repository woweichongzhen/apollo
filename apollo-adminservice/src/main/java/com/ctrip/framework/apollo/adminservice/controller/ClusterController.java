package com.ctrip.framework.apollo.adminservice.controller;

import com.ctrip.framework.apollo.biz.entity.Cluster;
import com.ctrip.framework.apollo.biz.service.ClusterService;
import com.ctrip.framework.apollo.common.dto.ClusterDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.exception.NotFoundException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.core.ConfigConsts;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * adminservice集群接口
 */
@RestController
public class ClusterController {

    private final ClusterService clusterService;

    public ClusterController(final ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    /**
     * 创建集群
     *
     * @param appId                      应用编号
     * @param autoCreatePrivateNamespace 是否自动创建私有命名空间
     * @param dto                        集群dto
     * @return 创建完成的集群dto
     */
    @PostMapping("/apps/{appId}/clusters")
    public ClusterDTO create(@PathVariable("appId") String appId,
                             @RequestParam(value = "autoCreatePrivateNamespace", defaultValue = "true") boolean autoCreatePrivateNamespace,
                             @Valid @RequestBody ClusterDTO dto) {
        // 转换dto为实体
        Cluster entity = BeanUtils.transform(Cluster.class, dto);
        // 校验集群是否存在
        Cluster managedEntity = clusterService.findOne(appId, entity.getName());
        if (managedEntity != null) {
            throw new BadRequestException("cluster already exist.");
        }

        // 根据参数，决定是否创建集群命名空间
        if (autoCreatePrivateNamespace) {
            // 创建集群，并根据应用命名空间，创建cluster的命名空间
            entity = clusterService.saveWithInstanceOfAppNamespaces(entity);
        } else {
            // 创建集群而不实例化应用命名空间
            entity = clusterService.saveWithoutInstanceOfAppNamespaces(entity);
        }

        // 转换，返回
        return BeanUtils.transform(ClusterDTO.class, entity);
    }

    @DeleteMapping("/apps/{appId}/clusters/{clusterName:.+}")
    public void delete(@PathVariable("appId") String appId,
                       @PathVariable("clusterName") String clusterName, @RequestParam String operator) {

        Cluster entity = clusterService.findOne(appId, clusterName);

        if (entity == null) {
            throw new NotFoundException("cluster not found for clusterName " + clusterName);
        }

        if (ConfigConsts.CLUSTER_NAME_DEFAULT.equals(entity.getName())) {
            throw new BadRequestException("can not delete default cluster!");
        }

        clusterService.delete(entity.getId(), operator);
    }

    @GetMapping("/apps/{appId}/clusters")
    public List<ClusterDTO> find(@PathVariable("appId") String appId) {
        List<Cluster> clusters = clusterService.findParentClusters(appId);
        return BeanUtils.batchTransform(ClusterDTO.class, clusters);
    }

    @GetMapping("/apps/{appId}/clusters/{clusterName:.+}")
    public ClusterDTO get(@PathVariable("appId") String appId,
                          @PathVariable("clusterName") String clusterName) {
        Cluster cluster = clusterService.findOne(appId, clusterName);
        if (cluster == null) {
            throw new NotFoundException("cluster not found for name " + clusterName);
        }
        return BeanUtils.transform(ClusterDTO.class, cluster);
    }

    @GetMapping("/apps/{appId}/cluster/{clusterName}/unique")
    public boolean isAppIdUnique(@PathVariable("appId") String appId,
                                 @PathVariable("clusterName") String clusterName) {
        return clusterService.isClusterNameUnique(appId, clusterName);
    }
}
