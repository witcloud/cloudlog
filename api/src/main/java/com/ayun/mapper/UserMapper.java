package com.ayun.mapper;

import com.ayun.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户信息 Mapper 接口
 */
@Mapper
public interface UserMapper {
    /**
     * 根据邮箱查询用户
     */
    User findByEmail(@Param("email") String email);
    
    /**
     * 根据用户名查询用户
     */
    User findByUsername(@Param("username") String username);
    
    /**
     * 根据 ID 查询用户
     */
    User findById(@Param("id") Integer id);
    
    /**
     * 插入新用户
     */
    int insert(User user);
    
    /**
     * 更新用户信息
     */
    int update(User user);
}
