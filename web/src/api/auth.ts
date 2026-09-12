import request from '@/utils/request'
import type { Result, RegisterRequest, LoginRequest, LoginResponse } from './types/auth'

/**
 * 发送注册验证码
 * @param email 用户邮箱
 */
export function sendCode(email: string) {
  return request.get<any, Result<void>>('/auth/code', {
    params: { email },
  })
}

/**
 * 用户注册
 * @param data 注册信息
 */
export function register(data: RegisterRequest) {
  return request.post<any, Result<void>>('/auth/register', data)
}

/**
 * 用户登录
 * @param data 登录信息
 */
export function login(data: LoginRequest) {
  return request.post<any, Result<LoginResponse>>('/auth/login', data)
}
