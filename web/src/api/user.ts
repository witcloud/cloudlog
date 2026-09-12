import request from '@/utils/request'
import type { Result } from './types/auth'

export interface UserProfile {
  username: string
  avatar: string
  bio: string
  email?: string
  articlesCount: number
  likesCount: number
  followersCount: number
}

export interface EditUserProfile {
  username: string
  avatar: string
  bio: string
  email?: string
}

/**
 * 获取用户资料
 */
export function getUserProfile() {
  return request.get<any, Result<UserProfile>>('/user/profile')
}

/**
 * 更新用户资料
 * @param data 用户资料信息 (username, bio)
 */
export function updateUserProfile(data: Partial<UserProfile>) {
  return request.put<any, Result<void>>('/user/profile', data)
}
