export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

export interface RegisterRequest {
  email: string
  username: string
  password: string
  code: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface PasswordResetRequest {
  email: string
  code: string
  newPassword: string
}

export interface UserInfo {
  /** 用户雪花 ID：字符串透传，不做数值运算 */
  id: string
  username: string
  role: string
  avatar: string
}

export interface LoginResponse {
  token: string
  user: UserInfo
}
