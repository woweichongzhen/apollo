package com.ctrip.framework.apollo.portal.controller;

import com.ctrip.framework.apollo.common.dto.NamespaceDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.openapi.entity.Consumer;
import com.ctrip.framework.apollo.openapi.entity.ConsumerRole;
import com.ctrip.framework.apollo.openapi.entity.ConsumerToken;
import com.ctrip.framework.apollo.openapi.service.ConsumerService;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
@RestController
public class ConsumerController {

    /**
     * 默认过期时间，2099年
     */
    private static final Date DEFAULT_EXPIRES = new GregorianCalendar(2099, Calendar.JANUARY, 1).getTime();

    private final ConsumerService consumerService;

    public ConsumerController(final ConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    /**
     * 创建第三方应用
     *
     * @param consumer 第三方应用
     * @param expires  过期时间
     * @return 第三方应用token
     */
    @Transactional
    @PreAuthorize(value = "@permissionValidator.isSuperAdmin()")
    @PostMapping(value = "/consumers")
    public ConsumerToken createConsumer(@RequestBody Consumer consumer,
                                        @RequestParam(value = "expires", required = false)
                                        @DateTimeFormat(pattern = "yyyyMMddHHmmss") Date expires) {
        // 参数非空，否则400
        if (StringUtils.isContainEmpty(
                consumer.getAppId(),
                consumer.getName(),
                consumer.getOwnerName(),
                consumer.getOrgId())) {
            throw new BadRequestException("Params(appId、name、ownerName、orgId) can not be empty.");
        }

        // 保存第三方应用
        Consumer createdConsumer = consumerService.createConsumer(consumer);

        // 生成过期时间 2099 年，并保存第三方应用
        if (Objects.isNull(expires)) {
            expires = DEFAULT_EXPIRES;
        }
        return consumerService.generateAndSaveConsumerToken(createdConsumer, expires);
    }

    @GetMapping(value = "/consumers/by-appId")
    public ConsumerToken getConsumerTokenByAppId(@RequestParam String appId) {
        return consumerService.getConsumerTokenByAppId(appId);
    }

    /**
     * 分配命名空间角色给第三方应用
     */
    @PreAuthorize(value = "@permissionValidator.isSuperAdmin()")
    @PostMapping(value = "/consumers/{token}/assign-role")
    public List<ConsumerRole> assignNamespaceRoleToConsumer(@PathVariable String token,
                                                            @RequestParam String type,
                                                            @RequestParam(required = false) String envs,
                                                            @RequestBody NamespaceDTO namespace) {
        String appId = namespace.getAppId();
        String namespaceName = namespace.getNamespaceName();

        // 校验参数
        if (StringUtils.isEmpty(appId)) {
            throw new BadRequestException("Params(AppId) can not be empty.");
        }

        // 分配应用角色
        if (Objects.equals("AppRole", type)) {
            return Collections.singletonList(consumerService.assignAppRoleToConsumer(token, appId));
        }
        if (StringUtils.isEmpty(namespaceName)) {
            throw new BadRequestException("Params(NamespaceName) can not be empty.");
        }
        if (null != envs) {
            String[] envArray = envs.split(",");
            List<String> envList = Lists.newArrayList();
            // validate env parameter
            for (String env : envArray) {
                if (Strings.isNullOrEmpty(env)) {
                    continue;
                }
                if (Env.UNKNOWN.equals(Env.transformEnv(env))) {
                    throw new BadRequestException(String.format("env: %s is illegal", env));
                }
                envList.add(env);
            }

            // 分配指定环境
            List<ConsumerRole> consumeRoles = new ArrayList<>();
            for (String env : envList) {
                consumeRoles.addAll(consumerService.assignNamespaceRoleToConsumer(token, appId, namespaceName, env));
            }
            return consumeRoles;
        }

        // 分配命名空间角色
        return consumerService.assignNamespaceRoleToConsumer(token, appId, namespaceName);
    }


}
