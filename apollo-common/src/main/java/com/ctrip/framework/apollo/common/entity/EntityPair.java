package com.ctrip.framework.apollo.common.entity;

/**
 * 实体键值对
 *
 * @param <E> 实体
 */
public class EntityPair<E> {

    /**
     * 上一个实体
     */
    private E firstEntity;

    /**
     * 第二个实体
     */
    private E secondEntity;

    public EntityPair(E firstEntity, E secondEntity) {
        this.firstEntity = firstEntity;
        this.secondEntity = secondEntity;
    }

    public E getFirstEntity() {
        return firstEntity;
    }

    public void setFirstEntity(E firstEntity) {
        this.firstEntity = firstEntity;
    }

    public E getSecondEntity() {
        return secondEntity;
    }

    public void setSecondEntity(E secondEntity) {
        this.secondEntity = secondEntity;
    }
}
