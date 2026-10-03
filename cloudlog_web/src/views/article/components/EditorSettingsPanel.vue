<template>
  <div class="settings-panel">
    <section class="settings-section">
      <label class="settings-label">分类 <span class="settings-required">*</span></label>
      <el-select
        v-model="form.categoryId"
        placeholder="请选择分类"
        :fallback-placements="selectFallbackPlacements"
        :popper-options="selectPopperOptions"
        class="full-width"
      >
        <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
    </section>

    <section class="settings-section">
      <label class="settings-label">标签</label>
      <el-select
        v-model="form.tags"
        multiple
        filterable
        allow-create
        default-first-option
        placeholder="请选择或输入标签"
        :fallback-placements="selectFallbackPlacements"
        :popper-options="selectPopperOptions"
        class="full-width"
      >
        <el-option v-for="item in tags" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
    </section>

    <section class="settings-section">
      <label class="settings-label">封面</label>
      <div class="cover-uploader" @click="triggerUpload">
        <template v-if="coverUrl">
          <img :src="coverUrl" class="cover-image" alt="文章封面" />
          <div class="cover-mask">
            <svg viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true">
              <path
                d="m11.5 2.5 2 2L6 12l-3 1 1-3 7.5-7.5Z"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linejoin="round"
              />
            </svg>
            <span>更换封面</span>
          </div>
        </template>
        <div v-else class="uploader-placeholder">
          <svg viewBox="0 0 16 16" width="18" height="18" fill="none" aria-hidden="true">
            <path
              d="M8 3v10M3 8h10"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
            />
          </svg>
          <span>上传封面</span>
        </div>
        <input
          ref="fileInput"
          type="file"
          accept="image/*"
          class="hidden-input"
          @change="handleFileChange"
        />
      </div>
      <p class="settings-hint">建议尺寸 800×450，支持 jpg / png / webp</p>
    </section>

    <section class="settings-section">
      <label class="settings-label">发布</label>

      <!-- 一行陈述「点主按钮会发生什么」+ 一个切换到另一端的动作；已上线/已下线只陈述状态 -->
      <div class="publish-row">
        <span class="publish-state">{{ publishStateLabel }}</span>
        <button v-if="!isSettled" class="text-action" type="button" @click="toggleSchedule">
          {{ isScheduledIntent ? '立即' : '定时' }}
        </button>
      </div>

      <template v-if="isScheduledIntent">
        <div class="schedule-row">
          <el-date-picker
            v-model="scheduleDate"
            type="date"
            placeholder="选择日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledDate"
            placement="bottom-end"
            popper-class="editor-datetime-popper"
            :popper-options="datetimePopperOptions"
            class="schedule-date"
          />
          <el-time-picker
            v-model="scheduleTime"
            placeholder="选择时间"
            format="HH:mm"
            value-format="HH:mm"
            :disabled-hours="disabledHours"
            :disabled-minutes="disabledMinutes"
            placement="bottom-end"
            :fallback-placements="scheduleTimeFallbackPlacements"
            popper-class="editor-datetime-popper"
            :popper-options="scheduleTimePopperOptions"
            class="schedule-time"
          />
        </div>
        <p v-if="!scheduledLabel" class="schedule-hint">
          日期可选今天起；选今天时，时间需晚于当前。
        </p>
      </template>

      <!-- 状态迁移动作紧跟状态行，与 Ghost/WordPress 的 Publication 卡片同位 -->
      <div v-if="statusAction" class="status-action-row">
        <button
          class="text-action"
          type="button"
          :disabled="submittingStatus"
          @click="handleStatusAction"
        >
          {{ submittingStatus ? '处理中…' : statusAction.label }}
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import dayjs from 'dayjs'
import { getTags, updateArticle } from '@/api/article'
import { getCategories } from '@/api/category'
import type { Category, Tag } from '@/api/types/article'
import type { ArticleStatus } from '@/api/types/admin'
import { useArticleStore } from '@/stores/article'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatMinioUrl } from '@/config'
import { resolveTagIds } from '@/utils/articleTags'
import { beijingNow } from '@/utils/time'
import { UPLOAD_LIMITS, isImageFile } from '@/config/upload'

const articleStore = useArticleStore()
const form = articleStore.editArticle

/** EP 默认给 flip 四个候选，而 popper 每次 update 都会重跑 flip（_skip 随 forceUpdate 复位），
   设置栏滚动时选择框上下移，浮层就在 bottom/top 之间来回翻面。只留一个候选即钉死朝下。 */
const selectFallbackPlacements = ['bottom-start']

/** 设置栏钉在视口上，absolute 的浮层却按文档坐标定位，页面一滚它先跟着文档走、
   等 popper 回算才弹回来贴上（看着就是漂一下复位）。fixed 直接锚视口，没有这个瞬时态——与顶栏「创作」下拉同一处理；
   下面两个日期/时间弹层各自带了 modifiers，所以同一条 strategy 写在那里 */
const selectPopperOptions = { strategy: 'fixed' }

/** 时间选择弹出层：与屏幕边缘保持留白，防止时间滚轮展开后顶出屏幕 */
const datetimePopperOptions = {
  strategy: 'fixed',
  modifiers: [
    { name: 'offset', options: { offset: [-8, 8] } },
    { name: 'preventOverflow', options: { padding: 12, rootBoundary: 'viewport' } },
  ],
}

/** 定时行：时间弹层右缘贴本行右缘（行右缘即「选择时间」框右缘） */
const scheduleTimePopperOptions = {
  strategy: 'fixed',
  modifiers: [
    { name: 'offset', options: { offset: [0, 8] } },
    { name: 'preventOverflow', options: { padding: 12, rootBoundary: 'viewport' } },
    // popper 的 adaptive 会把 *-end 落成 right 定位，量纲取 documentElement.scrollWidth
    // （比可视宽度多出滚动条那 15px），右对齐会短一截；改回 left 定位才贴得准
    { name: 'computeStyles', options: { adaptive: false } },
  ],
}

/** EP 默认只回落到不带对齐变体的 top/bottom，弹层会改成按输入框居中摆放、压到面板右缘的滚动条上 */
const scheduleTimeFallbackPlacements = ['bottom-end', 'top-end']

/** 已上线的文章：本区块只陈述状态，不再提供「立即/定时」二选一 */
const isLive = computed(() => !!form.id && articleStore.dbStatus === 'published')

/** 已下线的文章：读者访问详情一律 404，只有作者本人与管理员看得到 */
const isOffline = computed(() => !!form.id && articleStore.dbStatus === 'offline')

/** 状态已落定（在线或已下线）：发布方式二选一无意义，迁移交给下方的动作按钮 */
const isSettled = computed(() => isLive.value || isOffline.value)

const publishedAtLabel = computed(() =>
  articleStore.publishedAt ? dayjs(articleStore.publishedAt).format('YYYY年M月D日 HH:mm') : '',
)

/** 本次提交是否排期：由「定时 / 立即」这对动作改写，主按钮文案跟着它走 */
const isScheduledIntent = computed(() => form.status === 'scheduled')

const toggleSchedule = () => {
  if (!isScheduledIntent.value) {
    form.status = 'scheduled'
    return
  }
  // 取消排期：库里已定时的要显式转成即时上线，草稿回到草稿（上线动作仍由主按钮负责）
  form.status = articleStore.dbStatus === 'scheduled' ? 'published' : 'draft'
}

/**
 * 定时发布：日期与时间分开选择，内部仍拼回
 * form.publishTime 的 'YYYY-MM-DD HH:mm:ss' 契约（提交逻辑不变）
 */
const scheduleDate = ref('')
const scheduleTime = ref('')

const syncScheduleToForm = () => {
  form.publishTime =
    scheduleDate.value && scheduleTime.value
      ? `${scheduleDate.value} ${scheduleTime.value}:00`
      : null
}

watch([scheduleDate, scheduleTime], syncScheduleToForm)

/**
 * 定时文章回显：详情接口不带 publishTime，计划时刻存于 publishedAt（db-design.md §5.3）。
 * 详情比面板挂载晚到，且不清空用户已选的值，故按状态源补写空选择器。
 */
watch(
  () => [articleStore.dbStatus, articleStore.publishedAt, form.publishTime],
  () => {
    if (articleStore.dbStatus !== 'scheduled') return
    if (scheduleDate.value && scheduleTime.value) return
    const planned = form.publishTime || articleStore.publishedAt
    if (!planned) return
    const [d, t] = planned.replace('T', ' ').split(' ')
    scheduleDate.value = d ?? ''
    scheduleTime.value = (t ?? '').slice(0, 5)
  },
  { immediate: true },
)

const scheduledLabel = computed(() => {
  if (!scheduleDate.value || !scheduleTime.value) return ''
  return dayjs(`${scheduleDate.value} ${scheduleTime.value}`).format('YYYY年M月D日 HH:mm')
})

/** 状态行：一句话说明「点右上角主按钮会发生什么」，取代原来的「立即/定时」二选一 */
const publishStateLabel = computed(() => {
  if (isLive.value) return publishedAtLabel.value ? `已发布 · ${publishedAtLabel.value}` : '已发布'
  if (isOffline.value) return '已下线'
  if (!isScheduledIntent.value) return '立即发布'
  return scheduledLabel.value ? `将在 ${scheduledLabel.value} 发布` : '定时发布'
})

/** 日历只允许今天起。「今天」「当前时刻」都取北京墙钟：计划时刻按北京墙钟输入与存储，
    浏览器不在东八区时用本地 now 会把可选窗口错开数小时 */
const disabledDate = (time: Date) =>
  dayjs(time).startOf('day').isBefore(beijingNow().startOf('day'))

const isScheduledToday = computed(() => scheduleDate.value === beijingNow().format('YYYY-MM-DD'))

/** 选今天时，小时与分钟禁选当前时刻之前 */
const disabledHours = () =>
  isScheduledToday.value ? Array.from({ length: beijingNow().hour() }, (_, i) => i) : []

const disabledMinutes = (hour: number) =>
  isScheduledToday.value && hour === beijingNow().hour()
    ? Array.from({ length: beijingNow().minute() }, (_, i) => i)
    : []

const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

// 封面预览地址。localCover 的预览走 ObjectURL：必须在换图与卸载时 revoke，
// computed 里每次重算都 createObjectURL 从不释放，是纯粹的内存泄漏
const coverUrl = ref('')
let coverObjectUrl: string | null = null

watch(
  () => articleStore.localCover,
  (file) => {
    if (coverObjectUrl) {
      URL.revokeObjectURL(coverObjectUrl)
      coverObjectUrl = null
    }
    if (file) {
      coverObjectUrl = URL.createObjectURL(file)
      coverUrl.value = coverObjectUrl
    } else {
      coverUrl.value = formatMinioUrl(form.cover)
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  if (coverObjectUrl) {
    URL.revokeObjectURL(coverObjectUrl)
    coverObjectUrl = null
  }
})

onMounted(async () => {
  try {
    const [catRes, tagRes] = await Promise.all([getCategories(), getTags()])
    categories.value = catRes.data
    tags.value = tagRes.data
  } catch (error) {
    console.error('Failed to fetch categories/tags:', error)
  }
})

const triggerUpload = () => {
  fileInput.value?.click()
}

const handleFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    // accept 只是选择器提示，拖拽/改名文件可绕过，MIME 在这里显式拦
    if (!isImageFile(file)) {
      ElMessage.warning('封面仅支持图片文件 (jpg / png / webp)')
      return
    }
    if (file.size > UPLOAD_LIMITS.cover) {
      ElMessage.warning('封面图不能超过 5MB')
      return
    }
    articleStore.setLocalCover(file)
  }
  // 同一张图选两次不触发 change，清掉让 input 复位
  target.value = ''
}

/**
 * 状态迁移动作：库中状态决定这一个按钮是什么。
 * 草稿没有可迁移的东西（上线由主按钮负责），故返回 null 让整行不渲染。
 */
type StatusAction = {
  label: string
  next: ArticleStatus
  confirmTitle: string
  confirm: string
  done: string
}

const statusAction = computed<StatusAction | null>(() => {
  if (!form.id) return null
  switch (articleStore.dbStatus) {
    case 'published':
      return {
        label: '下线',
        next: 'offline',
        confirmTitle: '下线文章',
        confirm: '下线后读者将无法访问此文章，可随时重新上线',
        done: '文章已下线',
      }
    case 'offline':
      return {
        label: '上线',
        next: 'published',
        confirmTitle: '重新上线',
        confirm: '上线后此文章将立即对所有读者可见',
        done: '文章已重新上线',
      }
    case 'scheduled':
      return {
        label: '取消定时',
        next: 'draft',
        confirmTitle: '取消定时发布',
        confirm: '取消后计划时刻将被清空，文章回到草稿箱',
        done: '已取消定时，文章回到草稿箱',
      }
    default:
      return null
  }
})

const submittingStatus = ref(false)

const handleStatusAction = async () => {
  const action = statusAction.value
  if (!form.id || !action || submittingStatus.value) return
  try {
    await ElMessageBox.confirm(action.confirm, action.confirmTitle, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return // 取消
  }

  submittingStatus.value = true
  try {
    // 字段组装与编辑器提交一致，只把 status 换成本次迁移的目标态。
    // categoryId 不兜底：无分类是草稿的合法态，硬塞 id=1 是在静默污染数据；
    // 转 published 且无分类时后端会以 400 提示补分类，那才是正确反馈。
    // allow-create 输入的新标签名同样先解析成 id，不能在迁移时丢掉
    const res = await updateArticle(form.id, {
      id: form.id,
      title: form.title,
      content: form.content,
      cover: form.cover,
      categoryId: form.categoryId ?? null,
      tagIds: await resolveTagIds(form.tags),
      status: action.next,
      publishTime: null,
    })
    if (res.code === 200) {
      form.status = action.next
      articleStore.dbStatus = action.next
      // 下线保留 published_at（重新上线沿用首次对外可见的时刻）；
      // 取消定时后端会清空它，本地快照同步清掉，免得再次发布后顶栏挂着旧计划时刻
      if (action.next === 'draft') articleStore.publishedAt = null
      // 待上传的本地封面不随本请求落库，保留 dirty 让自动保存继续处理它
      if (!articleStore.localCover) articleStore.isDirty = false
      ElMessage.success(action.done)
    }
  } catch (error) {
    // 失败原因由响应拦截器统一提示，此处不重复出声
    console.error('状态迁移失败:', error)
  } finally {
    submittingStatus.value = false
  }
}
</script>

<style scoped lang="scss">
.settings-panel {
  display: flex;
  flex-direction: column;
  padding: var(--space-md);
}

.settings-section {
  padding-block: var(--space-md);

  & + .settings-section {
    border-top: 1px solid var(--color-rule);
  }
}

.settings-label {
  display: block;
  margin-bottom: var(--space-sm);
  font-size: var(--text-xs);
  letter-spacing: 0.08em;
  color: var(--color-ink-4);
}

.settings-required {
  color: var(--color-accent);
}

.settings-hint {
  margin-top: var(--space-2xs);
  font-size: var(--text-xs);
  color: var(--color-ink-4);
}

.full-width {
  width: 100%;
}

/* 定时发布：日期 + 时间紧凑成行 */
.schedule-row {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-sm);

  .schedule-date {
    flex: 1.35;
    min-width: 0;
  }

  .schedule-time {
    flex: 1;
    min-width: 0;
  }
}

/* 状态行 + 右侧动作：立即发布 [定时] / 将在 X 发布 [立即] / 已发布。
   时刻文案较长，窄屏与底部抽屉里允许换行而不是挤压 */
.publish-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
}

.publish-state {
  font-size: var(--text-sm);
  color: var(--color-ink-2);
}

.schedule-hint {
  margin-top: var(--space-sm);
  font-size: var(--text-xs);
  color: var(--color-ink-4);
}

.cover-uploader {
  width: 100%;
  max-width: 240px;
  aspect-ratio: 16 / 9;
  border: 1px dashed var(--color-rule-strong);
  border-radius: var(--radius-sm);
  cursor: pointer;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-color: var(--color-paper-3);
  transition: border-color var(--dur-micro) var(--ease-out);

  &:hover {
    border-color: var(--color-accent);

    .cover-mask {
      opacity: 1;
    }
  }

  .cover-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .cover-mask {
    position: absolute;
    inset: 0;
    background: var(--color-overlay);
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    gap: var(--space-2xs);
    color: var(--color-paper);
    font-size: var(--text-xs);
    opacity: 0;
    transition: opacity var(--dur-short) var(--ease-out);
  }

  .uploader-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--space-2xs);
    color: var(--color-ink-3);
    font-size: var(--text-xs);
  }
}

.hidden-input {
  display: none;
}

.text-action {
  font-size: var(--text-xs);
  color: var(--color-accent);

  &:hover {
    text-decoration: underline;
    text-underline-offset: 0.25em;
  }

  &:disabled {
    color: var(--color-ink-4);
    cursor: not-allowed;
  }
}

/* 状态迁移动作（下线 / 上线 / 取消定时）：文字按钮，独立成行挂在「发布」区块末尾 */
.status-action-row {
  margin-top: var(--space-sm);
}
</style>

<style lang="scss">
/* 时间选择弹出层（teleport 到 body，须用全局样式）：
   右对齐输入框向左展开，任何情况下不超出屏幕 */
.editor-datetime-popper {
  max-width: calc(100vw - 16px);
}

/* 时间滚轮子面板：EP 默认 right:-26px 向右伸出包裹，贴边时会顶出屏幕。
   改为向左锚定在「选择时间」输入下方，始终落在弹层内部。 */
.editor-datetime-popper .el-time-panel {
  left: auto;
  right: 0;
}
</style>
