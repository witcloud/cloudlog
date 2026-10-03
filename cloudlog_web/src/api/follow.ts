import request from '@/utils/request'
import type { Result } from './types/auth'
import type { FollowState } from './types/article'

/**
 * 关注作者（需登录）
 * 幂等：重复关注返回同一「已关注」状态；响应即按钮的权威状态
 * @param id 被关注用户ID
 */
export function followAuthor(id: string) {
  return request.post<unknown, Result<FollowState>>(`/users/${id}/follow`)
}

/**
 * 取消关注（需登录）
 * 幂等：未关注过也返回「未关注」状态；响应即按钮的权威状态
 * @param id 被关注用户ID
 */
export function unfollowAuthor(id: string) {
  return request.delete<unknown, Result<FollowState>>(`/users/${id}/follow`)
}
