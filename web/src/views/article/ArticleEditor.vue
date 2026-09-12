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
        <span class="save-status" :class="{ 'is-dirty': saveState === 'dirty' }">
          {{ saveStateText }}
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
let autoSaveTimer: ReturnType<typeof setTimeout> | null = null

// 是否为编辑模式：通过文章 ID 判定
const isEdit = computed(() => !!article.id)
const loaded = ref(false)

const canPublish = computed(() => !!article.title && !!article.content)

const publishLabel = computed(() => {
  if (submitting.value) return '保存中…'
  return article.status === 'scheduled' ? '定时发布' : '发布'
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
      const res = await getArticleDetail(Number(id))
      if (res.code === 200) {
        articleStore.initEditArticle(res.data)
        loaded.value = true
      } else {
        ElMessage.error('文章不存在或无权编辑')
        router.replace('/editor')
      }
    } catch (error) {
      console.error('Failed to load article:', error)
      ElMessage.error('获取文章详情失败')
    }
  } else if (article.id) {
    articleStore.resetEditArticle()
    loaded.value = true
  } else {
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

// 监听内容变化，标记为 dirty 并开启自动保存
watch(
  () => [article.title, article.content],
  () => {
    if (!loaded.value) return // 初次加载不标记
    articleStore.isDirty = true
    startAutoSave()
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
  article.status = article.status === 'scheduled' ? 'scheduled' : 'published'
  handleRealSave(false)
}

const handleSaveDraft = () => {
  article.status = 'draft'
  handleRealSave(true)
}

// Cmd/Ctrl + S 保存草稿
const onKeydown = (event: KeyboardEvent) => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    if (article.title && article.content) handleSaveDraft()
  }
}

onMounted(() => {
  wideQuery.addEventListener('change', onWideChange)
  window.addEventListener('keydown', onKeydown)
  initVditor()
  loadArticleData(route.params.id as string)
})

onUnmounted(() => {
  wideQuery.removeEventListener('change', onWideChange)
  window.removeEventListener('keydown', onKeydown)
  stopAutoSave()
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

const initVditor = () => {
  if (!vditorEl.value) return
  vditor = new Vditor(vditorEl.value, {
    cdn: '/vditor',
    mode: 'ir',
    value: article.content,
    placeholder: '开始你的创作…',
    theme: isDark.value ? 'dark' : 'classic',
    height: 'auto',
    cache: { enable: false },
    toolbar: [
      'headings',
      'bold',
      'italic',
      '|',
      'list',
      'ordered-list',
      'quote',
      '|',
      'code',
      'inline-code',
      'link',
      'image',
      'table',
      '|',
      'undo',
      'redo',
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
 * 图片上传（拖拽/粘贴/工具栏），复用原有上传接口
 * handler 返回要插入的 markdown 片段，返回 null 表示失败（已提示）
 */
const onUploadImages = (files: File[]): Promise<string> | Promise<null> => {
  // Vditor 的 handler 类型把成功/失败拆成两个 Promise 分支，这里统一收口
  return uploadImages(files) as Promise<string> | Promise<null>
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
 * 自动保存逻辑（业务逻辑与原实现一致）
 */
const startAutoSave = () => {
  stopAutoSave()
  // 30秒自动保存一次草稿
  autoSaveTimer = setTimeout(async () => {
    if (articleStore.isDirty && article.title && article.content) {
      await handleRealSave(true, true)
    }
  }, 30000)
}

const stopAutoSave = () => {
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer)
    autoSaveTimer = null
  }
}

/**
 * 核心保存/发布流程（业务逻辑与原实现一致）
 * @param isDraft 是否为草稿模式
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
      excerpt: article.excerpt,
      cover: finalCover,

      // 草稿文章分类默认为第一个分类
      categoryId: article.categoryId || 1,
      tagIds,
      status: isDraft ? 'draft' : article.status,
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
      const newId = result?.data?.id
      if (!newId) throw new Error('创建文章失败：未返回文章ID')

      // 4. 处理保存后的状态切换
      if (!isEdit.value) {
        // 第一次创建成功，更新 ID 并切换路由
        article.id = newId
        router.replace({ name: 'ArticleEditor', params: { id: newId } })
      }

      if (!isSilent) {
        if (isDraft) {
          ElMessage.success('草稿已保存')
        } else {
          settingsOpen.value = false
          router.push({ path: '/publish-success', query: { id: newId } })
        }
      }
    }
  } catch (error) {
    console.error('Save failed:', error)
    if (!isSilent) ElMessage.error('操作失败，请重试')
  } finally {
    submitting.value = false
    if (isDraft) startAutoSave() // 保存完后继续开启自动保存
  }
}
</script>

<style lang="scss">
/* 编辑器页为全屏工作台；vditor 结构复杂，使用全局类而非 scoped。
   所有颜色/字体仍引用 assets/styles/variables.css 的 Token。 */

.editor-page {
  --editor-gutter: clamp(var(--space-md), 3vw, var(--space-xl));
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

.editor-sheet {
  width: 100%;
  padding: var(--space-xl) var(--space-lg) var(--space-3xl);
}

.editor-title {
  display: block;
  width: 100%;
  margin-inline: var(--editor-gutter);
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
    max-width: 46rem;
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
}

.editor-page .vditor-content {
  min-height: 55vh;
  background: transparent;
}

/* 正文画布：白底去卡片化，阅读版式与详情页一致 */
.editor-page .vditor .vditor-reset,
.editor-page .vditor-ir > .vditor-reset {
  font-family: var(--font-body);
  font-size: 1.0625rem;
  line-height: 1.75;
  color: var(--color-ink-2);
  max-width: 100%;
  margin-inline: 0;
  padding-block: var(--space-sm);
  background: transparent;
}

.editor-page .vditor-ir > .vditor-reset,
.editor-page .vditor--dark .vditor-ir > .vditor-reset,
.editor-page .vditor--dark .vditor-reset {
  background: transparent;
}

.editor-page .vditor .vditor-reset h1,
.editor-page .vditor .vditor-reset h2 {
  font-weight: 700;
  color: var(--color-ink);
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

.editor-page .vditor .vditor-reset code {
  font-family: var(--font-mono);
  background-color: var(--color-paper-3);
  border-radius: var(--radius-xs);
}

.editor-page .vditor .vditor-reset pre {
  background-color: var(--color-paper-3);
}

.editor-page .vditor-ir__node[data-type='code-block'] {
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
   改为与标题一致的固定响应式栏距 */
.editor-page .vditor-ir > pre.vditor-reset {
  padding: var(--space-md) var(--editor-gutter, clamp(var(--space-md), 3vw, var(--space-xl))) !important;
}

.editor-page .vditor-toolbar {
  padding-inline: var(--editor-gutter, clamp(var(--space-md), 3vw, var(--space-xl))) !important;
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

.editor-page .vditor-panel--arrow:before,
.editor-page .vditor-dropdown--arrow:before {
  border-bottom-color: var(--color-paper-2);
}

.editor-page .vditor-hint button:hover {
  background-color: var(--color-paper-3);
}

/* 工具栏：轻、无边框 */
.editor-page .vditor-toolbar {
  position: sticky;
  top: var(--header-h);
  z-index: var(--z-raised);
  background-color: var(--color-paper);
  border-bottom: 1px solid var(--color-rule);
  padding-inline: 0;
}

.editor-page .vditor-toolbar-item {
  color: var(--color-ink-3);
}

.editor-page .vditor-toolbar-item:hover {
  color: var(--color-ink);
  background-color: var(--color-paper-3);
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
