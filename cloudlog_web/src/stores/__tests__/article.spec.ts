import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getArticleDetail } = vi.hoisted(() => ({ getArticleDetail: vi.fn() }))
vi.mock('@/api/article', () => ({ getArticleDetail }))

import type { Article } from '@/api/types/article'
import { useArticleStore } from '@/stores/article'

const base = {
  id: '7',
  title: '测试文章',
  excerpt: '',
  content: '第一段',
  cover: null,
  publishedAt: '2026-03-05 20:15:30',
  author: { id: '1', username: 'ayun', avatar: null },
  category: { id: 2, name: '技术' },
  tags: [],
  views: 0,
  likes: 0,
  comments: 0,
  isTop: 0,
}

const anArticle = (over: Partial<Article>): Article => ({ ...base, ...over }) as Article

beforeEach(() => {
  setActivePinia(createPinia())
  getArticleDetail.mockReset()
  // 详情取数的 catch 会 console.error 整个错误对象，两条失败用例会刷出堆栈噪音
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('article store / 编辑器状态流转', () => {
  it('定时文章回显时从 publishedAt 恢复计划时刻', () => {
    const store = useArticleStore()
    store.initEditArticle(anArticle({ status: 'scheduled', publishedAt: '2026-03-08 09:00:00' }))

    expect(store.editArticle.status).toBe('scheduled')
    expect(store.editArticle.publishTime).toBe('2026-03-08 09:00:00')
    expect(store.dbStatus).toBe('scheduled')
    expect(store.publishedAt).toBe('2026-03-08 09:00:00')
  })

  it('已发布文章不带计划时刻，publishedAt 只作库中事实', () => {
    const store = useArticleStore()
    store.initEditArticle(anArticle({ status: 'published', publishedAt: '2026-03-05 20:15:30' }))

    expect(store.editArticle.publishTime).toBeNull()
    expect(store.publishedAt).toBe('2026-03-05 20:15:30')
  })

  it('标签归一成 id，回显不留脏数据不报未保存', () => {
    const store = useArticleStore()
    store.initEditArticle(anArticle({ tags: [{ id: 3, name: 'Vue' }], status: 'draft' }))

    expect(store.editArticle.tags).toEqual([3])
    expect(store.isDirty).toBe(false)
  })

  it('resetEditArticle 把编辑面与本地封面一起收回初值', () => {
    const store = useArticleStore()
    store.initEditArticle(
      anArticle({ id: '9', status: 'scheduled', publishedAt: '2026-03-08 09:00:00' }),
    )
    store.setLocalCover(new File(['x'], 'cover.png', { type: 'image/png' }))
    store.resetEditArticle()

    expect(store.editArticle.id).toBeNull()
    expect(store.editArticle.publishTime).toBeNull()
    expect(store.dbStatus).toBe('draft')
    expect(store.localCover).toBeNull()
  })
})

describe('article store / 详情取数', () => {
  it('code 200 才落进 currentArticle', async () => {
    const store = useArticleStore()
    getArticleDetail.mockResolvedValue({ code: 200, message: '操作成功', data: anArticle({}) })

    await expect(store.fetchArticleDetail('7')).resolves.toMatchObject({ id: '7' })
    expect(store.currentArticle?.id).toBe('7')
    expect(store.detailFailed).toBe(false)
    expect(store.loading).toBe(false)
  })

  it('404 是「不存在」，5xx 与网络故障是「服务端没答上话」', async () => {
    const store = useArticleStore()

    getArticleDetail.mockRejectedValue({ response: { status: 404 } })
    await expect(store.fetchArticleDetail('404')).resolves.toBeNull()
    expect(store.detailFailed).toBe(false)

    getArticleDetail.mockRejectedValue({ response: { status: 502 } })
    await expect(store.fetchArticleDetail('502')).resolves.toBeNull()
    expect(store.detailFailed).toBe(true)
  })
})
