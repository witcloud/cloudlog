import request from '@/utils/request'
import type { Result } from './types/auth'
import type { Author, PageResult, PostVO } from './types/article'

export interface UserProfile {
  username: string
  avatar: string
  bio: string
  occupation: string
  email?: string
}

export interface EditUserProfile {
  username: string
  avatar: string
  bio: string
  occupation: string
  email?: string
}

export interface EmailChangeRequest {
  newEmail: string
  code: string
  password: string
}

export interface PasswordChangeRequest {
  /** 当前密码，服务端先比对它才接受换发 */
  oldPassword: string
  newPassword: string
}

/** 关注关系列表用的用户简要信息，不含邮箱与角色 */
export interface UserBrief {
  /** 用户雪花 ID */
  id: string
  username: string
  avatar: string
  bio: string
  occupation: string
  /** 我是否已关注此人。只有作者主页的「关注中」「粉丝」两栏标注，未登录与个人主页列表为 null */
  followed: boolean | null
  /** 此人是否已关注我，与 followed 同真即互相关注。同上为 null */
  followsMe: boolean | null
}

/** 个人主页统计行的两个计数 */
export interface ProfileStats {
  followingCount: number
  followersCount: number
}

/**
 * 他人作者主页：与文章详情的 Author 同一套字段名（后端共用一条聚合查询），
 * 多出的只有 bio、occupation 与 following。followed 未登录时为 null
 */
export interface AuthorProfile extends Author {
  bio: string | null
  occupation: string | null
  following: number
}

/** 列表接口统一分页参数（business-model.md §5.4：新接口一律 page / pageSize） */
export interface PageParams {
  page?: number
  pageSize?: number
}

/**
 * 获取用户资料
 */
export function getUserProfile() {
  return request.get<unknown, Result<UserProfile>>('/user/profile')
}

/**
 * 更新用户资料
 * @param data 用户资料信息 (username, bio, occupation)
 */
export function updateUserProfile(data: Partial<UserProfile>) {
  return request.put<unknown, Result<void>>('/user/profile', data)
}

/**
 * 作者主页公开视图（无需登录）
 * @param id 作者用户ID
 */
export function getAuthorProfile(id: string) {
  return request.get<unknown, Result<AuthorProfile>>(`/users/${id}/profile`)
}

/**
 * 该作者关注的用户列表（公开，无需登录）
 * @param id 作者用户ID
 * @param params 分页参数
 */
export function getAuthorFollowing(id: string, params: PageParams) {
  return request.get<unknown, Result<PageResult<UserBrief>>>(`/users/${id}/following`, {
    params,
  })
}

/**
 * 关注该作者的用户列表（公开，无需登录）
 * @param id 作者用户ID
 * @param params 分页参数
 */
export function getAuthorFollowers(id: string, params: PageParams) {
  return request.get<unknown, Result<PageResult<UserBrief>>>(`/users/${id}/followers`, {
    params,
  })
}

/**
 * 该作者点赞过的已发布文章（公开，无需登录）
 * @param id 作者用户ID
 * @param params 分页参数
 */
export function getAuthorLikedPosts(id: string, params: PageParams) {
  return request.get<unknown, Result<PageResult<PostVO>>>(`/users/${id}/liked-posts`, {
    params,
  })
}

/**
 * 发送修改邮箱验证码
 * @param newEmail 待绑定的新邮箱
 */
export function sendEmailChangeCode(newEmail: string) {
  return request.post<unknown, Result<void>>('/user/email/code', null, {
    params: { newEmail },
  })
}

/**
 * 修改登录邮箱
 * @param data 新邮箱、验证码与当前密码
 */
export function changeEmail(data: EmailChangeRequest) {
  return request.put<unknown, Result<void>>('/user/email', data)
}

/**
 * 修改登录密码：服务端验过当前密码后换发新口令，并作废该账号此前签发的全部 token
 * @param data 当前密码与新密码
 */
export function changePassword(data: PasswordChangeRequest) {
  return request.put<unknown, Result<void>>('/user/password', data)
}

/**
 * 当前用户的关注与粉丝计数（个人主页统计行）
 */
export function getProfileStats() {
  return request.get<unknown, Result<ProfileStats>>('/user/profile/stats')
}

/**
 * 我关注的用户列表
 * @param params 分页参数
 */
export function getFollowing(params: PageParams) {
  return request.get<unknown, Result<PageResult<UserBrief>>>('/user/following', {
    params,
  })
}

/**
 * 关注我的用户列表
 * @param params 分页参数
 */
export function getFollowers(params: PageParams) {
  return request.get<unknown, Result<PageResult<UserBrief>>>('/user/followers', {
    params,
  })
}

/**
 * 我点赞过的文章列表，按点赞时刻倒序
 * @param params 分页参数
 */
export function getLikedPosts(params: PageParams) {
  return request.get<unknown, Result<PageResult<PostVO>>>('/user/liked-posts', {
    params,
  })
}
