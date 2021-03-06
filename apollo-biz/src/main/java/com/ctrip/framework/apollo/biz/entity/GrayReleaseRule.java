package com.ctrip.framework.apollo.biz.entity;

import com.ctrip.framework.apollo.common.entity.BaseEntity;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 灰度发布规则
 */
@Entity
@Table(name = "GrayReleaseRule")
@SQLDelete(sql = "Update GrayReleaseRule set isDeleted = 1 where id = ?")
@Where(clause = "isDeleted = 0")
public class GrayReleaseRule extends BaseEntity {

    /**
     * 应用编号
     */
    @Column(name = "appId", nullable = false)
    private String appId;

    /**
     * 集群名称
     */
    @Column(name = "ClusterName", nullable = false)
    private String clusterName;

    /**
     * 命名空间名称
     */
    @Column(name = "NamespaceName", nullable = false)
    private String namespaceName;

    /**
     * 分支名称
     */
    @Column(name = "BranchName", nullable = false)
    private String branchName;

    /**
     * 规则
     * 将 {@link com.ctrip.framework.apollo.common.dto.GrayReleaseRuleItemDTO} 的数组 JSON 格式化
     */
    @Column(name = "Rules")
    private String rules;

    /**
     * 发布id
     * <p>
     * 有两种情况：
     * 1、当灰度已经发布，则指向对应的最新的 Release 对象的编号
     * 2、当灰度还未发布，等于 0 。等到灰度发布后，更新为对应的 Release 对象的编号
     */
    @Column(name = "releaseId", nullable = false)
    private Long releaseId;

    /**
     * 分支状态
     * <p>
     * {@link com.ctrip.framework.apollo.common.constants.NamespaceBranchStatus} 枚举
     */
    @Column(name = "BranchStatus", nullable = false)
    private int branchStatus;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getRules() {
        return rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(Long releaseId) {
        this.releaseId = releaseId;
    }

    public int getBranchStatus() {
        return branchStatus;
    }

    public void setBranchStatus(int branchStatus) {
        this.branchStatus = branchStatus;
    }
}
