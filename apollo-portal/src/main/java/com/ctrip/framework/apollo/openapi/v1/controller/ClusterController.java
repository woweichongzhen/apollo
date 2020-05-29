package com.ctrip.framework.apollo.openapi.v1.controller;

import com.ctrip.framework.apollo.common.dto.ClusterDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.InputValidator;
import com.ctrip.framework.apollo.common.utils.RequestPrecondition;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.openapi.dto.OpenClusterDTO;
import com.ctrip.framework.apollo.openapi.util.OpenApiBeanUtils;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.service.ClusterService;
import com.ctrip.framework.apollo.portal.spi.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Objects;

/**
 * 开放集群api
 */
@RestController("openapiClusterController")
@RequestMapping("/openapi/v1/envs/{env}")
public class ClusterController {

    private final ClusterService clusterService;
    private final UserService userService;

    public ClusterController(final ClusterService clusterService, final UserService userService) {
        this.clusterService = clusterService;
        this.userService = userService;
    }

    @GetMapping(value = "apps/{appId}/clusters/{clusterName:.+}")
    public OpenClusterDTO loadCluster(@PathVariable("appId") String appId, @PathVariable String env,
                                      @PathVariable("clusterName") String clusterName) {

        ClusterDTO clusterDTO = clusterService.loadCluster(appId, Env.fromString(env), clusterName);
        return clusterDTO == null ? null : OpenApiBeanUtils.transformFromClusterDTO(clusterDTO);
    }

    /**
     * v1版本创建指定环境的集群
     */
    @PreAuthorize(value = "@consumerPermissionValidator.hasCreateClusterPermission(#request, #appId)")
    @PostMapping(value = "apps/{appId}/clusters")
    public OpenClusterDTO createCluster(@PathVariable String appId, @PathVariable String env,
                                        @Valid @RequestBody OpenClusterDTO cluster, HttpServletRequest request) {
        // 校验appid相同
        if (!Objects.equals(appId, cluster.getAppId())) {
            throw new BadRequestException(String.format(
                    "AppId not equal. AppId in path = %s, AppId in payload = %s", appId, cluster.getAppId()));
        }

        // 集群名和创建者不能为空
        String clusterName = cluster.getName();
        String operator = cluster.getDataChangeCreatedBy();
        RequestPrecondition.checkArguments(!StringUtils.isContainEmpty(clusterName, operator),
                "name and dataChangeCreatedBy should not be null or empty");

        // 集群名要符合输入
        if (!InputValidator.isValidClusterNamespace(clusterName)) {
            throw new BadRequestException(
                    String.format("Invalid ClusterName format: %s", InputValidator.INVALID_CLUSTER_NAMESPACE_MESSAGE));
        }

        // 创建者要存在
        if (userService.findByUserId(operator) == null) {
            throw new BadRequestException("User " + operator + " doesn't exist!");
        }

        // 转换dto，创建对应环境的集群
        ClusterDTO toCreate = OpenApiBeanUtils.transformToClusterDTO(cluster);
        ClusterDTO createdClusterDTO = clusterService.createCluster(Env.fromString(env), toCreate);

        return OpenApiBeanUtils.transformFromClusterDTO(createdClusterDTO);
    }

}
