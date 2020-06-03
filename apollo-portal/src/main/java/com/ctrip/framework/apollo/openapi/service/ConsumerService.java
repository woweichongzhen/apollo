package com.ctrip.framework.apollo.openapi.service;

import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.openapi.entity.Consumer;
import com.ctrip.framework.apollo.openapi.entity.ConsumerAudit;
import com.ctrip.framework.apollo.openapi.entity.ConsumerRole;
import com.ctrip.framework.apollo.openapi.entity.ConsumerToken;
import com.ctrip.framework.apollo.openapi.repository.ConsumerAuditRepository;
import com.ctrip.framework.apollo.openapi.repository.ConsumerRepository;
import com.ctrip.framework.apollo.openapi.repository.ConsumerRoleRepository;
import com.ctrip.framework.apollo.openapi.repository.ConsumerTokenRepository;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.po.Role;
import com.ctrip.framework.apollo.portal.service.RolePermissionService;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.ctrip.framework.apollo.portal.spi.UserService;
import com.ctrip.framework.apollo.portal.util.RoleUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import org.apache.commons.lang.time.FastDateFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 第三方应用服务
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Service
public class ConsumerService {

    /**
     * apache线程安全时间戳工具
     */
    private static final FastDateFormat TIMESTAMP_FORMAT = FastDateFormat.getInstance("yyyyMMddHHmmss");

    /**
     * | 分隔符
     */
    private static final Joiner KEY_JOINER = Joiner.on("|");

    private final UserInfoHolder userInfoHolder;
    private final ConsumerTokenRepository consumerTokenRepository;
    private final ConsumerRepository consumerRepository;
    private final ConsumerAuditRepository consumerAuditRepository;
    private final ConsumerRoleRepository consumerRoleRepository;
    private final PortalConfig portalConfig;
    private final RolePermissionService rolePermissionService;
    private final UserService userService;

    public ConsumerService(
            final UserInfoHolder userInfoHolder,
            final ConsumerTokenRepository consumerTokenRepository,
            final ConsumerRepository consumerRepository,
            final ConsumerAuditRepository consumerAuditRepository,
            final ConsumerRoleRepository consumerRoleRepository,
            final PortalConfig portalConfig,
            final RolePermissionService rolePermissionService,
            final UserService userService) {
        this.userInfoHolder = userInfoHolder;
        this.consumerTokenRepository = consumerTokenRepository;
        this.consumerRepository = consumerRepository;
        this.consumerAuditRepository = consumerAuditRepository;
        this.consumerRoleRepository = consumerRoleRepository;
        this.portalConfig = portalConfig;
        this.rolePermissionService = rolePermissionService;
        this.userService = userService;
    }

    /**
     * 创建第三方应用
     *
     * @param consumer 第三方应用
     * @return 创建后的
     */
    public Consumer createConsumer(Consumer consumer) {
        String appId = consumer.getAppId();

        // 校验存在，否则400
        Consumer managedConsumer = consumerRepository.findByAppId(appId);
        if (managedConsumer != null) {
            throw new BadRequestException("Consumer already exist");
        }

        // 校验用户，否则400
        String ownerName = consumer.getOwnerName();
        UserInfo owner = userService.findByUserId(ownerName);
        if (owner == null) {
            throw new BadRequestException(String.format("User does not exist. UserId = %s", ownerName));
        }
        consumer.setOwnerEmail(owner.getEmail());

        String operator = userInfoHolder.getUser().getUserId();
        consumer.setDataChangeCreatedBy(operator);
        consumer.setDataChangeLastModifiedBy(operator);

        // 保存
        return consumerRepository.save(consumer);
    }

    /**
     * 生成并保存第三方应用token
     *
     * @param consumer 第三方应用
     * @param expires  过期时间
     * @return 第三方应用token
     */
    public ConsumerToken generateAndSaveConsumerToken(Consumer consumer, Date expires) {
        // 校验第三方应用非空
        Preconditions.checkArgument(consumer != null, "Consumer can not be null");

        // 生成token，并保存
        ConsumerToken consumerToken = generateConsumerToken(consumer, expires);
        consumerToken.setId(0);
        return consumerTokenRepository.save(consumerToken);
    }

    /**
     * 通过应用编号获取第三方应用token
     *
     * @param appId 应用编号
     * @return 第三方应用token
     */
    public ConsumerToken getConsumerTokenByAppId(String appId) {
        Consumer consumer = consumerRepository.findByAppId(appId);
        if (consumer == null) {
            return null;
        }

        return consumerTokenRepository.findByConsumerId(consumer.getId());
    }

    /**
     * 通过token获取第三方应用id
     *
     * @param token token
     * @return 第三方应用id
     */
    public Long getConsumerIdByToken(String token) {
        if (Strings.isNullOrEmpty(token)) {
            return null;
        }
        // 通过token和过期时间查找
        ConsumerToken consumerToken = consumerTokenRepository.findTopByTokenAndExpiresAfter(token, new Date());
        return consumerToken == null ? null : consumerToken.getConsumerId();
    }

    /**
     * 通过第三方应用id获取应用信息
     *
     * @param consumerId 第三方应用id
     * @return 应用信息
     */
    public Consumer getConsumerByConsumerId(long consumerId) {
        return consumerRepository.findById(consumerId).orElse(null);
    }

    /**
     * 分配命名空间角色给第三方应用
     *
     * @param token         token
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @return 分配后的第三方应用角色
     */
    public List<ConsumerRole> assignNamespaceRoleToConsumer(String token, String appId, String namespaceName) {
        return assignNamespaceRoleToConsumer(token, appId, namespaceName, null);
    }

    /**
     * 分配命名空间角色给第三方应用
     *
     * @param token         token
     * @param appId         应用编号
     * @param namespaceName 命名空间名称
     * @param env           环境
     * @return 分配后的第三方应用角色
     */
    @Transactional
    public List<ConsumerRole> assignNamespaceRoleToConsumer(String token, String appId, String namespaceName,
                                                            String env) {
        // 获取第三方应用id
        Long consumerId = getConsumerIdByToken(token);
        if (consumerId == null) {
            throw new BadRequestException("Token is Illegal");
        }

        // 查找修改角色
        Role namespaceModifyRole = rolePermissionService.findRoleByRoleName(
                RoleUtils.buildModifyNamespaceRoleName(appId, namespaceName, env));
        // 查找发布角色
        Role namespaceReleaseRole = rolePermissionService.findRoleByRoleName(
                RoleUtils.buildReleaseNamespaceRoleName(appId, namespaceName, env));
        // 修改和发布命名空间的角色都不能为空
        if (namespaceModifyRole == null || namespaceReleaseRole == null) {
            throw new BadRequestException("Namespace's role does not exist. Please check whether namespace has " +
                    "created.");
        }

        long namespaceModifyRoleId = namespaceModifyRole.getId();
        long namespaceReleaseRoleId = namespaceReleaseRole.getId();
        // 查找是否已有角色
        ConsumerRole managedModifyRole = consumerRoleRepository.findByConsumerIdAndRoleId(consumerId,
                namespaceModifyRoleId);
        ConsumerRole managedReleaseRole = consumerRoleRepository.findByConsumerIdAndRoleId(consumerId,
                namespaceReleaseRoleId);
        if (managedModifyRole != null && managedReleaseRole != null) {
            return Arrays.asList(managedModifyRole, managedReleaseRole);
        }

        // 创建第三方应用角色中间表
        String operator = userInfoHolder.getUser().getUserId();
        ConsumerRole namespaceModifyConsumerRole = createConsumerRole(consumerId, namespaceModifyRoleId, operator);
        ConsumerRole namespaceReleaseConsumerRole = createConsumerRole(consumerId, namespaceReleaseRoleId, operator);
        ConsumerRole createdModifyConsumerRole = consumerRoleRepository.save(namespaceModifyConsumerRole);
        ConsumerRole createdReleaseConsumerRole = consumerRoleRepository.save(namespaceReleaseConsumerRole);

        return Arrays.asList(createdModifyConsumerRole, createdReleaseConsumerRole);
    }

    @Transactional
    public ConsumerRole assignAppRoleToConsumer(String token, String appId) {
        Long consumerId = getConsumerIdByToken(token);
        if (consumerId == null) {
            throw new BadRequestException("Token is Illegal");
        }

        Role masterRole = rolePermissionService.findRoleByRoleName(RoleUtils.buildAppMasterRoleName(appId));
        if (masterRole == null) {
            throw new BadRequestException("App's role does not exist. Please check whether app has created.");
        }

        long roleId = masterRole.getId();
        ConsumerRole managedModifyRole = consumerRoleRepository.findByConsumerIdAndRoleId(consumerId, roleId);
        if (managedModifyRole != null) {
            return managedModifyRole;
        }

        String operator = userInfoHolder.getUser().getUserId();
        ConsumerRole consumerRole = createConsumerRole(consumerId, roleId, operator);
        return consumerRoleRepository.save(consumerRole);
    }

    /**
     * 批量创建第三方应用审计
     *
     * @param consumerAudits 第三方应用审计
     */
    @Transactional
    public void createConsumerAudits(Iterable<ConsumerAudit> consumerAudits) {
        consumerAuditRepository.saveAll(consumerAudits);
    }

    /**
     * 直接创建第三方应用token
     *
     * @param entity token实体
     * @return 创建后的
     */
    @Transactional
    public ConsumerToken createConsumerToken(ConsumerToken entity) {
        entity.setId(0); //for protection

        return consumerTokenRepository.save(entity);
    }

    /**
     * 生成第三方应用token
     *
     * @param consumer 第三方应用
     * @param expires  token过期时间
     * @return 第三方应用token
     */
    private ConsumerToken generateConsumerToken(Consumer consumer, Date expires) {
        long consumerId = consumer.getId();
        String createdBy = userInfoHolder.getUser().getUserId();
        Date createdTime = new Date();

        // 生成token实体类
        ConsumerToken consumerToken = new ConsumerToken();
        consumerToken.setConsumerId(consumerId);
        consumerToken.setExpires(expires);
        consumerToken.setDataChangeCreatedBy(createdBy);
        consumerToken.setDataChangeCreatedTime(createdTime);
        consumerToken.setDataChangeLastModifiedBy(createdBy);
        consumerToken.setDataChangeLastModifiedTime(createdTime);

        // 生成加盐token
        generateAndEnrichToken(consumer, consumerToken);

        return consumerToken;
    }

    /**
     * 生成加盐token
     *
     * @param consumer      第三方应用
     * @param consumerToken 第三方应用token
     */
    void generateAndEnrichToken(Consumer consumer, ConsumerToken consumerToken) {
        // 校验应用非空
        Preconditions.checkArgument(consumer != null);

        // 设置token创建时间
        if (consumerToken.getDataChangeCreatedTime() == null) {
            consumerToken.setDataChangeCreatedTime(new Date());
        }

        // 设置token
        consumerToken.setToken(generateToken(
                consumer.getAppId(),
                consumerToken.getDataChangeCreatedTime(),
                portalConfig.consumerTokenSalt()));
    }

    /**
     * 生成token
     *
     * @param consumerAppId     第三方应用编号
     * @param generationTime    生成时间
     * @param consumerTokenSalt 加盐字符串
     * @return token
     */
    String generateToken(String consumerAppId, Date generationTime, String consumerTokenSalt) {
        return Hashing.sha1().hashString(
                KEY_JOINER.join(
                        consumerAppId,
                        TIMESTAMP_FORMAT.format(generationTime),
                        consumerTokenSalt),
                Charsets.UTF_8).toString();
    }

    /**
     * 创建第三方应用角色实体
     *
     * @param consumerId 第三方应用id
     * @param roleId     角色id
     * @param operator   操作者
     * @return 创建后的
     */
    ConsumerRole createConsumerRole(Long consumerId, Long roleId, String operator) {
        ConsumerRole consumerRole = new ConsumerRole();
        consumerRole.setConsumerId(consumerId);
        consumerRole.setRoleId(roleId);
        consumerRole.setDataChangeCreatedBy(operator);
        consumerRole.setDataChangeLastModifiedBy(operator);
        return consumerRole;
    }

}
