import dayjs from 'dayjs'

/** 北京时间的 UTC 偏移分钟数（+08:00）。后端 Jackson 的 time-zone 与 JDBC 的
 *  serverTimezone 都锚 GMT+8：publishTime、publishedAt 这类时刻一律是北京墙钟字符串 */
export const BEIJING_OFFSET_MINUTES = 8 * 60

/**
 * 「此刻」的北京墙钟：返回一个以本机时区显示、但各字段等于北京墙钟的 dayjs 实例。
 * 用于与「按北京墙钟输入/存储」的时刻（定时发布时间等）比较与展示——
 * 浏览器不在东八区时，直接 dayjs() 比较会差出时区差
 */
export const beijingNow = () =>
  dayjs(Date.now() + (BEIJING_OFFSET_MINUTES + new Date().getTimezoneOffset()) * 60_000)

/**
 * 解析后端下发的 "yyyy-MM-dd HH:mm:ss"（北京墙钟）为绝对时刻。
 * dayjs 裸解析会按浏览器本地时区解释，海外访客拿到的时间整体偏移；
 * 显式补 +08:00 让它落回正确瞬间
 */
export const parseBeijingTime = (value: string) => {
  if (!value) return null
  const parsed = dayjs(value.includes('T') ? value : `${value.replace(' ', 'T')}+08:00`)
  return parsed.isValid() ? parsed : null
}
