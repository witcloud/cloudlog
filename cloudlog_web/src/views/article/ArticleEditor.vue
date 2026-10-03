<template>
  <div class="editor-page">
    <header class="editor-topbar">
      <div class="editor-topbar__left">
        <button class="icon-btn" aria-label="返回" @click="goBack">
          <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
            <path
              d="M13 8H3m0 0 4-4M3 8l4 4"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
        <span class="editor-topbar__rule" aria-hidden="true"></span>
        <span class="topbar-status">
          <span v-if="statusLabel" class="article-status" :title="statusHint">
            {{ statusLabel }}
          </span>
          <span v-if="statusLabel" class="article-status__sep" aria-hidden="true">·</span>
          <span class="save-status" :class="{ 'is-dirty': saveState === 'dirty' }">
            {{ saveStateText }}
          </span>
        </span>
      </div>

      <div class="editor-topbar__right">
        <button
          class="icon-btn"
          aria-label="预览"
          :disabled="!article.title && !article.content"
          @click="previewing = true"
        >
          <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
            <path
              d="M1.7 8S4 3.8 8 3.8 14.3 8 14.3 8 12 12.2 8 12.2 1.7 8 1.7 8Z"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linejoin="round"
            />
            <circle cx="8" cy="8" r="1.8" stroke="currentColor" stroke-width="1.5" />
          </svg>
        </button>
        <button class="icon-btn settings-toggle" aria-label="文章设置" @click="settingsOpen = true">
          <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
            <path
              d="M2 4.5h7.3M12.7 4.5H14M2 11.5h1.3M6.7 11.5H14"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
            />
            <circle cx="10.5" cy="4.5" r="1.7" stroke="currentColor" stroke-width="1.4" />
            <circle cx="4.5" cy="11.5" r="1.7" stroke="currentColor" stroke-width="1.4" />
          </svg>
        </button>
        <button
          class="publish-btn"
          :disabled="!canPublish || submitting || uploadingCount > 0"
          @click="handlePublish"
        >
          {{ publishLabel }}
        </button>
      </div>
    </header>

    <div class="editor-body">
      <div class="editor-canvas">
        <div class="editor-sheet">
          <input
            v-model="article.title"
            class="editor-title"
            type="text"
            placeholder="文章标题…"
            maxlength="100"
            autocomplete="off"
          />
          <div ref="vditorEl" class="editor-vditor"></div>
        </div>
      </div>

      <!-- 桌面右侧设置栏 -->
      <aside v-if="showPanel" class="editor-panel">
        <div class="editor-panel__head">
          <span>文章设置</span>
        </div>
        <EditorSettingsPanel />
      </aside>
    </div>

    <!-- 移动端底部设置抽屉 -->
    <el-drawer
      v-model="settingsOpen"
      v-if="!showPanel"
      direction="btt"
      size="85%"
      :with-header="false"
      class="settings-drawer"
    >
      <div class="settings-drawer__head">
        <span>文章设置</span>
        <button class="icon-btn" aria-label="关闭设置" @click="settingsOpen = false">
          <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
            <path
              d="m4 4 8 8M12 4l-8 8"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
            />
          </svg>
        </button>
      </div>
      <EditorSettingsPanel />
    </el-drawer>

    <!-- 阅读态预览 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="previewing" class="preview-overlay">
          <header class="preview-overlay__bar">
            <span class="preview-overlay__label">预览</span>
            <button class="icon-btn" aria-label="关闭预览" @click="previewing = false">
              <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
                <path
                  d="m4 4 8 8M12 4l-8 8"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                />
              </svg>
            </button>
          </header>
          <div class="preview-overlay__canvas">
            <div class="preview-overlay__sheet">
              <h1 class="preview-overlay__title">{{ article.title || '无标题' }}</h1>
              <md-preview
                :model-value="article.content"
                :theme="isDark ? 'dark' : 'light'"
                code-theme="one-dark"
              />
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 图片上传弹窗：Tab 上传/链接 -->
    <ImageUploadDialog v-model="imageDialogOpen" @confirm="insertImageMarkdown" />

    <!-- 插入链接弹窗 -->
    <LinkInsertDialog v-model="linkDialogOpen" @confirm="insertLinkMarkdown" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import Vditor from 'vditor'
import 'vditor/dist/index.css'
import { MdPreview } from 'md-editor-v3'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { useArticleStore } from '@/stores/article'
import { useTheme } from '@/composables/useTheme'
import {
  getArticleDetail,
  createArticle,
  updateArticle,
  uploadArticleImage,
  uploadCoverImage,
} from '@/api/article'
import EditorSettingsPanel from './components/EditorSettingsPanel.vue'
import ImageUploadDialog from './components/ImageUploadDialog.vue'
import LinkInsertDialog from './components/LinkInsertDialog.vue'
import { formatMinioUrl } from '@/config'

const route = useRoute()
const router = useRouter()
const articleStore = useArticleStore()
const { isDark } = useTheme()

// 使用 Pinia store 中的状态
const article = articleStore.editArticle
const submitting = ref(false)
const previewing = ref(false)
const settingsOpen = ref(false)
const uploadingCount = ref(0)
const imageDialogOpen = ref(false)
const linkDialogOpen = ref(false)
// 打开弹窗时捕获的选区文本，确认后作为链接文字
let pendingLinkText = ''
let autoSaveTimer: ReturnType<typeof setInterval> | null = null

/** 自动保存节拍。必须是 interval：原先每次输入都 clearTimeout + 重开 30s，
    连续写作时永远到不了点，等于只在停手之后才存（ui-design.md §5.1 写的是「周期性」）。 */
const AUTO_SAVE_MS = 30000

// 是否为编辑模式：通过文章 ID 判定
const isEdit = computed(() => !!article.id)
const loaded = ref(false)

/** 最近一次落库的快照。脏与否拿它比对，不拿「有没有触发过 input」——
    回显链路（initEditArticle → setValue → input 回调）会在打开文章的瞬间空转一圈，
    事件判据因此把刚打开、一个字没改的文章直接标成「未保存」。 */
const baseline = ref({ title: '', content: '' })

const markBaseline = () => {
  baseline.value = { title: article.title, content: article.content ?? '' }
}

const syncDirty = () => {
  articleStore.isDirty =
    article.title !== baseline.value.title || (article.content ?? '') !== baseline.value.content
}

const canPublish = computed(() => !!article.title && !!article.content)

/** 库中已发布：主按钮只做内容更新，不承担状态转换（设置面板此时不给发布方式选项） */
const isPublishedEdit = computed(() => isEdit.value && articleStore.dbStatus === 'published')

/** 库中已下线：主按钮承担重新上线，走与首次发布同一条通道（后端不覆盖首发时刻） */
const isOfflineEdit = computed(() => isEdit.value && articleStore.dbStatus === 'offline')

/** 主按钮：已上线只更新内容，已下线是重新上线，其余按面板选的意图决定这次提交成什么状态 */
const publishLabel = computed(() => {
  if (submitting.value) return '保存中…'
  if (isPublishedEdit.value) return '更新'
  if (isOfflineEdit.value) return '上线'
  return article.status === 'scheduled' ? '定时发布' : '发布'
})

/** 顶栏状态徽章：未落库的新文章不显示，其余只认库中状态，不被面板的意图改写 */
const statusLabel = computed(() => {
  if (!article.id) return ''
  if (articleStore.dbStatus === 'published') return '已发布'
  if (articleStore.dbStatus === 'scheduled') return '定时发布中'
  if (articleStore.dbStatus === 'offline') return '已下线'
  return '草稿'
})

/** 徽章提示：已发布给上线时刻，定时给计划时刻（库里两者都存于 published_at）；已下线给首发时刻 */
const statusHint = computed(() => {
  const time = articleStore.publishedAt
  if (!time) return undefined
  const label = dayjs(time).format('YYYY年M月D日 HH:mm')
  if (articleStore.dbStatus === 'scheduled') return `计划时刻 ${label}`
  if (articleStore.dbStatus === 'published') return `发布于 ${label}`
  if (articleStore.dbStatus === 'offline') return `首发于 ${label}`
  return undefined
})

/** 保存状态（顶栏只读展示，逻辑来自 store 的 isDirty/submitting） */
const saveState = computed<'new' | 'saved' | 'dirty' | 'saving' | 'uploading'>(() => {
  if (uploadingCount.value > 0) return 'uploading'
  if (submitting.value) return 'saving'
  if (articleStore.isDirty) return 'dirty'
  if (article.id) return 'saved'
  return 'new'
})

const saveStateText = computed(() => {
  switch (saveState.value) {
    case 'uploading':
      return '上传图片…'
    case 'saving':
      return '保存中…'
    case 'dirty':
      return '未保存'
    case 'saved':
      return '已保存'
    default:
      return '草稿'
  }
})

/** ≤960px 时设置栏收为底部抽屉 */
const wideQuery = window.matchMedia('(min-width: 60rem)')
const showPanel = ref(wideQuery.matches)
const onWideChange = (event: MediaQueryListEvent) => {
  showPanel.value = event.matches
}

/**
 * 加载文章数据（业务逻辑与原实现一致）
 */
const loadArticleData = async (id?: string) => {
  if (id) {
    try {
      const res = await getArticleDetail(id)
      if (res.code === 200) {
        articleStore.initEditArticle(res.data)
        markBaseline()
        loaded.value = true
      } else {
        ElMessage.error('文章不存在或无权编辑')
        router.replace('/editor')
      }
    } catch (error) {
      console.error('Failed to load article:', error)
      ElMessage.error('获取文章详情失败')
    }
  } else {
    // 新建模式：无论 store 是否残留旧 id，都清空状态，
    // 避免上次未保存退出的内容出现在下一次新建编辑中
    articleStore.resetEditArticle()
    markBaseline()
    loaded.value = true
  }
}

watch(
  () => route.params.id,
  (newId) => {
    if (route.name === 'ArticleEditor') {
      loadArticleData(newId as string)
    }
  },
)

// 监听内容变化：脏标记拿落库快照比对决定。自动保存不挂在这里——
// 一挂上去就变成「每敲一个字续一次期」，连续写作永远存不了
watch(
  () => [article.title, article.content],
  () => {
    if (!loaded.value) return // 初次加载不标记
    syncDirty()
  },
)

const goBack = () => {
  if (window.history.state && window.history.state.back) {
    router.back()
  } else {
    router.push('/')
  }
}

const handlePublish = () => {
  // 校验规则与原发布弹窗一致
  if (!article.categoryId) {
    ElMessage.warning('请在右侧设置中选择文章分类')
    settingsOpen.value = !showPanel.value
    return
  }
  if (article.status === 'scheduled') {
    if (!article.publishTime) {
      ElMessage.warning('请选择定时发布时间')
      return
    }
    if (dayjs(article.publishTime).isBefore(dayjs())) {
      ElMessage.warning('定时发布时间需晚于当前时间')
      return
    }
  }
  // 已发布文章：状态已在库中，「更新」只走纯内容保存通道，不带 status、不改 article.status
  if (isPublishedEdit.value) {
    handleRealSave(true)
    return
  }
  // 已下线文章走到这里即「上线」：与首次发布同一条通道，后端因 published_at 已有值不会覆盖首发时刻
  article.status = article.status === 'scheduled' ? 'scheduled' : 'published'
  handleRealSave(false)
}

const handleSaveDraft = () => {
  handleRealSave(true)
}

/** 手动保存的共用入口。标题与正文都得非空才落库（后端 PostCreateDTO 两处 @NotBlank），
    但守卫不能静默——原先 ⌘S 在内容为空时直接 return，按下去没有任何反应 */
const requestSaveDraft = () => {
  if (!article.title || !article.content) {
    ElMessage.warning('请先填写文章标题和内容')
    return
  }
  handleSaveDraft()
}

// Cmd/Ctrl + S 保存草稿
const onKeydown = (event: KeyboardEvent) => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    requestSaveDraft()
  }
}

onMounted(() => {
  wideQuery.addEventListener('change', onWideChange)
  window.addEventListener('keydown', onKeydown)
  initVditor()
  loadArticleData(route.params.id as string)
  startAutoSave()
})

onUnmounted(() => {
  wideQuery.removeEventListener('change', onWideChange)
  window.removeEventListener('keydown', onKeydown)
  stopAutoSave()
  hintPanelObserver?.disconnect()
  hintPanelObserver = null
  vditor?.destroy()
  vditor = null
})

// 路由守卫：未保存提醒（业务逻辑与原实现一致）
onBeforeRouteLeave((to, from, next) => {
  if (articleStore.isDirty) {
    ElMessageBox.confirm('当前内容尚未保存，确定要离开吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
      .then(() => next())
      .catch(() => next(false))
  } else {
    next()
  }
})

/**
 * Vditor IR（所见即所得）初始化
 */
const vditorEl = ref<HTMLDivElement>()
let vditor: Vditor | null = null
let vditorReady = false
/* 观察下拉面板显隐：面板经菜单项选择/点击编辑区等路径关闭时，
   vditor 不清除触发按钮上的 vditor-hint--current，需补齐以免高亮残留 */
let hintPanelObserver: MutationObserver | null = null

/**
 * 工具栏图标：16px / 1.5 stroke 手绘线性，与顶栏 icon-btn 同一语言，
 * 通过 toolbar 项的 icon 字段注入，替代 vditor 内置雪碧图。
 * 「H」字形单按钮直接设 H2；其余字形单击走原生 prefix/suffix 行为。
 */
const tbIcon = (body: string) =>
  `<svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">${body}</svg>`

const tbGlyph = (ch: string, textAttrs = '', body = '') =>
  `<svg class="cl-tb-glyph" viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><text x="8" y="12.4" text-anchor="middle" font-size="13" font-weight="700" ${textAttrs}>${ch}</text>${body}</svg>`

const tbCaret =
  '<svg class="cl-tb-caret" viewBox="0 0 10 10" width="10" height="10" fill="none" aria-hidden="true"><path d="m2.6 3.8 2.4 2.4 2.4-2.4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>'

const tbText = (label: string) => `<span class="cl-tb-label">${label}</span>${tbCaret}`

const tbIcons = {
  strike: tbGlyph('S', '', '<path d="M3.4 8.3h9.2" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>'),
  undo: tbIcon('<path d="M2.8 6.5H9a3.75 3.75 0 0 1 0 7.5H7"/><path d="M6 3.3 2.8 6.5 6 9.7"/>'),
  redo: tbIcon('<path d="M13.2 6.5H7a3.75 3.75 0 0 0 0 7.5h2"/><path d="m10 3.3 3.2 3.2L10 9.7"/>'),
  image: tbIcon(
    '<rect x="2.2" y="3.2" width="11.6" height="9.6" rx="1.4"/><circle cx="5.9" cy="6.4" r="1.15"/><path d="m2.6 11.6 3.6-3.6 2.6 2.6 1.9-1.9 2.7 2.7"/>',
  ),
  link: tbIcon(
    '<path d="m6.6 9.4 2.8-2.8"/><path d="M7.7 4.6 9.2 3.1a2.7 2.7 0 0 1 3.8 3.8l-1.5 1.5"/><path d="M8.3 11.4 6.8 12.9a2.7 2.7 0 0 1-3.8-3.8l1.5-1.5"/>',
  ),
  save: tbIcon(
    '<path d="M3.2 2.8h7.6l2.4 2.4v7.6a.9.9 0 0 1-.9.9H3.2a.9.9 0 0 1-.9-.9V3.7a.9.9 0 0 1 .9-.9Z"/><path d="M5.2 2.8v3.4h5V2.8"/><path d="M4.6 13.4v-4h6.8v4"/>',
  ),
  code: tbIcon('<path d="M5.2 4.2 1.9 8l3.3 3.8"/><path d="M10.8 4.2 14.1 8l-3.3 3.8"/><path d="m9.1 2.8-2.2 10.4"/>'),
  list: tbIcon(
    '<path d="M5.6 4.3h8"/><path d="M5.6 8h8"/><path d="M5.6 11.7h8"/><path class="cl-tb-dot" d="M2.6 4.3h.01"/><path class="cl-tb-dot" d="M2.6 8h.01"/><path class="cl-tb-dot" d="M2.6 11.7h.01"/>',
  ),
}

/** 保存按钮的提示语随状态变化：已上线与已下线文章的保存都是就地「更新」，不会把状态打回草稿 */
const saveTip = computed(() =>
  isPublishedEdit.value || isOfflineEdit.value ? '保存更新' : '保存草稿',
)

/**
 * 工具栏保存按钮状态：存在未落库变更时点亮红点，tooltip 跟随状态改文案。
 * vditor 的 tooltip 文案取自按钮的 aria-label（CSS content: attr(aria-label)），
 * 而工具栏在初始化时就固定，故状态变化后需就地改写该属性。
 */
const syncSaveButton = () => {
  const item = vditorEl.value?.querySelector('.cl-tb-save')
  if (!item) return
  const pending = ['dirty', 'saving', 'uploading'].includes(saveState.value)
  item.classList.toggle('is-dirty', pending)
  item.querySelector('button')?.setAttribute('aria-label', saveTip.value)
}

watch([saveState, saveTip], syncSaveButton)

const initVditor = () => {
  if (!vditorEl.value) return
  vditor = new Vditor(vditorEl.value, {
    cdn: '/vditor',
    lang: 'zh_CN',
    mode: 'ir',
    value: article.content,
    placeholder: '开始你的创作…',
    theme: isDark.value ? 'dark' : 'classic',
    height: 'auto',
    cache: { enable: false },
    /* 布局：行内格式与标题 │ 块级与插入下拉 │ 历史 │ 插入图片链接 │ 保存。
       prefix/suffix/hotkey 与 vditor 默认一致（仅换图标与文案）；
       下拉父项通过 toolbar 子数组走 vditor 原生 submenu 渲染与开合。
       「更多」同时收着引用/分隔线/表格：画布收进 --measure-prose 后工具栏只剩 688px，
       单独一枚只装表格的下拉会把整条挤成两行，合并后同宽余 76px。 */
    toolbar: [
      {
        name: 'bold',
        prefix: '**',
        suffix: '**',
        hotkey: '⌘B',
        icon: tbGlyph('B'),
        tip: '粗体',
        tipPosition: 'ne',
      },
      {
        name: 'italic',
        prefix: '*',
        suffix: '*',
        hotkey: '⌘I',
        icon: tbGlyph('I', 'font-style="italic"'),
        tip: '斜体',
        tipPosition: 'ne',
      },
      {
        /* 原生标题下拉直接展示，一级~六级都在面板里。
           原先它只挂载不展示（cl-tb-hidden），另做一枚「H」按钮把点击转发到它的 h2 项 —
           代价是工具栏只能设二级标题，想做三级得手动敲 ### */
        name: 'headings',
        className: 'cl-tb-text',
        icon: tbGlyph('H') + tbText('标题'),
        tip: '标题',
        tipPosition: 'n',
      },
      {
        name: 'strike',
        prefix: '~~',
        suffix: '~~',
        hotkey: '⌘D',
        icon: tbIcons.strike,
        tip: '删除线',
        tipPosition: 'ne',
      },
      '|',
      {
        name: 'cl-list',
        className: 'cl-tb-text',
        icon: tbIcons.list + tbText('列表'),
        tip: '列表',
        tipPosition: 'n',
        click: () => {},
        toolbar: [
          { name: 'list', prefix: '* ', hotkey: '⌘L' },
          { name: 'ordered-list', prefix: '1. ', hotkey: '⌘O' },
          { name: 'check', prefix: '* [ ] ', hotkey: '⌘J' },
        ],
      },
      {
        name: 'cl-code',
        className: 'cl-tb-text',
        icon: tbIcons.code + tbText('代码块'),
        tip: '代码块',
        tipPosition: 'n',
        click: () => {},
        toolbar: [
          { name: 'code', prefix: '```', suffix: '\n```', hotkey: '⌘U' },
          { name: 'inline-code', prefix: '`', suffix: '`', hotkey: '⌘G' },
        ],
      },
      {
        name: 'cl-format',
        className: 'cl-tb-text',
        icon: tbText('更多'),
        tip: '更多',
        tipPosition: 'n',
        click: () => {},
        toolbar: [
          { name: 'quote', prefix: '> ', hotkey: '⌘;' },
          { name: 'line', prefix: '***', hotkey: '⇧⌘H' },
          {
            name: 'table',
            // 表头留空：原先插的是 col1/col2/col3 三个字面占位符，作者得手删掉才像话
            prefix: '|  |  |  ',
            suffix: ' |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |',
            hotkey: '⌘M',
          },
        ],
      },
      '|',
      { name: 'undo', icon: tbIcons.undo, hotkey: '⌘Z', tip: '撤销', tipPosition: 'n' },
      { name: 'redo', icon: tbIcons.redo, hotkey: '⌘Y', tip: '重做', tipPosition: 'n' },
      '|',
      {
        name: 'cl-image',
        icon: tbIcons.image,
        tip: '插入图片',
        tipPosition: 'n',
        // 工具栏按钮改为打开图片上传弹窗；拖拽/粘贴仍走 vditor 原生 upload handler
        click: () => {
          imageDialogOpen.value = true
        },
      },      {
        // 自定义名称：内置 link 项按名合并默认 prefix/suffix（[xx](https://)），
        // 自定义 click 无法覆盖，参考 cl-image 的做法换名挂载
        name: 'cl-link',
        hotkey: '⌘K',
        icon: tbIcons.link,
        tip: '链接',
        tipPosition: 'n',
        // 工具栏/⌘K 打开插入链接弹窗；确认后仍走 vditor.insertValue 生成 [文本](url)
        click: () => {
          pendingLinkText = vditor?.getSelection()?.trim() ?? ''
          linkDialogOpen.value = true
        },
      },
      '|',
      {
        name: 'cl-save',
        className: 'cl-tb-save',
        icon: tbIcons.save,
        tip: saveTip.value,
        hotkey: '⌘S',
        tipPosition: 'nw',
        click: () => requestSaveDraft(),
      },
    ],
    preview: {
      mode: 'editor',
      theme: { current: isDark.value ? 'dark' : 'light' },
      hljs: { style: 'atom-one-dark', lineNumber: false },
    },
    upload: {
      accept: 'image/*',
      multiple: false,
      handler: onUploadImages,
    },
    input: (value) => {
      article.content = value
    },
    after: () => {
      vditorReady = true
      syncSaveButton()
      // 文字下拉按钮已有可见名称，去掉悬停 tooltip（保留 aria-label 语义；
      // 同时让 vditor 对无 tooltip 的按钮在面板展开时高亮 vditor-hint--current）
      vditorEl.value
        ?.querySelectorAll('.cl-tb-text > button')
        .forEach((btn) => btn.classList.remove('vditor-tooltipped', 'vditor-tooltipped__n'))
      // 面板隐藏时同步清掉触发按钮的展开高亮
      const toolbarEl = vditorEl.value?.querySelector('.vditor-toolbar')
      if (toolbarEl) {
        hintPanelObserver = new MutationObserver(() => {
          toolbarEl.querySelectorAll('.vditor-toolbar__item > .vditor-hint').forEach((panel) => {
            if ((panel as HTMLElement).style.display !== 'block') {
              panel.parentElement?.querySelector('button')?.classList.remove('vditor-hint--current')
            }
          })
        })
        hintPanelObserver.observe(toolbarEl, {
          subtree: true,
          attributes: true,
          attributeFilter: ['style'],
        })
      }
      if (article.content) {
        vditor?.setValue(article.content)
      }
    },
  })
}

// 异步加载完成后同步进编辑器（初始回显）
watch(
  () => article.content,
  (val) => {
    if (!vditor || !vditorReady) return
    if (vditor.getValue() === val) return
    vditor.setValue(val ?? '')
  },
)

// 主题切换同步到编辑器
watch(isDark, (dark) => {
  vditor?.setTheme(dark ? 'dark' : 'classic', dark ? 'dark' : 'light', 'atom-one-dark')
})

/**
 * 拖拽/粘贴上传。Vditor 的 upload.handler 是接管式钩子：返回值只会被当作提示文案
 * 弹 tip.show，不会插入正文（插入逻辑 genUploadedLabel 仅存在于 upload.url 分支），
 * 故此处自行插入并返回 null 抑制 tip。
 */
const onUploadImages = async (files: File[]): Promise<null> => {
  const markdown = await uploadImages(files)
  if (markdown) insertImageMarkdown(markdown)
  return null
}

const uploadImages = async (files: File[]): Promise<string | null> => {
  uploadingCount.value += files.length
  try {
    const urls: string[] = []
    for (const file of files) {
      try {
        const res = await uploadArticleImage(file)
        if (res.code === 200) {
          urls.push(formatMinioUrl(res.data))
        } else {
          throw new Error(res.message || '上传失败')
        }
      } catch (error) {
        console.error('Image upload failed:', error)
        ElMessage.error(`图片 ${file.name} 上传失败`)
      }
    }
    if (urls.length === 0) return null
    return urls.map((url) => `![](${url})`).join('\n')
  } finally {
    uploadingCount.value = Math.max(0, uploadingCount.value - files.length)
  }
}

/**
 * 图片上传弹窗确认后插入 Markdown（与 vditor handler 相同的 `![](url)` 片段），
 * 插入后手动同步 article.content 以触发 dirty/自动保存
 */
const insertImageMarkdown = (markdown: string) => {
  vditor?.insertValue(markdown)
  article.content = vditor?.getValue() ?? article.content
}

/**
 * 插入链接弹窗确认后插入 [文本](url)：有选区时以选区为链接文字，
 * 否则用 URL 本身；插入路径与图片一致（insertValue + 手动同步 content）
 */
const insertLinkMarkdown = (url: string) => {
  const text = pendingLinkText || url
  vditor?.insertValue(`[${text}](${url})`)
  article.content = vditor?.getValue() ?? article.content
  pendingLinkText = ''
}

/**
 * 自动保存：固定节拍，与输入事件解耦
 */
const startAutoSave = () => {
  stopAutoSave()
  autoSaveTimer = setInterval(() => {
    // 提交中/上传中不打断；标题或正文为空时后端会拒，也就没必要落一个空壳草稿
    if (!articleStore.isDirty || submitting.value || uploadingCount.value > 0) return
    if (!article.title || !article.content) return
    void handleRealSave(true, true)
  }, AUTO_SAVE_MS)
}

const stopAutoSave = () => {
  if (autoSaveTimer) {
    clearInterval(autoSaveTimer)
    autoSaveTimer = null
  }
}

/**
 * 核心保存/发布流程（业务逻辑与原实现一致）
 * @param isDraft 草稿模式：已有文章不带 status，只存内容（已发布文章的「更新」同走此通道）
 * @param isSilent 是否静默保存（不显示成功提示，用于自动保存）
 */
const handleRealSave = async (isDraft = false, isSilent = false) => {
  if (submitting.value) return
  submitting.value = true

  try {
    // 1. 处理封面图 (如果存在待上传的本地文件)
    let finalCover = article.cover
    if (articleStore.localCover) {
      if (!isSilent) ElMessage.info('正在上传封面图...')
      const res = await uploadCoverImage(articleStore.localCover)
      if (res.code === 200) {
        finalCover = formatMinioUrl(res.data)
        article.cover = finalCover
        articleStore.localCover = null // 上传成功后清空本地文件
      }
    }

    // 2. 构造提交数据
    const tagIds = article.tags.filter((t) => typeof t === 'number')
    const submitData = {
      id: article.id,
      title: article.title,
      content: article.content,
      cover: finalCover,

      // 草稿免分类：categoryId 可空，发布/定时时由 handlePublish 的校验兜住（后端同样强制）
      categoryId: article.categoryId ?? null,
      tagIds,
      // 草稿模式只存内容：已有文章不带 status，否则 30 秒自动保存会把已发布/定时文章打回草稿
      status: isDraft && isEdit.value ? undefined : article.status,
      // 定时发布时间（仅 scheduled 时有值；修复原先未随载荷发送的遗漏）
      publishTime: article.status === 'scheduled' ? article.publishTime : null,
    }

    // 3. 提交到后端
    let result
    if (isEdit.value) {
      result = await updateArticle(article.id!, submitData)
    } else {
      result = await createArticle(submitData)
    }

    if (result.code === 200) {
      articleStore.isDirty = false
      markBaseline()
      // 只有载荷真带了 status 才算库中状态变了；纯内容保存不带 status，不能改写快照
      if (submitData.status !== undefined) articleStore.dbStatus = article.status

      // 4. 处理保存后的状态切换：只有新建需要回填 ID 并切到更新路径，更新接口无返回载荷
      let savedId = article.id
      if (!isEdit.value) {
        const newId = result?.data?.id
        if (!newId) throw new Error('创建文章失败：未返回文章ID')
        savedId = newId
        article.id = newId
        router.replace({ name: 'ArticleEditor', params: { id: newId } })
      }

      if (!isSilent) {
        if (isDraft) {
          if (isPublishedEdit.value) {
            settingsOpen.value = false
            ElMessage.success('文章已更新')
          } else {
            ElMessage.success('草稿已保存')
          }
        } else {
          settingsOpen.value = false
          router.replace({ path: '/publish-success', query: { id: savedId } })
        }
      }
    }
  } catch (error) {
    console.error('Save failed:', error)
    if (!isSilent) ElMessage.error('操作失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss">
/* 编辑器页为全屏工作台；vditor 结构复杂，使用全局类而非 scoped。
   所有颜色/字体仍引用 assets/styles/variables.css 的 Token。 */

.editor-page {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  background-color: var(--color-paper);
  color: var(--color-ink);
}

/* ---- 顶栏 ---- */
.editor-topbar {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  height: var(--header-h);
  padding-inline: var(--space-md);
  border-bottom: 1px solid var(--color-rule);
  background-color: var(--color-paper);

  &__left {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
    min-width: 0;
  }

  &__rule {
    width: 1px;
    height: 1rem;
    background-color: var(--color-rule);
  }

  &__right {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
  }
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border-radius: var(--radius-sm);
  color: var(--color-ink-3);
  transition:
    color var(--dur-micro) var(--ease-out),
    background-color var(--dur-micro) var(--ease-out);

  &:hover:not(:disabled) {
    color: var(--color-ink);
    background-color: var(--color-paper-3);
  }

  &:disabled {
    color: var(--color-ink-4);
    cursor: not-allowed;
  }
}

/* 状态徽章 + 保存状态：同为顶栏只读小字，紧密成组 */
.topbar-status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2xs);
  min-width: 0;
}

.article-status {
  font-size: var(--text-xs);
  color: var(--color-ink-4);
  white-space: nowrap;

  &__sep {
    font-size: var(--text-xs);
    color: var(--color-ink-4);
  }
}

.save-status {
  font-size: var(--text-xs);
  color: var(--color-ink-4);
  white-space: nowrap;

  &.is-dirty {
    color: var(--color-accent);
  }
}

.publish-btn {
  height: 2rem;
  padding-inline: var(--space-md);
  font-size: var(--text-sm);
  color: var(--color-paper-2);
  background-color: var(--color-ink);
  border-radius: var(--radius-sm);
  white-space: nowrap;
  transition:
    background-color var(--dur-micro) var(--ease-out),
    transform 100ms var(--ease-out);

  &:hover:not(:disabled) {
    background-color: var(--color-accent);
  }

  &:active:not(:disabled) {
    transform: translateY(1px);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

/* ---- 主体：画布 + 设置栏 ---- */
.editor-body {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: start;
}

.editor-canvas {
  min-width: 0;
}

/* 画布收在 --measure-prose 并居中：§3.9「正文区宽度按阅读宽度上限」。
   整块按 §6.2 卡片范式抬升（与详情页 .article-detail__post 同底、同描边、同阴影），
   标题与工具栏都在这张面里，所以工具栏吸顶时底色必须跟着走纸面，否则正文从它底下滚过时露色块。
   顶部让开一档：卡片描边若贴着顶栏发丝线会叠成 2px 双线。 */
.editor-sheet {
  max-width: var(--measure-prose);
  margin: var(--space-md) auto 0;
  padding: var(--space-xl) var(--space-lg) var(--space-3xl);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-whisper);
}

/* 标题与正文同一条左缘：input 的 UA 默认 2px 内衬要去掉，
   且不能同时写 width:100% 和 margin-inline——那样盒子会溢出画布右缘。 */
.editor-title {
  display: block;
  width: 100%;
  padding-inline: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 700;
  line-height: 1.3;
  color: var(--color-ink);
  margin-bottom: var(--space-md);

  &::placeholder {
    color: var(--color-ink-4);
  }
}

/* ---- 桌面设置栏 ---- */
.editor-panel {
  display: none;
  position: sticky;
  top: var(--header-h);
  max-height: calc(100dvh - var(--header-h));
  overflow-y: auto;
  width: 17rem;
  border-inline-start: 1px solid var(--color-rule);
  background-color: var(--color-paper);

  &__head {
    padding: var(--space-md) var(--space-md) 0;
    font-size: var(--text-xs);
    letter-spacing: 0.08em;
    color: var(--color-ink-4);
  }
}

.settings-drawer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg) var(--space-sm);
  font-size: var(--text-xs);
  letter-spacing: 0.08em;
  color: var(--color-ink-4);
}

/* ---- 预览覆盖层 ---- */
.preview-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: flex;
  flex-direction: column;
  background-color: var(--color-paper);

  &__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: var(--header-h);
    padding-inline: var(--space-md);
    border-bottom: 1px solid var(--color-rule);
  }

  &__label {
    font-size: var(--text-xs);
    letter-spacing: 0.08em;
    color: var(--color-ink-4);
  }

  &__canvas {
    flex: 1;
    overflow-y: auto;
  }

  &__sheet {
    max-width: var(--measure-prose);
    margin-inline: auto;
    padding: var(--space-xl) var(--space-lg) var(--space-3xl);
  }

  &__title {
    font-size: var(--text-2xl);
    font-weight: 700;
    line-height: 1.3;
    margin-bottom: var(--space-lg);
    padding-bottom: var(--space-lg);
    border-bottom: 1px solid var(--color-rule);
  }
}

/* ---- Vditor 定制：贴合 Token 与阅读版式 ---- */
.editor-page .vditor {
  border: none;
  border-radius: 0;
  background: transparent;
  height: auto;
  min-height: 60vh;
  box-shadow: none;
  /* Vditor 内部表面按语义落到 Token：浮层用抬升面，代码编辑态用凹陷面 */
  --panel-background-color: var(--color-paper-2);
  --textarea-background-color: var(--color-paper-3);
  --toolbar-background-color: var(--color-paper);
  /* 工具栏尺寸与图标色走 vditor 内部变量，值引用 Token */
  --toolbar-height: 2rem;
  --toolbar-icon-color: var(--color-ink-3);
  --toolbar-icon-hover-color: var(--color-ink);
  --second-color: var(--color-rule);
}

.editor-page .vditor-content {
  min-height: 55vh;
  background: transparent;
}

/* ---- 写作面版式：与详情页同一套值 ---- *
 * 特异性是这里的重点：vditor 的 content-theme/light.css 由运行时注入，落在级联末端，
 * 同特异性会盖过本文件（行内代码的 --color-paper-3 就是这么被 rgba(27,31,35,.05) 顶掉的）。
 * 所以一律抬到 .editor-page .vditor .vditor-reset 以上，不赌顺序。
 * 字号从 1.0625rem（17px 裸值，既跳出资阶又比阅读页大一号）回 --text-base，
 * 与阅读页同大小；标题档位整体下移一格对齐阅读页——那边 `#` 渲染后会被下移成 h2。 */
.editor-page .vditor .vditor-reset,
.editor-page .vditor-ir > .vditor-reset {
  font-family: var(--font-body);
  font-size: var(--text-base);
  line-height: 1.75;
  color: var(--color-ink-2);
  max-width: 100%;
  margin-inline: 0;
  padding-block: var(--space-sm);
  background: transparent;
  word-break: normal;
  overflow-wrap: break-word;
}

.editor-page .vditor-ir > .vditor-reset,
.editor-page .vditor--dark .vditor-ir > .vditor-reset,
.editor-page .vditor--dark .vditor-reset {
  background: transparent;
}

.editor-page .vditor .vditor-reset :is(h1, h2, h3, h4, h5, h6) {
  font-family: var(--font-display);
  font-weight: 700;
  font-style: normal;
  line-height: 1.35;
  color: var(--color-ink);
  word-break: normal;
  margin: var(--space-lg) 0 var(--space-sm);
  /* light.css 给 h1/h2 画了一条 GitHub 式下划线，阅读页没有 */
  padding-block-end: 0;
  border-block-end: none;
}

.editor-page .vditor .vditor-reset h1 {
  font-size: var(--text-xl);
}

.editor-page .vditor .vditor-reset h2 {
  font-size: var(--text-lg);
}

.editor-page .vditor .vditor-reset h3 {
  font-size: var(--text-md);
}

.editor-page .vditor .vditor-reset h4 {
  font-size: var(--text-base);
}

.editor-page .vditor .vditor-reset :is(h5, h6) {
  font-size: var(--text-sm);
}

.editor-page .vditor .vditor-reset h6 {
  color: var(--color-ink-3);
}

.editor-page .vditor .vditor-reset p {
  margin: var(--space-sm) 0;
}

.editor-page .vditor .vditor-reset a {
  color: var(--color-accent);
  text-decoration: underline;
  text-underline-offset: 0.2em;
}

/* IR 写作态不生成 <a>，链接文字挂在 .vditor-ir__link 上，库给它写死纯蓝 #00f。
   下划线留着——它在写作面上就是「这是一枚链接」的标记，与阅读页一致。 */
.editor-page .vditor .vditor-reset .vditor-ir__link {
  color: var(--color-accent);
}

/* 全局 reset.css 清空了 ul/ol 的 list-style；vditor 只还原了 ul（disc），
   ol 的十进制序号需在此补回，否则有序列表不显示序号 */
.editor-page .vditor .vditor-reset :is(ul, ol) {
  margin: var(--space-sm) 0;
  padding-inline-start: var(--space-lg);
}

.editor-page .vditor .vditor-reset ol {
  list-style-type: decimal;
}

.editor-page .vditor .vditor-reset li {
  margin: var(--space-2xs) 0;
}

.editor-page .vditor .vditor-reset blockquote {
  margin: var(--space-lg) 0;
  padding: 0 var(--space-md);
  border-inline-start: 2px solid var(--color-rule-strong);
  color: var(--color-ink-3);
  line-height: inherit;
}

/* vditor 把 hr 留成 inline-block：边距不与相邻段落折叠（12+24=36），
   而且它坐在 28px 的行盒基线上，下方还挂着一截降部空间（实测 43）。
   改成 block 之后与阅读页同一套折叠结果：上下各 24。 */
.editor-page .vditor .vditor-reset hr {
  display: block;
  height: 0;
  margin: var(--space-lg) 0;
  border: none;
  border-block-start: 1px solid var(--color-rule);
  /* light.css 把 hr 做成 2px 的 #eaecef 粗条，阅读页是 1px 发丝线 */
  background-color: transparent;
}

/* 行内代码：库的选择器带两串 :not()，特异性 0,3,1 与本文件打平且注入在后，
   必须把 :not() 一起抄上才压得过 */
.editor-page .vditor .vditor-reset code:not(.hljs):not(.highlight-chroma) {
  font-family: var(--font-mono);
  font-size: 0.875em;
  line-height: 1;
  color: var(--color-ink);
  background-color: var(--color-paper-3);
  background-image: none;
  border-radius: var(--radius-xs);
  padding: var(--space-3xs) var(--space-2xs);
}

/* 表格：库是 display:block + 单元格 white-space:nowrap + GitHub 三色，
   改成与阅读页同款的发丝线 + 表头底色微差 */
.editor-page .vditor .vditor-reset table {
  display: table;
  width: 100%;
  margin: var(--space-lg) 0;
  overflow: visible;
  border-collapse: collapse;
  word-break: normal;
}

.editor-page .vditor .vditor-reset :is(th, td) {
  padding: var(--space-xs) var(--space-sm);
  border: 1px solid var(--color-rule);
  color: var(--color-ink-2);
  white-space: normal;
  text-align: left;
}

.editor-page .vditor .vditor-reset th {
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-ink);
  background-color: var(--color-paper-3);
}

/* vditor 还在 tr 上挂了一条 GitHub 的 #c6cbd1 上边线，与单元格的发丝线叠成两道 */
.editor-page .vditor .vditor-reset table tr,
.editor-page .vditor .vditor-reset table tbody tr:nth-child(2n) {
  background-color: transparent;
  border-top: none;
}

/* 与阅读页同一处理：块级图片会把所在段落劈成两段行内上下文，
   图片行与下一行之间没有空行时，那个换行就留下一个空行盒（实测上下 53/44）。
   行内块后图片仍各占一行（源文本里的换行负责断开），空带回到段落缝那一档。
   贴在段首时让位给段落自己的上边距，否则叠成 27。下边距留着：
   IR 的正文是裸文本节点，:first-child/:last-child 只数元素，分不清「图独占一段」
   和「图后面还有字」，压成 0 会让后者贴到 3px。
   max-height 与阅读页同一枚 Token：写作面收多高，发布后就收多高，否则「所见即所得」
   在长图上不成立。不给 cursor: zoom-in —— 灯箱只在阅读页，写作面点图归 Vditor 展开节点。 */
.editor-page .vditor .vditor-reset img {
  display: inline-block;
  vertical-align: bottom;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: var(--figure-max-h);
  margin: var(--space-sm) 0;
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
}

/* 被限高收窄的图贴着画布左缘像版式出错，与阅读页一样两侧均分留白 */
.editor-page .vditor-ir__node[data-type='img'] {
  text-align: center;
}

.editor-page .vditor-ir__node[data-type='img']:first-child img {
  margin-block-start: 0;
}

.editor-page .vditor .vditor-reset pre {
  background-color: var(--color-paper-3);
}

.editor-page .vditor .vditor-reset pre > code {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  /* 库给代码块垫了一张 base64 网点纹理图 */
  background-image: none;
}

/* IR 代码块的外层是 .vditor-ir__node，它自己不带外边距（库的 margin 挂在里面的 pre 上，
   折不出节点框），所以与阅读页那一档 24 对不上，实测只差 12。补在外层上。 */
.editor-page .vditor-ir__node[data-type='code-block'] {
  display: block;
  margin: var(--space-lg) 0;
  font-family: var(--font-mono);
}

/* IR 代码块编辑态：常态与聚焦都用凹陷面，不随 --textarea-background-color 泛白。
   注意写作面本身也是 pre.vditor-reset，必须限定在代码块节点内。 */
.editor-page .vditor-ir__node[data-type='code-block'] pre.vditor-reset,
.editor-page .vditor-ir__node[data-type='code-block'] pre.vditor-reset:focus {
  background-color: var(--color-paper-3);
}

/* 写作面（.vditor-ir 直接子级 pre）聚焦时不染色：Vditor 的
   pre.vditor-reset:focus 规则本意是代码块编辑态，会误伤整个写作面 */
.editor-page .vditor-ir > pre.vditor-reset,
.editor-page .vditor-ir > pre.vditor-reset:focus {
  background: transparent;
}

/* 覆盖 Vditor 的动态行宽边距（JS 内联 style，随宽度可达 400px+/侧）：
   画布已经收在 --measure-prose，这里归零让正文与标题共用画布那一档内衬 */
.editor-page .vditor-ir > pre.vditor-reset {
  padding: var(--space-md) 0 !important;
}

/* 浮层：下拉（标题/表格提示）、@ 补全、大纲统一为抬升面 + 发丝线 + 弱阴影 */
.editor-page .vditor-panel,
.editor-page .vditor-dropdown,
.editor-page .vditor-hint,
.editor-page .vditor-outline {
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-whisper);
}

/* vditor 的箭头是单层填充三角，无法跟随面板发丝边框，直接去掉保持干净卡片 */
.editor-page .vditor-panel--arrow:before,
.editor-page .vditor-dropdown--arrow:before {
  display: none;
}

/* 工具栏：单行、无边框按钮、全 Token。
   vditor 默认 float 布局改 flex 以统一间距与分组；
   下拉面板绝对定位在按钮项内，故不可加 overflow 裁切 */
.editor-page .vditor-toolbar {
  position: sticky;
  top: var(--header-h);
  z-index: var(--z-raised);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3xs);
  padding-block: var(--space-2xs);
  background-color: var(--color-paper-2);
  border-bottom: 1px solid var(--color-rule);
  /* 画布已收窄，工具栏首键与正文左缘对齐：内衬归零，交给 .editor-sheet 那一档 */
  padding-inline: 0 !important;
}

/* 按钮：2rem 方形无边框，交互与顶栏 icon-btn、首页列表一致 */
.editor-page .vditor-toolbar__item {
  position: relative;
}

.editor-page .vditor-toolbar__item > button {
  position: relative;
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--color-ink-3);
  cursor: pointer;
  font-family: var(--font-body);
  transition:
    color var(--dur-micro) var(--ease-out),
    background-color var(--dur-micro) var(--ease-out);
}

.editor-page .vditor-toolbar__item > button:hover {
  color: var(--color-ink);
  background-color: var(--color-paper-3);
}

/* 下拉展开期间触发按钮保持高亮：vditor 原生会加 vditor-hint--current，
   但其默认背景与工具栏同色等于不可见，这里对齐 hover 态 */
.editor-page .vditor-toolbar__item > button.vditor-hint--current {
  color: var(--color-ink) !important;
  background-color: var(--color-paper-3) !important;
}

.editor-page .vditor-toolbar__item > button:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
}

/* 光标位于对应格式内时 vditor 置 vditor-menu--current */
.editor-page .vditor-toolbar__item > button.vditor-menu--current {
  color: var(--color-accent);
  background-color: var(--color-accent-wash);
}

/* 禁用态：vditor 默认用 --second-color（此处为发丝线色）+ !important，浅底上不可见；
   统一改为设计系统的禁用色 ink-4 */
.editor-page .vditor-menu--disabled {
  color: var(--color-ink-4) !important;
  opacity: 1;
  cursor: not-allowed;
}

/* 图标统一 16px / 1.5 stroke；B/I/H/S 字形以文本绘制，单独填充 */
.editor-page .vditor-toolbar__item svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.editor-page .cl-tb-glyph text {
  fill: currentColor;
  stroke: none;
}

.editor-page .cl-tb-dot {
  stroke-width: 2;
}

/* 文字下拉按钮：图标/文字 + caret */
.editor-page .cl-tb-text > button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3xs);
  width: auto;
  padding-inline: var(--space-xs);
  font-size: var(--text-sm);
  color: var(--color-ink-2);
}

.editor-page .cl-tb-label {
  white-space: nowrap;
}

.editor-page .cl-tb-text .cl-tb-caret {
  width: 10px;
  height: 10px;
  color: var(--color-ink-4);
}

/* 功能分组：发丝细线。左右各让 --space-xs，整条 17px——
   画布收进 --measure-prose 后工具栏只有 688px，四根分隔线再宽就把整条挤成两行 */
.editor-page .vditor-toolbar__divider {
  height: 1.25rem;
  border-left: 1px solid var(--color-rule);
  margin-inline: var(--space-xs);
}

/* 下拉面板菜单项（标题/列表/代码块/更多） */
.editor-page .vditor-hint {
  padding: var(--space-2xs);
  min-width: 9rem;
}

/* 面板绝对定位时静态位置紧贴按钮底缘，与工具栏留出一档呼吸间距
   （只作用于子菜单面板，不影响代码块语言提示等 JS 定位的浮层） */
.editor-page .vditor-hint.vditor-panel--arrow {
  margin-top: var(--space-sm);
}

.editor-page .vditor-hint button {
  font-family: var(--font-body);
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  border-radius: var(--radius-xs);
}

.editor-page .vditor-hint button:not(.vditor-menu--disabled):hover {
  color: var(--color-ink) !important;
  background-color: var(--color-paper-3) !important;
}

/* 工具栏吸顶后向上弹出的 tooltip 会被顶栏遮挡，统一改为在按钮下方显示。
   只翻转垂直定位，各按钮原有的水平对齐（n/ne/nw 的 left/right 系）保持不变 */
.editor-page .vditor-toolbar__item .vditor-tooltipped::after {
  top: 100%;
  bottom: auto;
  margin-top: var(--space-3xs);
  margin-bottom: 0;
}

.editor-page .vditor-toolbar__item .vditor-tooltipped::before {
  top: auto;
  bottom: calc(-1 * var(--space-3xs));
  border-top-color: transparent;
  border-bottom-color: var(--color-ink);
}

/* 保存按钮红点：与顶栏「未保存」状态同源。
   红点放在按钮外层容器的 ::after 上——按钮自身的 ::after 被 vditor
   tooltip（.vditor-tooltipped）占用，占用后 hover 会出现孤立箭头 */
.editor-page .cl-tb-save.is-dirty::after {
  content: '';
  position: absolute;
  z-index: var(--z-base);
  top: var(--space-2xs);
  right: var(--space-2xs);
  width: 0.375rem;
  height: 0.375rem;
  border-radius: 50%;
  background-color: var(--color-danger);
  pointer-events: none;
}

/* IR 行内标记弱化：光标进入才显示 markdown 符号 */
.editor-page .vditor-ir__marker {
  color: var(--color-ink-4);
}

.editor-page .vditor-ir__node--hidden .vditor-ir__marker {
  display: none;
}

/* 窄屏 */
@media (max-width: 60rem) {
  .settings-toggle {
    display: grid;
  }

  .publish-btn {
    padding-inline: var(--space-sm);
  }
}

@media (min-width: 60rem) {
  .settings-toggle {
    display: none;
  }

  .editor-body {
    grid-template-columns: minmax(0, 1fr) 17rem;
  }

  .editor-panel {
    display: block;
  }
}
</style>
