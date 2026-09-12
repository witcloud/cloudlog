import request from '@/utils/request'
import type { Result } from './types/auth'
import type { Category } from './types/category'

/**
 * 获取所有分类列表
 */
export function getCategories() {
  return request.get<any, Result<Category[]>>('/categories')
}

/**
 * 获取分类详情
 * @param id 分类ID
 */
export function getCategoryDetail(id: number) {
  return request.get<any, Result<Category>>(`/categories/${id}`)
}
