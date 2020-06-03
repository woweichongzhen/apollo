package com.ctrip.framework.apollo.portal.spi;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;

import java.util.List;

/**
 * 用户接口服务
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface UserService {

    /**
     * 查找用户
     *
     * @param keyword 关键字
     * @param offset  偏移量
     * @param limit   个数
     * @return 用户信息
     */
    List<UserInfo> searchUsers(String keyword, int offset, int limit);

    /**
     * 通过用户id查找用户信息
     *
     * @param userId 用户id
     * @return 用户信息
     */
    UserInfo findByUserId(String userId);

    /**
     * 批量查找用户信息
     *
     * @param userIds 用户id
     * @return 用户集合
     */
    List<UserInfo> findByUserIds(List<String> userIds);

}
