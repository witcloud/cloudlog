import request from '@/utils/request'
import type { Result, RegisterRequest, LoginRequest, LoginResponse, PasswordResetRequest } from './types/auth'

/**
 * 发送注册验证码
 * @param email 用户邮箱
 */
export function sendCode(email: string) {
  // 邮箱走请求体而不是查询参数：发码是有副作用的动作（一封真邮件 + 一次该邮箱的频控占用），
  // 挂在 GET 上会被浏览器预取与代理重试重复触发
  return request.post<unknown, Result<void>>('/auth/code', { email })
}

/**
 * 用户注册
 * @param data 注册信息
 */
export function register(data: RegisterRequest) {
  return request.post<unknown, Result<void>>('/auth/register', data)
}

/**
 * 用户登录
 * @param data 登录信息
 */
export function login(data: LoginRequest) {
  return request.post<unknown, Result<LoginResponse>>('/auth/login', data)
}

/**
 * 登出：服务端把该账号的失效水位推到此刻，作废该账号全部在存 token。
 * 幂等：未登录（上下文匿名）调用同样返回成功
 *
 * token 由调用方显式传进来并塞进 header：store 在发这一枪之前已经把本地凭据清了
 * （本地先清是对的——网络失败也不该把人留在"看起来已登录"的状态），
 * 请求拦截器那时取不到 token，不补这一笔后端只会按匿名处理，水位推不上去、旧 token 照用到过期。
 * silent：这一枪的失败不该再触发一次整站登出，也不该弹红字
 */
export function logout(token?: string) {
  return request.post<unknown, Result<void>>('/auth/logout', null, {
    silent: true,
    ...(token ? { headers: { Authorization: `Bearer ${token}` } } : {}),
  })
}

/**
 * 发送重置密码验证码
 * @param email 账号邮箱
 */
export function sendResetCode(email: string) {
  // 与注册发码同为一个请求体（后端共用 SendCodeRequest），邮箱格式在后端同样要过校验
  return request.post<unknown, Result<void>>('/auth/password/code', { email })
}

/**
 * 凭验证码重置密码
 * @param data 重置信息
 */
export function resetPassword(data: PasswordResetRequest) {
  return request.post<unknown, Result<void>>('/auth/password/reset', data)
}
