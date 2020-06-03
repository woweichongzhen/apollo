package com.ctrip.framework.apollo.portal.entity.vo;

/**
 * 组织
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class Organization {

    /**
     * 组织id
     */
    private String orgId;

    /**
     * 组织名称
     */
    private String orgName;

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }
}
