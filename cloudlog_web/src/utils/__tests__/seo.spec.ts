import type { Window as HappyDomWindow } from 'happy-dom'
import { describe, expect, it } from 'vitest'

import { canonicalUrl, toIso8601 } from '@/utils/seo'

/** 改写 happy-dom 的地址栏，用来构造「同一个页面的不同参数形态」 */
const atUrl = (url: string) => {
  ;(window as unknown as HappyDomWindow).happyDOM.setURL(url)
}

describe('toIso8601', () => {
  it('套件跑在 UTC 上，+08:00 锚定才有可测的差量', () => {
    // 上面那条断言的全部效力来自这里：机器若回到 +08，裸解析与显式补偏移会给出同一瞬间
    expect(new Date().getTimezoneOffset()).toBe(0)
  })

  it('把后端时刻按 +08:00 解析，与运行时时区无关', () => {
    // vitest.config.ts 已把 TZ 钉成 UTC：本机 +08 时，裸解析与显式补偏移算出同一瞬间，错不出来
    expect(toIso8601('2026-03-05 20:15:30')).toBe('2026-03-05T12:15:30.000Z')
  })

  it('北京零点落到前一天的 UTC 16 点', () => {
    expect(toIso8601('2026-03-05 00:00:00')).toBe('2026-03-04T16:00:00.000Z')
  })

  it('已带偏移的 ISO 串原样换算，不再补一次 +08:00', () => {
    expect(toIso8601('2026-03-05T20:15:30+08:00')).toBe('2026-03-05T12:15:30.000Z')
  })
})

describe('canonicalUrl', () => {
  it('只留协议、主机与路径，剥掉查询参数', () => {
    atUrl('https://cloudlog.example.com/articles/7?from=rss&utm_source=weibo')
    expect(canonicalUrl()).toBe('https://cloudlog.example.com/articles/7')
  })

  it('分页形态与带尾斜杠的形态各归各的路径', () => {
    atUrl('https://cloudlog.example.com/categories/dev?page=2')
    expect(canonicalUrl()).toBe('https://cloudlog.example.com/categories/dev')
  })
})
