package com.ctrip.framework.apollo.portal.spi.springsecurity;

import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.po.UserPO;
import com.ctrip.framework.apollo.portal.repository.UserRepository;
import com.ctrip.framework.apollo.portal.spi.UserService;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于spring安全实现的用户服务
 *
 * @author lepdou 2017-03-10
 */
public class SpringSecurityUserService implements UserService {

    /**
     * 密码加密器
     */
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 认证信息提供接口
     */
    private List<GrantedAuthority> authorities;

    @Autowired
    private JdbcUserDetailsManager userDetailsManager;

    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    public void init() {
        authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_user"));
    }

    /**
     * 创建或更新用户
     *
     * @param user 用户
     */
    @Transactional
    public void createOrUpdate(UserPO user) {
        String username = user.getUsername();

        // 创建spring安全用户，并更新
        User userDetails = new User(username, encoder.encode(user.getPassword()), authorities);
        if (userDetailsManager.userExists(username)) {
            userDetailsManager.updateUser(userDetails);
        } else {
            userDetailsManager.createUser(userDetails);
        }

        // 通过用户名获取创建的用户，保存自定义的属性，比如email
        UserPO managedUser = userRepository.findByUsername(username);
        managedUser.setEmail(user.getEmail());
        userRepository.save(managedUser);
    }

    @Override
    public List<UserInfo> searchUsers(String keyword, int offset, int limit) {
        List<UserPO> users;
        if (StringUtils.isEmpty(keyword)) {
            // 查找前20条用户
            users = userRepository.findFirst20ByEnabled(1);
        } else {
            // 根据关键字查找用户
            users = userRepository.findByUsernameLikeAndEnabled("%" + keyword + "%", 1);
        }

        List<UserInfo> result = Lists.newArrayList();
        if (CollectionUtils.isEmpty(users)) {
            return result;
        }

        result.addAll(users.stream()
                .map(UserPO::toUserInfo)
                .collect(Collectors.toList()));

        return result;
    }

    @Override
    public UserInfo findByUserId(String userId) {
        UserPO userpo = userRepository.findByUsername(userId);
        return userpo == null
                ? null
                : userpo.toUserInfo();
    }

    @Override
    public List<UserInfo> findByUserIds(List<String> userIds) {
        List<UserPO> users = userRepository.findByUsernameIn(userIds);

        if (CollectionUtils.isEmpty(users)) {
            return Collections.emptyList();
        }

        List<UserInfo> result = Lists.newArrayList();

        result.addAll(users.stream()
                .map(UserPO::toUserInfo)
                .collect(Collectors.toList()));

        return result;
    }


}
