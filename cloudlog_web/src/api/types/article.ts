import type { Category } from './category'

export type { Category }

export interface Author {
  /** 用户雪花 ID：字符串透传 */
  id: string
  username: string
  avatar: string | null
  // 作者职业：详情接口填充，列表接口不携带；空值不落任何回落文案，由读取方决定是否成行
  occupation?: string | null
  // 关注态与关注数：详情接口填充，列表接口不携带，读取方按未关注回退
  followers?: number
  followed?: boolean
  // 作者全部已发布文章的聚合：详情接口填充，列表接口不携带
  articleCount?: number
  likeCount?: number
  viewCount?: number
}

export interface Tag {
  id: number
  name: string
}

export interface Article {
  /** 雪花 ID：19 位超出 JS 安全整数，全程按字符串透传，不得参与数值运算 */
  id: string
  title: string
  excerpt: string
  // 正文只由详情接口取出；列表、搜索、热榜、草稿箱、「我的文章」都走瘦身查询，响应里连 content 这个键都没有
  content?: string | null
  cover: string | null
  publishedAt: string
  author: Author
  category: Category
  tags: Tag[]
  views: number
  likes: number
  // 当前浏览者是否已点赞：详情接口返回，列表接口不携带，读取方按未点赞回退
  liked?: boolean
  comments: number
  isTop: number
  /** 置顶排序权重，越大越靠前；仅 isTop=1 时参与排序 */
  topOrder?: number
  // 编辑器回显字段，详情接口不保证返回，读取方均有回退逻辑
  categoryId?: number
  /** draft / published / scheduled / offline（已下线仅作者与管理员可见） */
  status?: string
  publishTime?: string | null
  /** 以下三个只在详情接口出现，由服务端合成（business-model.md §2.10）：
   *  seoTitle 已是完整 document.title（「文章标题 - 站点名」），
   *  seoDescription 即文章摘要、seoKeywords 是该文章标签名拼接。前端不再判空、不再自己拼站点名 */
  seoTitle?: string | null
  seoDescription?: string | null
  seoKeywords?: string | null
}

export interface HotArticle {
  id: string
  title: string
}

/** 点赞/取消点赞后服务端返回的权威状态 */
export interface LikeState {
  liked: boolean
  likeCount: number
}

/** 关注/取消关注后服务端返回的权威状态 */
export interface FollowState {
  followed: boolean
  followers: number
}

/** 列表页排序 tab 的取值：recommended＝「推荐」（置顶优先，其余按加权热度分），latest＝「最新」（置顶优先，按发布时间）。
 *  popular 不在此列——那是侧栏热榜 /articles/hot 自己走的纯热度序，与首页 tab 分属两个 SQL 分支 */
export type ArticleSort = 'latest' | 'recommended'

export interface ArticleListParams {
  page?: number
  pageSize?: number
  categoryId?: number
  tagId?: number
  keyword?: string
  /** 作者用户ID（雪花 ID，字符串透传）：作者主页只列该作者的已发布文章 */
  authorId?: string
  /** 排序 tab。后端缺省即 latest，故不传此参数的调用方（搜索页、作者主页）行为不变 */
  sort?: ArticleSort
}

export interface ArticleListResponse {
  /** 后端 Long 统一以字符串下发，消费侧按需 Number() */
  total: string
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
  /** 只认未发布的两种状态，留空即两种都要 */
  status?: 'draft' | 'scheduled' | null
}

/** 「我的文章」（已发布 + 已下线）列表参数：新接口统一 page / pageSize（business-model.md §5.4） */
export interface MineListParams {
  page?: number
  pageSize?: number
  year?: string | number | null
  month?: string | number | null
  keyword?: string | null
}

export interface PostVO {
  /** 雪花 ID，字符串透传 */
  id: string
  title: string
  cover: string | null
  status: string
  category: Category
  views: number
  likes: number
  comments: number
  updatedAt: string
  publishedAt?: string
}

export interface PageResult<T> {
  /** 后端 Long 统一以字符串下发，消费侧按需 Number() */
  total: string
  list: T[]
}

/** 草稿箱分页：多两个状态计数供页头的状态视图；后端是 Integer，按数字下发 */
export interface DraftPage extends PageResult<PostVO> {
  draftCount: number
  scheduledCount: number
}
