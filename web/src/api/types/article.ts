export type { Category } from './category'

export interface Author {
  id: number
  username: string
  avatar: string | null
}

export interface Tag {
  id: number
  name: string
}

export interface Article {
  id: number
  title: string
  excerpt: string
  content: string
  cover: string | null
  publishedAt: string
  author: Author
  category: Category
  tags: Tag[]
  views: number
  likes: number
  comments: number
  isTop: number
}

export interface HotArticle {
  id: number
  title: string
}

export interface ArticleListParams {
  page?: number
  pageSize?: number
  categoryId?: number
  tagId?: number
  keyword?: string
}

export interface ArticleListResponse {
  total: number
  page: number
  pageSize: number
  list: Article[]
}

export interface DraftListParams {
  pageNum: number
  pageSize: number
  year?: string | number | null
  month?: string | number | null
  keyword?: string | null
}

export interface PostVO {
  id: number
  title: string
  cover: string | null
  status: string
  category: Category
  updatedAt: string
}

export interface PageResult<T> {
  total: number
  list: T[]
}
