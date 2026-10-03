/** 管理后台相关的类型定义 */

export type ArticleStatus = 'draft' | 'published' | 'scheduled' | 'offline'
export type CommentStatus = 'pending' | 'approved' | 'rejected'
export type UserStatus = 0 | 1

/** 管理端文章作者选项（筛选用） */
export interface AdminAuthor {
  /** 用户雪花 ID */
  id: string
  username: string
  avatar: string | null
}

/** 管理端文章列表行（不含正文） */
export interface AdminArticleItem {
  /** 雪花 ID：字符串透传，不参与数值运算 */
  id: string
  title: string
  status: ArticleStatus
  isTop: number
  /** 置顶排序权重，越大越靠前；仅 isTop=1 时参与排序 */
  topOrder: number
  /** 最近一次执行下线的时刻；重新上线不清空，从未下线过为 null */
  offlinedAt: string | null
  /** 执行该次下线的人（不是作者，作者可能另有其人） */
  offlinedByName: string | null
  views: number
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  author: AdminAuthor | null
  category: { id: number; name: string } | null
}

export interface AdminArticleQuery {
  page: number
  pageSize: number
  keyword?: string | null
  status?: string | null
  authorId?: string | null
  categoryId?: number | null
  startDate?: string | null
  endDate?: string | null
}

/** 管理端评论者信息（匿名评论为 null） */
export interface AdminCommentUser {
  /** 用户雪花 ID */
  id: string
  username: string
  avatar: string | null
}

/** 管理端评论列表行 */
export interface AdminCommentItem {
  id: number
  /** 文章雪花 ID：字符串透传 */
  postId: string
  postTitle: string | null
  content: string
  /** 父评论 ID：0 = 顶级评论（后端 comments.parent_id 用哨兵值而非 NULL 表达「无父」） */
  parentId: number
  parentUsername: string | null
  parentContent: string | null
  status: CommentStatus
  /** 机审命中的首个违禁词；关审核、未命中与该列上线前的存量评论均为 null */
  hitWord: string | null
  createdAt: string
  user: AdminCommentUser | null
}

export interface AdminCommentQuery {
  page: number
  pageSize: number
  status?: string | null
  keyword?: string | null
}

export interface CommentStatusCounts {
  pending: number
  approved: number
  rejected: number
}

/** 管理端用户列表行 */
export interface AdminUserItem {
  /** 用户雪花 ID */
  id: string
  email: string
  username: string
  avatar: string | null
  bio: string | null
  occupation: string | null
  role: string
  status: UserStatus
  articleCount: number
  /** 上次登录时刻；从未登录过为 null，界面据此显示「从未」 */
  lastLoginAt: string | null
  /** 上次登录来源 IP；只在后台接口出现，从未登录过为 null */
  lastLoginIp: string | null
  createdAt: string
}

export interface AdminUserQuery {
  page: number
  pageSize: number
  keyword?: string | null
  status?: number | null
}

/** 管理端标签列表行（含关联文章数） */
export interface AdminTagItem {
  id: number
  name: string
  articleCount: number
  createdAt: string
}

/** 管理端分类列表行（/admin/categories 返回，articleCount 为含草稿/定时/已下线的全量口径） */
export interface AdminCategoryItem {
  id: number
  name: string
  description: string | null
  articleCount: number
  createdAt: string
}

export interface CategoryPayload {
  name: string
  description?: string | null
}

/** 工作台概览数据（后端 Long 统一以字符串下发，展示可直接用，比较/运算前 Number()） */
export interface DashboardData {
  userCount: string
  articleCount: string
  pendingCommentCount: string
  totalViews: string
  recentArticles: AdminArticleItem[]
  recentComments: AdminCommentItem[]
}

/** 系统设置：站点信息、运行开关与评论防护。违禁词库属内容数据，见 BannedWordItem */
export interface SystemSettings {
  commentModeration: boolean
  registrationEnabled: boolean
  /** 站点名称：留空保存后读取端回落 CloudLog */
  siteName: string
  /** ICP 备案号：留空则前台页脚不展示备案那一行 */
  icpNumber: string
  /** 站点对外域名（含协议、末尾不带斜杠）：sitemap / RSS 的绝对链接与 robots 的 Sitemap 行由它拼。
   *  留空即按当次请求的来源地址兜底 */
  siteUrl: string
  /** 评论字数上限，0 表示不限制，取值 0-5000 */
  commentMaxLength: number
  /** 同一用户评论发布间隔（秒），0 表示不限制，取值 0-86400 */
  commentIntervalSeconds: number
}

/** 违禁词命中后的处置动作：pending 转人工待审 / reject 直接拒绝且不入库 */
export type BannedWordAction = 'pending' | 'reject'

/** 违禁词库列表行 */
export interface BannedWordItem {
  id: number
  word: string
  action: BannedWordAction
  /** 1 参与匹配 / 0 停用；后端是 TINYINT，按数字下发（同 AdminArticleItem.isTop） */
  isEnabled: number
  createdAt: string
  updatedAt: string
}

export interface BannedWordQuery {
  page: number
  pageSize: number
  keyword?: string | null
}

/** 单条添加入参；action 留空后端按默认 pending */
export interface BannedWordPayload {
  word: string
  action?: BannedWordAction | null
}

/** 批量导入入参：text 是未拆分的原始文本，拆分规则由服务端承担 */
export interface BannedWordImportPayload {
  text: string
  action?: BannedWordAction | null
}

/** 批量导入结果：skipped 合并「文本内重复 / 词库已有 / 超过 50 字符」，不区分原因 */
export interface BannedWordImportResult {
  added: number
  skipped: number
}

/* ---- 访问日志（「日志管理」的访问日志页签） ---- */

/** 时间范围四档：自定义之外都是整天数固定 */
export type VisitLogRange = 'today' | 'last7' | 'last30' | 'custom'

/** 概览四卡的数值。四个数都出自同一次窗口扫描，可以直接与趋势图各柱交叉核对 */
export interface VisitLogOverview {
  /** 访问次数（窗口内日志行数）；后端 Long 以字符串下发 */
  pv: string
  /** 访问者数（窗口内 IP 去重数） */
  uv: string
  /** 篇均访问 = PV ÷ 窗口内被访问过的文章数，后端已保留一位小数 */
  avgVisitPerPost: number
  /** PV 较前一等长窗口的涨跌百分比，正升负降；前一窗口为 0 次时为 null（百分比求不出来） */
  pvChangeRate: number | null
}

/** 趋势的一根柱子。标签与提示文案都由后端算好，前端不再拼日期 */
export interface VisitLogTrendPoint {
  /** 柱下短标签：日视图为 MM-DD，小时视图为两位小时数 */
  label: string
  /** 悬浮提示用的完整时刻 */
  tooltip: string
  visitCount: string
}

/** Top 文章行 */
export interface VisitLogTopPost {
  /** 文章雪花 ID：字符串透传 */
  postId: string
  /** 文章已删时为 null，界面显示「已删除文章」且不给跳转 */
  title: string | null
  visitCount: string
  /** 占窗口内 PV 的百分比，后端保留一位小数 */
  percent: number
}

/** Top IP 行：只有脱敏后的地址，接口响应里不存在完整 IP */
export interface VisitLogTopIp {
  ipMasked: string
  visitCount: string
  percent: number
}

/** Top 分类行。无分类、分类已删、文章已删三种情况都归在 categoryName 为 null 的那一格 */
export interface VisitLogTopCategory {
  categoryName: string | null
  visitCount: string
  percent: number
}

/** 概览 + 趋势 + 三个 Top 榜：一次窗口一次返回 */
export interface VisitLogSummary {
  overview: VisitLogOverview
  /** 单日窗口 24 根小时柱，多日窗口每天一根，无访问的时段补 0 不缺柱 */
  trend: VisitLogTrendPoint[]
  topPosts: VisitLogTopPost[]
  topIps: VisitLogTopIp[]
  topCategories: VisitLogTopCategory[]
}

/** 访问明细的一行 */
export interface VisitLogItem {
  id: number
  createdAt: string
  /** 文章雪花 ID：字符串透传 */
  postId: string
  /** 文章已删时为 null */
  postTitle: string | null
  ipMasked: string
  /** UA 归类标签，七个取值由后端判定 */
  userAgentType: string
  /** 原始 UA，只在悬浮提示里整串展示 */
  userAgent: string | null
}

/** 明细查询参数。时间范围与概览共用同一对日期，不单独设一套 */
export interface VisitLogQuery {
  page: number
  pageSize: number
  startDate?: string | null
  endDate?: string | null
  postId?: string | null
  /** 脱敏口径的前缀，如 112.10 */
  ip?: string | null
  userAgent?: string | null
}

/* ---- 登录日志与锁定策略（日志管理页的第二个页签） ---- */

/** 登录结果，与后端 login_logs.result 的四个取值一一对应 */
export type LoginLogResult = 'success' | 'wrong_password' | 'locked' | 'disabled'

/** 锁定策略：当前生效值 + 合法区间。区间由后端随响应下发，说明文字不再抄第二份数字 */
export interface LoginPolicy {
  maxFailures: number
  lockMinutes: number
  maxFailuresMin: number
  maxFailuresMax: number
  lockMinutesMin: number
  lockMinutesMax: number
}

export interface LoginTopAttack {
  email: string
  ipMasked: string
  /** 该「邮箱 + 来源地址」组合在保留期内的失败次数 */
  failures: number
}

export interface LoginLogOverview {
  policy: LoginPolicy
  todayFailures: number
  todayLocked: number
  /** 近一小时失败来源数，按「邮箱 + 来源地址」组合去重 */
  recentFailureSources: number
  topAttacks: LoginTopAttack[]
}

export interface LoginLogItem {
  id: number
  createdAt: string
  /** 尝试时输入的邮箱，原样记录：账号不存在也照样有一行 */
  email: string
  /** 登录成功时的用户 ID（雪花 ID，字符串下发）；三种失败都是 null */
  userId: string | null
  ipMasked: string
  /** UA 归类标签，七个取值由后端判定，与访问日志同一套 */
  userAgentType: string
  /** 原始 UA，只在悬浮提示里整串展示 */
  userAgent: string | null
  result: LoginLogResult
}

/** 明细查询参数。四个筛选可任意组合，空白项按未筛选处理 */
export interface LoginLogQuery {
  page: number
  pageSize: number
  startDate?: string | null
  endDate?: string | null
  email?: string | null
  /** 脱敏口径的前缀，如 203.0 或直接粘 203.0.*.* */
  ipPrefix?: string | null
  result?: LoginLogResult | null
}
