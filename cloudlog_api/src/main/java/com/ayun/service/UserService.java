package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.common.NotFoundException;
import com.ayun.dto.EmailChangeRequest;
import com.ayun.dto.PasswordChangeRequest;
import com.ayun.dto.UserProfileUpdateDTO;
import com.ayun.entity.User;
import com.ayun.mapper.UserMapper;
import com.ayun.security.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户业务逻辑类
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final VerificationCodeService verificationCodeService;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;

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
            throw new BizException("未找到当前用户信息");
        }

        // 2. 仅更新用户名、个人简介与职业
        user.setUsername(profileUpdateDTO.getUsername());
        user.setBio(profileUpdateDTO.getBio());
        user.setOccupation(profileUpdateDTO.getOccupation());

        // 3. 调用 Mapper 更新数据库
        userMapper.update(user);
    }

    /**
     * 把已上传到对象存储的头像地址绑定到当前登录用户。
     * 头像上传的写库步骤原先长在 controller 里，这里收回服务层，身份仍只取安全上下文。
     * @param email 当前用户邮箱
     * @param avatarUrl 头像完整访问地址
     */
    @Transactional
    public void updateAvatar(String email, String avatarUrl) {
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new NotFoundException("未找到当前用户信息");
        }
        user.setAvatar(avatarUrl);
        userMapper.update(user);
    }

    /**
     * 向待绑定的新邮箱发送修改验证码。唯一性先于投递，避免向已占用邮箱发信。
     * @param newEmail 新邮箱
     */
    public void sendEmailChangeCode(String newEmail) {
        if (userMapper.findByEmail(newEmail) != null) {
            throw new BizException("该邮箱已被注册");
        }
        verificationCodeService.sendEmailChangeCode(newEmail);
    }

    /**
     * 校验当前口令与新邮箱验证码后改绑登录邮箱。
     * 唯一性排在验证码之前：验证码一次性、命中即销毁，先判占用可避免改绑失败时白烧掉一次验证码。
     * @param email 当前用户邮箱
     * @param emailChangeRequest 修改邮箱参数
     */
    @Transactional
    public void changeEmail(String email, EmailChangeRequest emailChangeRequest) {
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new BizException("未找到当前用户信息");
        }
        if (!passwordEncoder.matches(emailChangeRequest.getPassword(), user.getPassword())) {
            throw new BizException("当前密码错误");
        }
        String newEmail = emailChangeRequest.getNewEmail();
        if (userMapper.findByEmail(newEmail) != null) {
            throw new BizException("该邮箱已被注册");
        }
        if (!verificationCodeService.verifyEmailChangeCode(newEmail, emailChangeRequest.getCode())) {
            throw new BizException("验证码错误或已过期");
        }

        // 只带 id 与 email：UserMapper.xml 的 <set> 跳过 null，避免把口令等其余列原值回写
        userMapper.update(User.builder().id(user.getId()).email(newEmail).build());
    }

    /**
     * 校验当前口令后改换口令。
     * 与改邮箱的差别在失效方式：邮箱是 JWT 的 subject，改绑后旧 token 自然查不到用户；口令不是身份标识，
     * 旧 token 依旧验得过签名，所以改完必须显式把该账号的失效水位推到此刻，否则「改密即踢掉旧会话」落不了地
     * @param email 当前用户邮箱
     * @param passwordChangeRequest 当前口令与新口令
     */
    @Transactional
    public void changePassword(String email, PasswordChangeRequest passwordChangeRequest) {
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new BizException("未找到当前用户信息");
        }
        if (!passwordEncoder.matches(passwordChangeRequest.getOldPassword(), user.getPassword())) {
            throw new BizException("当前密码错误");
        }
        // 新口令与当前口令相同即拒绝：这一步不只是没必要，它还会白踢一次会话——改完人被登出，密码却没变
        if (passwordEncoder.matches(passwordChangeRequest.getNewPassword(), user.getPassword())) {
            throw new BizException("新密码不能与当前密码相同");
        }

        // 只带 id 与 password：UserMapper.xml 的 <set> 跳过 null，避免把 email 等其余列原值回写
        userMapper.update(User.builder()
                .id(user.getId())
                .password(passwordEncoder.encode(passwordChangeRequest.getNewPassword()))
                .build());
        tokenRevocationService.revokeBeforeNow(email);
    }
}
