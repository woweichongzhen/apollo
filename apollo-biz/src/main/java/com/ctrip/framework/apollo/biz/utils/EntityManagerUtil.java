package com.ctrip.framework.apollo.biz.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryAccessor;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityManagerFactory;

/**
 * jpa实体管理工具
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Component
public class EntityManagerUtil extends EntityManagerFactoryAccessor {
    private static final Logger logger = LoggerFactory.getLogger(EntityManagerUtil.class);

    /**
     * 关闭实体管理器
     * <p>
     * 请谨慎使用！这仅适用于异步请求，在异步请求完成之前，Spring不会关闭实体管理器。
     * 所以需要手动关闭
     * <p>
     * close the entity manager.
     * Use it with caution! This is only intended for use with async request, which Spring won't
     * close the entity manager until the async request is finished.
     */
    public void closeEntityManager() {
        EntityManagerFactory entityManagerFactory = getEntityManagerFactory();
        if (null == entityManagerFactory) {
            return;
        }

        // 获取事务管理器持有者
        EntityManagerHolder emHolder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(entityManagerFactory);
        if (emHolder == null) {
            return;
        }
        logger.debug("Closing JPA EntityManager in EntityManagerUtil");

        // 关闭JPA 事务管理器
        EntityManagerFactoryUtils.closeEntityManager(emHolder.getEntityManager());
    }
}
