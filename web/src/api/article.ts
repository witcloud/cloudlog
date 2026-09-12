import request from '@/utils/request'
import type { Result } from './types/auth'
import type {
  Article,
  ArticleListParams,
  ArticleListResponse,
  Category,
  Tag,
  DraftListParams,
  PostVO,
  PageResult,
  HotArticle,
} from './types/article'

/**
 * 获取热门文章
 * @param params { limit: number }
 */
export function getHotArticles(params: { limit: number }) {
  return request.get<any, Result<HotArticle[]>>('/articles/hot', {
    params,
  })
}

/**
 * 获取草稿列表
 * @param params 查询参数
 */
export function getDrafts(params: DraftListParams) {
  return request.get<any, Result<PageResult<PostVO>>>('/articles/drafts', {
    params,
  })
}

/**
 * 删除草稿
 * @param id 草稿ID
 */
export function deleteDraft(id: number) {
  return request.delete<any, Result<any>>(`/drafts/${id}`)
}

/**
 * 清空草稿箱
 */
export function clearAllDrafts() {
  return request.delete<any, Result<any>>('/drafts')
}

/**
 * 获取文章列表
 * @param params 查询参数
 */
export function getArticles(params?: ArticleListParams) {
  return request.get<any, Result<ArticleListResponse>>('/articles', {
    params,
  })
}

/**
 * 获取文章详情
 * @param id 文章ID
 */
export function getArticleDetail(id: number) {
  return request.get<any, Result<Article>>(`/articles/${id}`)
}

/**
 * 创建文章
 * @param data 文章数据
 */
export function createArticle(data: any) {
  return request.post<any, Result<Article>>('/articles', data)
}

/**
 * 更新文章
 * @param id 文章ID
 * @param data 文章数据
 */
export function updateArticle(id: number, data: any) {
  return request.put<any, Result<Article>>(`/articles/${id}`, data)
}

/**
 * 上传文章内容图片
 * @param file 图片文件
 */
export function uploadArticleImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, Result<string>>('/upload/article-image', formData, {
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
  return request.post<any, Result<string>>('/upload/cover-image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 获取所有标签
 */
export function getTags() {
  return request.get<any, Result<Tag[]>>('/tags')
}
