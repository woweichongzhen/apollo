package com.ctrip.framework.apollo.adminservice.aop;


import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.entity.Item;
import com.ctrip.framework.apollo.biz.entity.Namespace;
import com.ctrip.framework.apollo.biz.entity.NamespaceLock;
import com.ctrip.framework.apollo.biz.service.ItemService;
import com.ctrip.framework.apollo.biz.service.NamespaceLockService;
import com.ctrip.framework.apollo.biz.service.NamespaceService;
import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.dto.ItemDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.exception.ServiceException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;


/**
 * 一个namespace在一次发布中只能允许一个人修改配置
 * 通过数据库lock表来实现
 */
@Aspect
@Component
public class NamespaceAcquireLockAspect {

    private static final Logger logger = LoggerFactory.getLogger(NamespaceAcquireLockAspect.class);

    private final NamespaceLockService namespaceLockService;
    private final NamespaceService namespaceService;
    private final ItemService itemService;
    private final BizConfig bizConfig;

    public NamespaceAcquireLockAspect(
            final NamespaceLockService namespaceLockService,
            final NamespaceService namespaceService,
            final ItemService itemService,
            final BizConfig bizConfig) {
        this.namespaceLockService = namespaceLockService;
        this.namespaceService = namespaceService;
        this.itemService = itemService;
        this.bizConfig = bizConfig;
    }

    /**
     * 创建项的锁切面
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @param item          项
     */
    @Before("@annotation(PreAcquireNamespaceLock) && args(appId, clusterName, namespaceName, item, ..)")
    public void requireLockAdvice(String appId,
                                  String clusterName,
                                  String namespaceName,
                                  ItemDTO item) {
        acquireLock(appId, clusterName, namespaceName, item.getDataChangeLastModifiedBy());
    }

    /**
     * 更新项的锁切面
     *
     * @param appId         应用id
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @param itemId        项id
     * @param item          项
     */
    @Before("@annotation(PreAcquireNamespaceLock) && args(appId, clusterName, namespaceName, itemId, item, ..)")
    public void requireLockAdvice(String appId,
                                  String clusterName,
                                  String namespaceName,
                                  long itemId,
                                  ItemDTO item) {
        acquireLock(appId, clusterName, namespaceName, item.getDataChangeLastModifiedBy());
    }

    /**
     * 通过变更集修改项
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @param changeSet     改变项集合
     */
    @Before("@annotation(PreAcquireNamespaceLock) && args(appId, clusterName, namespaceName, changeSet, ..)")
    public void requireLockAdvice(String appId,
                                  String clusterName,
                                  String namespaceName,
                                  ItemChangeSets changeSet) {
        acquireLock(appId, clusterName, namespaceName, changeSet.getDataChangeLastModifiedBy());
    }

    /**
     * 删除项切面
     *
     * @param itemId   项id
     * @param operator 操作者
     */
    @Before("@annotation(PreAcquireNamespaceLock) && args(itemId, operator, ..)")
    public void RrequireLockAdvice(long itemId, String operator) {
        // 若项不存在，400
        Item item = itemService.findOne(itemId);
        if (item == null) {
            throw new BadRequestException("item not exist.");
        }
        acquireLock(item.getNamespaceId(), operator);
    }

    /**
     * 尝试获取锁定
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @param currentUser   当前用户
     */
    void acquireLock(String appId,
                     String clusterName,
                     String namespaceName,
                     String currentUser) {
        // 锁开关关闭，直接返回
        if (bizConfig.isNamespaceLockSwitchOff()) {
            return;
        }

        // 查找指定的命名空间
        Namespace namespace = namespaceService.findOne(appId, clusterName, namespaceName);

        // 尝试获取锁
        acquireLock(namespace, currentUser);
    }

    /**
     * 尝试获取锁定
     *
     * @param namespaceId 命名空间id
     * @param currentUser 当前用户
     */
    void acquireLock(long namespaceId, String currentUser) {
        // 锁开关关闭，直接返回
        if (bizConfig.isNamespaceLockSwitchOff()) {
            return;
        }

        // 查找指定的命名空间
        Namespace namespace = namespaceService.findOne(namespaceId);

        // 尝试获取锁
        acquireLock(namespace, currentUser);

    }

    /**
     * 尝试获取锁
     *
     * @param namespace   命名空间
     * @param currentUser 当前用户
     */
    private void acquireLock(Namespace namespace, String currentUser) {
        // 命名空间不存在，400
        if (namespace == null) {
            throw new BadRequestException("namespace not exist.");
        }

        long namespaceId = namespace.getId();

        // 判断锁是否存在
        NamespaceLock namespaceLock = namespaceLockService.findLock(namespaceId);
        if (namespaceLock == null) {
            try {
                // 锁不存在尝试加锁
                tryLock(namespaceId, currentUser);
                //lock success
            } catch (DataIntegrityViolationException e) {
                // 保存失败，即锁定失败，唯一索引冲突
                //lock fail
                // 锁定失败，说明存在锁，查找到对应的锁记录
                namespaceLock = namespaceLockService.findLock(namespaceId);
                // 已存在锁，判断是否为管理员获取的锁
                checkLock(namespace, namespaceLock, currentUser);
            } catch (Exception e) {
                logger.error("try lock error", e);
                throw e;
            }
        } else {
            //check lock owner is current user
            // 已存在锁，检查是否为管理员获取的锁
            checkLock(namespace, namespaceLock, currentUser);
        }
    }

    /**
     * 尝试获取锁
     *
     * @param namespaceId 命名空间id
     * @param user        当前用户
     */
    private void tryLock(long namespaceId, String user) {
        NamespaceLock lock = new NamespaceLock();
        lock.setNamespaceId(namespaceId);
        lock.setDataChangeCreatedBy(user);
        lock.setDataChangeLastModifiedBy(user);
        namespaceLockService.tryLock(lock);
    }

    /**
     * 检查锁拥有者是否为管理员
     *
     * @param namespace     命名空间
     * @param namespaceLock 命名空间锁
     * @param currentUser   当前用户
     */
    private void checkLock(Namespace namespace, NamespaceLock namespaceLock,
                           String currentUser) {
        // 锁不存在，500异常
        if (namespaceLock == null) {
            throw new ServiceException(
                    String.format("Check lock for %s failed, please retry.", namespace.getNamespaceName()));
        }

        // 拥有锁用户非管理员，400异常
        String lockOwner = namespaceLock.getDataChangeCreatedBy();
        if (!lockOwner.equals(currentUser)) {
            throw new BadRequestException(
                    "namespace:" + namespace.getNamespaceName() + " is modified by " + lockOwner);
        }
    }


}
