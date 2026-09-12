<template>
  <div class="settings-panel">
    <section class="settings-section">
      <label class="settings-label">分类 <span class="settings-required">*</span></label>
      <el-select v-model="form.categoryId" placeholder="请选择分类" class="full-width">
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
      <label class="settings-label">摘要</label>
      <el-input
        v-model="form.excerpt"
        type="textarea"
        :rows="4"
        placeholder="不填写将自动从正文提取…"
        maxlength="200"
        show-word-limit
      />
      <div class="excerpt-actions">
        <button class="text-action" @click="handleAutoGenerate">自动生成</button>
      </div>
    </section>

    <section class="settings-section">
      <label class="settings-label">发布</label>
      <el-radio-group v-model="publishChoice">
        <el-radio value="published">立即发布</el-radio>
        <el-radio value="scheduled">定时发布</el-radio>
      </el-radio-group>

      <template v-if="publishChoice === 'scheduled'">
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
            popper-class="editor-datetime-popper"
            :popper-options="datetimePopperOptions"
            class="schedule-time"
          />
        </div>
        <p v-if="scheduledLabel" class="schedule-confirm">
          将在 <strong>{{ scheduledLabel }}</strong> 发布
        </p>
        <p v-else class="schedule-hint">日期可选今天起；选今天时，时间需晚于当前。</p>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import dayjs from 'dayjs'
import { getTags } from '@/api/article'
import { getCategories } from '@/api/category'
import type { Category, Tag } from '@/api/types/article'
import { useArticleStore } from '@/stores/article'
import { ElMessage } from 'element-plus'
import { formatMinioUrl } from '@/config'

const articleStore = useArticleStore()
const form = articleStore.editArticle

/** 时间选择弹出层：与屏幕边缘保持留白，防止时间滚轮展开后顶出屏幕 */
const datetimePopperOptions = {
  modifiers: [
    { name: 'offset', options: { offset: [-8, 8] } },
    { name: 'preventOverflow', options: { padding: 12, rootBoundary: 'viewport' } },
  ],
}

/** 面板只表达「发布/定时」两种意图；草稿由自动保存与 ⌘S 负责 */
const publishChoice = computed({
  get: () => (form.status === 'scheduled' ? 'scheduled' : 'published'),
  set: (val: 'published' | 'scheduled') => {
    form.status = val
  },
})

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

// 编辑既有定时文章时回显
const initScheduleFromForm = () => {
  if (!form.publishTime) return
  const [d, t] = form.publishTime.split(' ')
  scheduleDate.value = d ?? ''
  scheduleTime.value = (t ?? '').slice(0, 5)
}
onMounted(initScheduleFromForm)

const scheduledLabel = computed(() => {
  if (!scheduleDate.value || !scheduleTime.value) return ''
  return dayjs(`${scheduleDate.value} ${scheduleTime.value}`).format('YYYY年M月D日 HH:mm')
})

/** 日历只允许今天起 */
const disabledDate = (time: Date) => dayjs(time).startOf('day').isBefore(dayjs().startOf('day'))

const isScheduledToday = computed(() => scheduleDate.value === dayjs().format('YYYY-MM-DD'))

/** 选今天时，小时与分钟禁选当前时刻之前 */
const disabledHours = () =>
  isScheduledToday.value ? Array.from({ length: dayjs().hour() }, (_, i) => i) : []

const disabledMinutes = (hour: number) =>
  isScheduledToday.value && hour === dayjs().hour()
    ? Array.from({ length: dayjs().minute() }, (_, i) => i)
    : []

const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

// 封面预览地址
const coverUrl = computed(() => {
  if (articleStore.localCover) {
    return URL.createObjectURL(articleStore.localCover)
  }
  return formatMinioUrl(form.cover)
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
    if (file.size > 2 * 1024 * 1024) {
      ElMessage.warning('封面图不能超过 2MB')
      return
    }
    articleStore.setLocalCover(file)
  }
}

const handleAutoGenerate = () => {
  // 提取正文前 150 字作为摘要
  if (!form.content) {
    ElMessage.warning('请先输入文章内容')
    return
  }
  const pureText = form.content.replace(/[#*`>]/g, '').substring(0, 150)
  form.excerpt = pureText + '…'
  ElMessage.success('摘要已自动生成')
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

.schedule-confirm {
  margin-top: var(--space-sm);
  font-size: var(--text-sm);
  color: var(--color-ink-2);

  strong {
    font-weight: 600;
    color: var(--color-accent);
  }
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

.excerpt-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-2xs);
}

.text-action {
  font-size: var(--text-xs);
  color: var(--color-accent);

  &:hover {
    text-decoration: underline;
    text-underline-offset: 0.25em;
  }
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
