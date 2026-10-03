package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.entity.User;
import com.ayun.mapper.UserMapper;
import com.ayun.model.dto.AdminUserQueryDTO;
import com.ayun.model.vo.AdminUserVO;
import com.ayun.model.vo.PageResult;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理后台用户业务逻辑类
 */
@Service
public class AdminUserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 分页多条件查询注册用户
     */
    public PageResult<AdminUserVO> getUsers(AdminUserQueryDTO queryDTO) {
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        List<AdminUserVO> list = userMapper.findAdminList(queryDTO.getKeyword(), queryDTO.getStatus());
        // last_login_ip 原始值只在库侧：响应体一律下发脱敏地址（IpMaskUtils 的硬约束，
        // 登录日志与访问日志页签都照办了，用户列表不能是例外）
        for (AdminUserVO vo : list) {
            vo.setLastLoginIp(com.ayun.common.IpMaskUtils.mask(vo.getLastLoginIp()));
        }
        PageInfo<AdminUserVO> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), pageInfo.getPageNum(), pageInfo.getPageSize(), list);
    }

    /**
     * 禁用/启用用户。管理员账号与当前登录账号不允许禁用，避免锁死系统。
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("非法的用户状态");
        }
        User target = userMapper.findById(id);
        if (target == null) {
            throw new BizException("用户不存在");
        }
        if ("admin".equalsIgnoreCase(target.getRole())) {
            throw new BizException("不能禁用管理员账号");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User current = userMapper.findByEmail(email);
        if (current != null && current.getId().equals(id)) {
            throw new BizException("不能禁用当前登录账号");
        }

        userMapper.updateStatus(id, status);
    }
}
