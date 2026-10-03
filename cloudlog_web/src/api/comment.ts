import request from '@/utils/request'
import type { Result } from './types/auth'
import type { AddCommentPayload, Comment } from './types/comment'

/**
 * 获取文章评论列表
 * 服务端只返回已通过审核的评论，回复挂在各自根评论下
 * @param postId 文章ID
 */
export function getComments(postId: string) {
  return request.get<unknown, Result<Comment[]>>(`/articles/${postId}/comments`)
}

/**
 * 发表评论（需登录）
 * 初始审核状态由服务端按「评论审核开关 + 违禁词机审」决定，data 返回最终 status：
 * 'approved' = 已发布，'pending' = 待审核（business-model.md §4.4 第 4 条）
 * @param data 文章ID、内容、父评论ID（根评论传 0）
 */
export function addComment(data: AddCommentPayload) {
  return request.post<unknown, Result<string>>('/comments', data)
}
