import request from '@/utils/request'
import type { Result } from './types/auth'
import type { AboutContent, SiteInfo, SiteStats } from './types/site'

/**
 * 获取关于页文案（公开接口）
 */
export function getAboutContent() {
  return request.get<unknown, Result<AboutContent>>('/site/about')
}

/**
 * 获取站点信息（公开接口）：站点名 / ICP 备案号 / 评论字数上限，供布局、页脚与评论框读取
 */
export function getSiteInfo() {
  return request.get<unknown, Result<SiteInfo>>('/site/settings')
}

/**
 * 获取站点统计（公开接口）：文章总数 / 分类总数 / 最近更新 / 各分类文章数
 */
export function getSiteStats() {
  return request.get<unknown, Result<SiteStats>>('/site/stats')
}
