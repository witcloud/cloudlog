export interface Result<T = any> {
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

export interface UserInfo {
  id: number
  username: string
  role: string
  avatar: string
}

export interface LoginResponse {
  token: string
  user: UserInfo
}
