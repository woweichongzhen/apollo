package com.ctrip.framework.apollo.common.dto;


import com.google.common.collect.Sets;

import java.util.Set;

/**
 * 灰度发布规则dto
 */
public class GrayReleaseRuleDTO extends BaseDTO {

    private String appId;

    private String clusterName;

    private String namespaceName;

    private String branchName;

    /**
     * 灰度发布规则项dto
     */
    private Set<GrayReleaseRuleItemDTO> ruleItems;

    /**
     * 发布id
     */
    private Long releaseId;

    public GrayReleaseRuleDTO(String appId, String clusterName, String namespaceName, String branchName) {
        this.appId = appId;
        this.clusterName = clusterName;
        this.namespaceName = namespaceName;
        this.branchName = branchName;
        this.ruleItems = Sets.newHashSet();
    }

    public String getAppId() {
        return appId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public String getBranchName() {
        return branchName;
    }

    public Set<GrayReleaseRuleItemDTO> getRuleItems() {
        return ruleItems;
    }

    public void setRuleItems(Set<GrayReleaseRuleItemDTO> ruleItems) {
        this.ruleItems = ruleItems;
    }

    public void addRuleItem(GrayReleaseRuleItemDTO ruleItem) {
        this.ruleItems.add(ruleItem);
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(Long releaseId) {
        this.releaseId = releaseId;
    }
}

