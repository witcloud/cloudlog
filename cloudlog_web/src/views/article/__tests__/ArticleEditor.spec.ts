import { nextTick } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 替身记的是「编辑区当前显示的字」（shown），不是 store 里的值：
 * 详情页点编辑 → 退出 → 点写文章，store 由 loadArticleData 清空，
 * 而 vditor 实例是 onMounted 里先于那次清空构造的，两者会分叉。
 */
const { instances, api, route } = vi.hoisted(() => ({
  instances: [] as Array<{ shown: string }>,
  api: {
    getArticleDetail: vi.fn(),
    createArticle: vi.fn(),
    updateArticle: vi.fn(),
    uploadArticleImage: vi.fn(),
    uploadCoverImage: vi.fn(),
  },
  route: { name: 'ArticleEditor', params: {} as Record<string, string>, query: {}, meta: {} },
}))

vi.mock('vditor', () => ({
  default: class VditorStub {
    shown: string
    constructor(_el: unknown, options: Record<string, unknown>) {
      this.shown = (options.value as string | undefined) ?? ''
      instances.push(this)
      // 真库要等 lute 脚本落地才回调 after；同步回调会把这中间的竞态窗口抹掉
      setTimeout(() => (options.after as (() => void) | undefined)?.(), 0)
    }
    getValue = () => this.shown
    setValue = (value: string) => {
      this.shown = value
    }
    insertValue = (value: string) => {
      this.shown += value
    }
    getSelection = () => ''
    destroy = () => {}
    setTheme = () => {}
  },
}))

vi.mock('@/api/article', () => ({
  getArticleDetail: api.getArticleDetail,
  createArticle: api.createArticle,
  updateArticle: api.updateArticle,
  uploadArticleImage: api.uploadArticleImage,
  uploadCoverImage: api.uploadCoverImage,
}))

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  onBeforeRouteLeave: vi.fn(),
}))

// @/utils/request 在模块顶层 import 真路由表（它会连带拉起 MainLayout 与全站守卫），
// 组件测试只要「有个 router 对象」，不需要那套导航
vi.mock('@/router', () => ({
  default: { beforeEach: vi.fn(), afterEach: vi.fn(), push: vi.fn(), replace: vi.fn() },
  scrollBehavior: {},
}))

vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  }),
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
}))

vi.mock('md-editor-v3', () => ({
  MdPreview: {
    name: 'MdPreview',
    props: { modelValue: { type: String, default: '' } },
    template: '<div class="md-preview-stub"></div>',
  },
}))

import type { Article } from '@/api/types/article'
import { useArticleStore } from '@/stores/article'
import ArticleEditor from '../ArticleEditor.vue'

const anArticle = (over: Partial<Article>): Article =>
  ({
    id: '9',
    title: '旧作',
    excerpt: '',
    content: '旧作的正文',
    cover: null,
    status: 'published',
    publishedAt: '2026-03-05 20:15:30',
    author: { id: '1', username: 'ayun', avatar: null },
    category: { id: 2, name: '技术' },
    tags: [],
    views: 0,
    likes: 0,
    comments: 0,
    isTop: 0,
    ...over,
  }) as Article

const detail = (article: Article) => ({ code: 200, message: '操作成功', data: article })

/** 挂载并跑完「构造 → 详情落地 → 库回调 after」这一串异步 */
const open = async (wrapper: VueWrapper) => {
  await flushPromises()
  await nextTick()
  await new Promise((resolve) => setTimeout(resolve, 0))
  await flushPromises()
  await wrapper.vm.$nextTick()
}

let wrapper: VueWrapper

beforeEach(() => {
  setActivePinia(createPinia())
  instances.length = 0
  route.params = {}
  Object.values(api).forEach((fn) => fn.mockReset())
  api.getArticleDetail.mockResolvedValue(detail(anArticle({})))
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

const mountEditor = () =>
  mount(ArticleEditor, {
    global: {
      stubs: {
        EditorSettingsPanel: true,
        ImageUploadDialog: true,
        LinkInsertDialog: true,
        TocSidebar: true,
      },
      components: { 'el-drawer': { template: '<div><slot /></div>' } },
    },
  })

describe('ArticleEditor 的进页取数', () => {
  it('编辑过一篇再退出，点「写文章」时编辑区不带上一篇的字', async () => {
    // 上一次编辑会话的残留：store 里还挂着那篇文章
    useArticleStore().initEditArticle(anArticle({ id: '9', title: '旧作', content: '旧作的正文' }))

    wrapper = mountEditor() // /editor，无 id —— 新建
    await open(wrapper)

    expect(useArticleStore().editArticle.content).toBe('')
    expect(instances[0]?.shown).toBe('')
  })

  it('直接进某篇文章的编辑，正文照样回显', async () => {
    api.getArticleDetail.mockResolvedValue(detail(anArticle({ content: '库里的正文' })))
    route.params = { id: '9' }

    wrapper = mountEditor()
    await open(wrapper)

    expect(instances[0]?.shown).toBe('库里的正文')
  })
})
