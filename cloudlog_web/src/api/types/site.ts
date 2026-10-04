/** 站点级文案（后台可编辑，未配置时由后端回落内置默认值） */
export interface AboutContent {
  name: string
  role: string
  lead: string
  avatar: string
}

/** 公开站点信息：标题后缀与页脚落款用的站点名、备案号、评论字数上限 */
export interface SiteInfo {
  /** 后端永不为空：未配置时回落 CloudLog */
  siteName: string
  /** 空串表示后台没填，页脚据此不渲染备案那一行 */
  icpNumber: string
  /** 评论字数上限，0 表示不限制 */
  commentMaxLength: number
  /** 同一用户两次发评论的最小间隔秒数，0 表示不限制；服务端仍独立校验 */
  commentIntervalSeconds: number
  /** 开放注册开关。缺省按「开放」解读：拉不到设置时不该把注册入口藏起来 */
  registrationEnabled: boolean
}

/** 站点统计（口径为已发布文章），最近更新为 null 表示全站尚无已发布文章 */
export interface SiteStats {
  /** 后端 Long 以字符串下发，仅用于展示 */
  articleCount: string
  categoryCount: string
  latestPublishedAt: string | null
  categoryCounts: Record<number, number>
}
