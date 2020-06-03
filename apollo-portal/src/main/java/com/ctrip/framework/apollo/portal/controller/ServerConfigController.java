package com.ctrip.framework.apollo.portal.controller;


import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.portal.entity.po.ServerConfig;
import com.ctrip.framework.apollo.portal.repository.ServerConfigRepository;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Objects;

/**
 * 配置中心本身需要一些配置,这些配置放在数据库里面
 */
@RestController
public class ServerConfigController {

    private final ServerConfigRepository serverConfigRepository;

    private final UserInfoHolder userInfoHolder;

    public ServerConfigController(final ServerConfigRepository serverConfigRepository,
                                  final UserInfoHolder userInfoHolder) {
        this.serverConfigRepository = serverConfigRepository;
        this.userInfoHolder = userInfoHolder;
    }

    /**
     * 创建或更新服务配置
     *
     * @param serverConfig 服务配置
     * @return 修改后的服务配置
     */
    @PreAuthorize(value = "@permissionValidator.isSuperAdmin()")
    @PostMapping("/server/config")
    public ServerConfig createOrUpdate(@Valid @RequestBody ServerConfig serverConfig) {
        String modifiedBy = userInfoHolder.getUser().getUserId();

        ServerConfig storedConfig = serverConfigRepository.findByKey(serverConfig.getKey());

        if (Objects.isNull(storedConfig)) {
            //为空，设置ID为0，jpa执行新增操作
            serverConfig.setDataChangeCreatedBy(modifiedBy);
            serverConfig.setDataChangeLastModifiedBy(modifiedBy);
            serverConfig.setId(0L);
            return serverConfigRepository.save(serverConfig);
        }
        // 修改
        BeanUtils.copyEntityProperties(serverConfig, storedConfig);
        storedConfig.setDataChangeLastModifiedBy(modifiedBy);
        return serverConfigRepository.save(storedConfig);
    }

    /**
     * 加载服务配置
     *
     * @param key 键
     * @return 服务配置
     */
    @PreAuthorize(value = "@permissionValidator.isSuperAdmin()")
    @GetMapping("/server/config/{key:.+}")
    public ServerConfig loadServerConfig(@PathVariable String key) {
        return serverConfigRepository.findByKey(key);
    }

}
