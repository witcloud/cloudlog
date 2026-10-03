import request from '@/utils/request'
import type { Result } from './types/auth'
import type {
  Article,
  ArticleListParams,
  ArticleListResponse,
  Tag,
  DraftListParams,
  DraftPage,
  MineListParams,
  PostVO,
  PageResult,
  HotArticle,
  LikeState,
} from './types/article'

/**
 * 获取热门文章
 * @param params { limit: number }
 */
export function getHotArticles(params: { limit: number }) {
  return request.get<unknown, Result<HotArticle[]>>('/articles/hot', {
    params,
  })
}

/**
 * 获取未发布文章列表（草稿与定时发布），响应带分状态计数
 * @param params 查询参数
 */
export function getDrafts(params: DraftListParams) {
  return request.get<unknown, Result<DraftPage>>('/articles/drafts', {
    params,
  })
}

/**
 * 获取当前用户「我的文章」（已发布 + 已下线，个人主页用）
 * @param params 分页查询参数（page / pageSize）
 */
export function getMyPosts(params: MineListParams) {
  return request.get<unknown, Result<PageResult<PostVO>>>('/articles/mine', {
    params,
  })
}

/**
 * 删除草稿
 * @param id 草稿ID
 */
export function deleteDraft(id: string) {
  return request.delete<unknown, Result<void>>(`/drafts/${id}`)
}

/**
 * 清空草稿箱
 */
export function clearAllDrafts() {
  return request.delete<unknown, Result<void>>('/drafts')
}

/**
 * 获取文章列表
 * @param params 查询参数
 */
export function getArticles(params?: ArticleListParams) {
  return request.get<unknown, Result<ArticleListResponse>>('/articles', {
    params,
  })
}

/**
 * 获取文章详情
 * @param id 文章ID
 */
export function getArticleDetail(id: string) {
  return request.get<unknown, Result<Article>>(`/articles/${id}`)
}

/**
 * 点赞文章（需登录）
 * 幂等：重复点赞返回同一「已点赞」状态；响应即按钮的权威状态
 * @param id 文章ID
 */
export function likeArticle(id: string) {
  return request.post<unknown, Result<LikeState>>(`/articles/${id}/like`)
}

/**
 * 取消点赞（需登录）
 * 幂等：未点赞过也返回「未点赞」状态；响应即按钮的权威状态
 * @param id 文章ID
 */
export function unlikeArticle(id: string) {
  return request.delete<unknown, Result<LikeState>>(`/articles/${id}/like`)
}

/**
 * 创建文章
 * @param data 文章数据
 */
export function createArticle(data: Record<string, unknown>) {
  return request.post<unknown, Result<{ id: string }>>('/articles', data)
}

/**
 * 更新文章
 * @param id 文章ID
 * @param data 文章数据
 */
export function updateArticle(id: string, data: Record<string, unknown>) {
  return request.put<unknown, Result<void>>(`/articles/${id}`, data)
}

/**
 * 上传文章内容图片
 * @param file 图片文件
 */
export function uploadArticleImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<unknown, Result<string>>('/upload/article-image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 上传文章封面图片
 * @param file 图片文件
 */
export function uploadCoverImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<unknown, Result<string>>('/upload/cover-image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 获取所有标签
 */
export function getTags() {
  return request.get<unknown, Result<Tag[]>>('/tags')
}
