import request from '@/utils/request'
import type { Result } from './types/auth'
import type { PageResult } from './types/article'
import type {
  AdminArticleItem,
  AdminArticleQuery,
  AdminAuthor,
  AdminCategoryItem,
  AdminCommentItem,
  AdminCommentQuery,
  AdminTagItem,
  AdminUserItem,
  AdminUserQuery,
  BannedWordImportPayload,
  BannedWordImportResult,
  BannedWordItem,
  BannedWordPayload,
  BannedWordQuery,
  CategoryPayload,
  CommentStatusCounts,
  CommentStatus,
  DashboardData,
  LoginLogItem,
  LoginLogOverview,
  LoginLogQuery,
  SystemSettings,
  UserStatus,
  VisitLogItem,
  VisitLogQuery,
  VisitLogSummary,
} from './types/admin'
import type { AboutContent } from './types/site'

/* ---- 工作台 ---- */

/** 获取工作台概览数据 */
export function getDashboard() {
  return request.get<unknown, Result<DashboardData>>('/admin/dashboard')
}

/* ---- 文章管理 ---- */

/** 分页查询全站文章（含草稿/定时发布） */
export function getAdminArticles(params: AdminArticleQuery) {
  return request.get<unknown, Result<PageResult<AdminArticleItem>>>('/admin/articles', { params })
}

/** 获取发表过文章的作者列表（筛选项） */
export function getAdminAuthors() {
  return request.get<unknown, Result<AdminAuthor[]>>('/admin/articles/authors')
}

/** 删除文章（联动清理标签关联与评论） */
export function deleteAdminArticle(id: string) {
  return request.delete<unknown, Result<void>>(`/admin/articles/${id}`)
}

/* ---- 评论管理 ---- */

/** 分页查询全站评论 */
export function getAdminComments(params: AdminCommentQuery) {
  return request.get<unknown, Result<PageResult<AdminCommentItem>>>('/admin/comments', { params })
}

/** 获取各审核状态的评论数（页签角标） */
export function getCommentStatusCounts() {
  return request.get<unknown, Result<CommentStatusCounts>>('/admin/comments/status-counts')
}

/** 审核评论：approved(通过) / rejected(拒绝) / pending(退回待审) */
export function updateCommentStatus(id: number, status: CommentStatus) {
  return request.put<unknown, Result<void>>(`/admin/comments/${id}/status`, null, {
    params: { status },
  })
}

/** 删除评论（联动删除直接回复） */
export function deleteAdminComment(id: number) {
  return request.delete<unknown, Result<void>>(`/admin/comments/${id}`)
}

/* ---- 违禁词库（评论管理页的第二个页签，不在系统设置） ---- */

/** 分页查询违禁词库，keyword 按词面模糊匹配 */
export function getBannedWords(params: BannedWordQuery) {
  return request.get<unknown, Result<PageResult<BannedWordItem>>>('/admin/banned-words', { params })
}

/** 添加违禁词；词库已有该词时后端回 400 与中文原因，提示由拦截器统一处理 */
export function createBannedWord(data: BannedWordPayload) {
  return request.post<unknown, Result<void>>('/admin/banned-words', data)
}

/** 批量导入：text 传未拆分的原始文本，拆分与去重由服务端承担，回 added/skipped */
export function importBannedWords(data: BannedWordImportPayload) {
  return request.post<unknown, Result<BannedWordImportResult>>(
    '/admin/banned-words/batch-import',
    data,
  )
}

/** 启用/停用违禁词。停用不是删除：词条留着，机审跳过它 */
export function toggleBannedWord(id: number) {
  return request.put<unknown, Result<void>>(`/admin/banned-words/${id}/toggle`, null)
}

/** 删除违禁词（硬删；不回写存量评论已记录的命中词） */
export function deleteBannedWord(id: number) {
  return request.delete<unknown, Result<void>>(`/admin/banned-words/${id}`)
}

/* ---- 分类管理 ---- */

/** 分类列表（文章数为全量口径，含草稿与定时；公开 /categories 只计已发布） */
export function getAdminCategories() {
  return request.get<unknown, Result<AdminCategoryItem[]>>('/admin/categories')
}

/** 创建分类 */
export function createCategory(data: CategoryPayload) {
  return request.post<unknown, Result<void>>('/admin/categories', data)
}

/** 更新分类 */
export function updateCategory(id: number, data: CategoryPayload) {
  return request.put<unknown, Result<void>>(`/admin/categories/${id}`, data)
}

/** 删除分类 */
export function deleteCategory(id: number) {
  return request.delete<unknown, Result<void>>(`/admin/categories/${id}`)
}

/* ---- 标签治理（编辑/合并/删除） ---- */

/** 标签列表（含关联文章数） */
export function getAdminTags() {
  return request.get<unknown, Result<AdminTagItem[]>>('/admin/tags')
}

/** 修改标签名称 */
export function updateTag(id: number, name: string) {
  return request.put<unknown, Result<void>>(`/admin/tags/${id}`, { name })
}

/** 合并标签：sourceId 的文章关联转移到 targetId 后删除源标签 */
export function mergeTag(sourceId: number, targetId: number) {
  return request.post<unknown, Result<void>>('/admin/tags/merge', null, {
    params: { sourceId, targetId },
  })
}

/** 删除标签（联动清理文章关联） */
export function deleteAdminTag(id: number) {
  return request.delete<unknown, Result<void>>(`/admin/tags/${id}`)
}

/* ---- 用户管理 ---- */

/** 分页查询注册用户 */
export function getAdminUsers(params: AdminUserQuery) {
  return request.get<unknown, Result<PageResult<AdminUserItem>>>('/admin/users', { params })
}

/** 禁用/启用用户：1-正常，0-禁用 */
export function updateUserStatus(id: string, status: UserStatus) {
  return request.put<unknown, Result<void>>(`/admin/users/${id}/status`, null, {
    params: { status },
  })
}

/* ---- 系统设置 ---- */

/** 获取系统设置 */
export function getSystemSettings() {
  return request.get<unknown, Result<SystemSettings>>('/admin/settings')
}

/** 更新系统设置 */
export function updateSystemSettings(data: Partial<SystemSettings>) {
  return request.put<unknown, Result<void>>('/admin/settings', data)
}

/* ---- 关于页文案 ---- */

/** 获取关于页文案 */
export function getAboutContent() {
  return request.get<unknown, Result<AboutContent>>('/admin/about-content')
}

/** 更新关于页文案（传空串恢复该项默认文案） */
export function updateAboutContent(data: Partial<AboutContent>) {
  return request.put<unknown, Result<void>>('/admin/about-content', data)
}

/* ---- 访问日志（「日志管理」的访问日志页签） ---- */

/**
 * 概览四卡 + 趋势 + 三个 Top 榜。
 * 合成一次请求：四张卡、柱图与榜单必须出自同一次窗口扫描，分开发请求就会拿到两份对不上的数
 */
export function getVisitLogSummary(params: { startDate?: string; endDate?: string }) {
  return request.get<unknown, Result<VisitLogSummary>>('/admin/logs/visits/summary', { params })
}

/** 分页查询访问明细，时间 / 文章 / 地址前缀 / UA 关键词四个条件可组合 */
export function getVisitLogs(params: VisitLogQuery) {
  return request.get<unknown, Result<PageResult<VisitLogItem>>>('/admin/logs/visits', { params })
}

/* ---- 登录日志与锁定策略（「日志管理」的登录日志页签） ---- */

/**
 * 概览：锁定策略 + 三张计数卡 + 攻击 Top 榜。
 * 与访问日志同一口径，合成一次请求，卡片与榜单才可能对得上账
 */
export function getLoginLogOverview() {
  return request.get<unknown, Result<LoginLogOverview>>('/admin/login-logs/overview')
}

/** 分页查询登录明细，时间 / 邮箱 / 地址前缀 / 结果四个条件可任意组合；来源地址只回脱敏值 */
export function getLoginLogs(params: LoginLogQuery) {
  return request.get<unknown, Result<PageResult<LoginLogItem>>>('/admin/login-logs', { params })
}

/**
 * 保存锁定策略。越界值后端回 400 与中文原因，提示由拦截器统一处理；
 * 改动只影响之后新开的计数窗口，已锁定键的剩余时长不变
 */
export function updateLoginPolicy(data: { maxFailures: number; lockMinutes: number }) {
  return request.put<unknown, Result<void>>('/admin/login-policy', data)
}
