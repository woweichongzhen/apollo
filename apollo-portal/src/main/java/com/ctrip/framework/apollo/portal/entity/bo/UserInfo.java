package com.ctrip.framework.apollo.portal.entity.bo;

/**
 * 用户信息
 */
public class UserInfo {

    /**
     * 用户id
     */
    private String userId;

    /**
     * 用户名
     */
    private String name;

    /**
     * 用户邮箱
     */
    private String email;

    public UserInfo() {

    }

    public UserInfo(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof UserInfo) {

            if (o == this) {
                return true;
            }

            UserInfo anotherUser = (UserInfo) o;
            return userId.equals(anotherUser.userId);
        }
        return false;

    }
}
