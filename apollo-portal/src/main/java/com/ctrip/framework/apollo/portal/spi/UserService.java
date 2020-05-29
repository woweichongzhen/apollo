package com.ctrip.framework.apollo.portal.spi;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;

import java.util.List;

/**
 * 用户业务服务
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface UserService {
    List<UserInfo> searchUsers(String keyword, int offset, int limit);

    /**
     * 通过用户id查找用户信息
     *
     * @param userId 用户id
     * @return 用户信息
     */
    UserInfo findByUserId(String userId);

    List<UserInfo> findByUserIds(List<String> userIds);

}
