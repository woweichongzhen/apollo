package com.ctrip.framework.apollo.portal.repository;

import com.ctrip.framework.apollo.portal.entity.po.UserPO;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * 用户数据层
 *
 * @author lepdou 2017-04-08
 */
public interface UserRepository extends PagingAndSortingRepository<UserPO, Long> {

    /**
     * 查找前20条用户
     *
     * @param enabled 是否启用，1启用，0禁用
     * @return 用户
     */
    List<UserPO> findFirst20ByEnabled(int enabled);

    /**
     * 根据用户名模糊查找用户
     *
     * @param username 用户名
     * @param enabled  是否启用，1启用，0禁用
     * @return 用户
     */
    List<UserPO> findByUsernameLikeAndEnabled(String username, int enabled);

    /**
     * 查找用户
     *
     * @param username 用户名
     * @return 用户
     */
    UserPO findByUsername(String username);

    /**
     * 批量查找用户
     *
     * @param userNames 用户名
     * @return 用户
     */
    List<UserPO> findByUsernameIn(List<String> userNames);
}
