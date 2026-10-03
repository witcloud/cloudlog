package com.ayun.mapper;

import com.ayun.entity.User;
import com.ayun.model.vo.AdminUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
    User findById(@Param("id") Long id);

    /**
     * 插入新用户
     */
    int insert(User user);

    /**
     * 更新用户信息
     */
    int update(User user);

    /**
     * 管理后台分页多条件查询用户列表（含文章数，不含密码）
     */
    List<AdminUserVO> findAdminList(@Param("keyword") String keyword, @Param("status") Integer status);

    /**
     * 统计用户总数
     */
    long countAll();

    /**
     * 更新用户状态（1-正常, 0-禁用）
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 记录本次登录的时刻与来源 IP，仅登录成功时调用
     */
    int updateLastLogin(@Param("id") Long id, @Param("ip") String ip);
}
