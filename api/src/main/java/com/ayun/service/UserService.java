package com.ayun.service;

import com.ayun.dto.UserProfileUpdateDTO;
import com.ayun.entity.User;
import com.ayun.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户业务逻辑类
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /**
     * 根据邮箱查询用户
     * @param email 用户邮箱
     * @return 用户对象
     */
    public User findByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    /**
     * 更新用户资料
     * @param email 当前用户邮箱
     * @param profileUpdateDTO 更新参数
     */
    @Transactional
    public void updateProfile(String email, UserProfileUpdateDTO profileUpdateDTO) {
        // 1. 查询完整用户对象
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("未找到当前用户信息");
        }

        // 2. 仅更新用户名和个人简介
        user.setUsername(profileUpdateDTO.getUsername());
        user.setBio(profileUpdateDTO.getBio());

        // 3. 调用 Mapper 更新数据库
        userMapper.update(user);
    }
}
