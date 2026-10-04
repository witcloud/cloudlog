<template>
  <Teleport to="body">
    <Transition name="img-dialog">
      <div
        v-if="modelValue"
        class="img-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="插入图片"
        @mousedown.self="close"
      >
        <div class="img-dialog__panel">
          <header class="img-dialog__header">
            <nav class="img-dialog__tabs" role="tablist">
              <button
                role="tab"
                :aria-selected="tab === 'upload'"
                class="img-dialog__tab"
                :class="{ 'is-active': tab === 'upload' }"
                @click="tab = 'upload'"
              >
                上传图片
              </button>
              <button
                role="tab"
                :aria-selected="tab === 'link'"
                class="img-dialog__tab"
                :class="{ 'is-active': tab === 'link' }"
                @click="tab = 'link'"
              >
                链接添加
              </button>
            </nav>
            <button class="img-dialog__close" aria-label="关闭" @click="close">
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

          <!-- 上传图片 -->
          <div
            v-show="tab === 'upload'"
            class="img-dialog__body"
            :class="{ 'is-dragover': dragover }"
            @dragover.prevent="dragover = true"
            @dragleave.prevent="dragover = false"
            @drop.prevent="onDrop"
          >
            <input
              ref="fileInputEl"
              type="file"
              accept="image/*"
              class="img-dialog__input"
              @change="onFileChange"
            />

            <!-- 默认状态 -->
            <div v-if="stage === 'idle'" class="img-dialog__center">
              <button class="btn-primary" @click="fileInputEl?.click()">选择图片</button>
              <p class="img-dialog__hint">支持 jpg、jpeg、png 等多种格式，单张图片最大支持 5MB</p>
            </div>

            <!-- 上传中 -->
            <div v-else-if="stage === 'uploading'" class="img-dialog__center">
              <span class="img-dialog__spinner" aria-hidden="true"></span>
              <p class="img-dialog__hint">正在上传「{{ fileName }}」…</p>
            </div>

            <!-- 上传成功 -->
            <div v-else-if="stage === 'success'" class="img-dialog__center img-dialog__result">
              <img :src="previewUrl" class="img-dialog__preview" alt="图片预览" />
              <p class="img-dialog__filename">{{ fileName }} · {{ fileSizeText }}</p>
              <div class="img-dialog__actions">
                <button class="btn-secondary" @click="fileInputEl?.click()">替换</button>
                <button class="btn-ghost-danger" @click="resetUpload">删除</button>
                <button class="btn-primary" @click="confirmUpload">确认插入</button>
              </div>
            </div>

            <!-- 上传失败 -->
            <div v-else class="img-dialog__center">
              <p class="img-dialog__error">{{ errorText }}</p>
              <div class="img-dialog__actions">
                <button class="btn-primary" @click="retry">重试</button>
                <button class="btn-secondary" @click="resetUpload">重新选择</button>
              </div>
            </div>
          </div>

          <!-- 链接添加 -->
          <div v-show="tab === 'link'" class="img-dialog__body">
            <div class="img-dialog__center img-dialog__link">
              <div class="img-dialog__link-form">
                <input
                  v-model="linkUrl"
                  type="url"
                  class="img-dialog__url"
                  placeholder="粘贴图片链接，如 https://…"
                  spellcheck="false"
                />
                <button class="btn-primary" :disabled="!linkReady" @click="confirmLink">
                  确认插入
                </button>
              </div>
              <p v-if="linkError" class="img-dialog__error">{{ linkError }}</p>
              <p v-else-if="linkUrl" class="img-dialog__hint">
                {{ linkReady ? '图片可正常加载，点击确认插入' : '输入链接后自动预览' }}
              </p>
              <!-- 预览图需先渲染才能触发 load/error 探测，失败后仅隐藏不销毁 -->
              <img
                v-if="linkAttempt"
                v-show="!linkError"
                :src="linkUrl"
                class="img-dialog__preview"
                alt="链接图片预览"
                @load="onLinkLoad"
                @error="onLinkError"
              />
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { uploadArticleImage } from '@/api/article'
import { UPLOAD_LIMITS, ACCEPTED_IMAGE_TYPES, ACCEPTED_IMAGE_HINT } from '@/config/upload'
import { formatMinioUrl } from '@/config'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'confirm', markdown: string): void }>()

// 与封面/头像共用同一份上限口径（config/upload.ts）
const MAX_SIZE = UPLOAD_LIMITS.articleImage

const tab = ref<'upload' | 'link'>('upload')
const stage = ref<'idle' | 'uploading' | 'success' | 'error'>('idle')
const dragover = ref(false)
const fileInputEl = ref<HTMLInputElement>()
const fileName = ref('')
const fileSizeText = ref('')
const previewUrl = ref('')
const uploadedUrl = ref('')
const errorText = ref('')
let lastFile: File | null = null

const linkUrl = ref('')
const linkState = ref<'idle' | 'loading' | 'ok' | 'fail'>('idle')
const linkAttempt = computed(() => /^https?:\/\//i.test(linkUrl.value.trim()))
const linkReady = computed(() => linkState.value === 'ok')
const linkError = computed(() => {
  if (!linkUrl.value) return ''
  if (!linkAttempt.value) return '请输入以 http(s):// 开头的图片链接'
  if (linkState.value === 'fail') return '无法加载该图片，请检查链接是否有效'
  return ''
})

const close = () => {
  voidUpload()
  emit('update:modelValue', false)
}

/**
 * 在途上传的作废通道。
 * 关弹窗不撤请求的话：对象照样落桶、日额度照样扣，而屏幕上已经看不见这一枪；
 * 更糟的是重开弹窗后回来的响应把 stage 改回 success，点确认插进去的是上一张图。
 * 所以既 abort 真请求，也用序号挡住任何迟到的回写。
 */
let uploadCtrl: AbortController | null = null
let uploadSeq = 0

const voidUpload = () => {
  uploadSeq += 1
  uploadCtrl?.abort()
  uploadCtrl = null
}

const resetUpload = () => {
  voidUpload()
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
  stage.value = 'idle'
  uploadedUrl.value = ''
  lastFile = null
  errorText.value = ''
  if (fileInputEl.value) fileInputEl.value.value = ''
}

const fileSizeOf = (file: File) => {
  fileSizeText.value =
    file.size >= 1024 * 1024
      ? `${(file.size / 1024 / 1024).toFixed(1)} MB`
      : `${Math.max(1, Math.round(file.size / 1024))} KB`
}

/** 校验 + 上传，复用文章图片上传接口 */
const uploadFile = async (file: File) => {
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
    stage.value = 'error'
    errorText.value = `不支持的图片格式，请选择 ${ACCEPTED_IMAGE_HINT} 图片`
    return
  }
  if (file.size > MAX_SIZE) {
    stage.value = 'error'
    errorText.value = '图片超过 5MB 限制，请压缩后重试'
    return
  }

  lastFile = file
  fileName.value = file.name
  fileSizeOf(file)
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = URL.createObjectURL(file)
  stage.value = 'uploading'

  // 换文件重传时先撤掉上一发，并领一个本轮序号
  voidUpload()
  const ctrl = new AbortController()
  uploadCtrl = ctrl
  const seq = uploadSeq

  try {
    const res = await uploadArticleImage(file, ctrl.signal)
    if (seq !== uploadSeq) return
    if (res.code === 200) {
      uploadedUrl.value = formatMinioUrl(res.data)
      stage.value = 'success'
    } else {
      stage.value = 'error'
      errorText.value = res.message || '上传失败，请重试'
    }
  } catch {
    // 已被作废（关弹窗/换了文件）：这一发的结果不作数，也不留错误态
    if (seq !== uploadSeq) return
    stage.value = 'error'
    errorText.value = '上传失败，请重试'
  } finally {
    if (uploadCtrl === ctrl) uploadCtrl = null
  }
}

const retry = () => {
  if (lastFile) uploadFile(lastFile)
  else resetUpload()
}

const onFileChange = (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) uploadFile(file)
}

const onDrop = (event: DragEvent) => {
  dragover.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) uploadFile(file)
}

const confirmUpload = () => {
  if (!uploadedUrl.value) return
  emit('confirm', `![](${uploadedUrl.value})`)
  close()
}

const onLinkLoad = () => {
  linkState.value = 'ok'
}
const onLinkError = () => {
  linkState.value = 'fail'
}

watch(linkUrl, (val) => {
  const url = val.trim()
  linkState.value = /^https?:\/\//i.test(url) ? 'loading' : 'idle'
})

const confirmLink = () => {
  if (!linkReady.value) return
  emit('confirm', `![](${linkUrl.value.trim()})`)
  close()
}

/** 打开时归零，避免残留上一次的上传状态 */
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      tab.value = 'upload'
      resetUpload()
      linkUrl.value = ''
      linkState.value = 'idle'
    }
  },
)

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && props.modelValue) close()
}
window.addEventListener('keydown', onKeydown)
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<style scoped lang="scss">
/* 弹窗：抬升面 + 发丝线分层，不引入新颜色/阴影，全部走 variables.css Token */

.img-dialog {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: grid;
  place-items: center;
  padding: var(--space-md);
  background-color: var(--color-overlay);
}

.img-dialog__panel {
  width: min(32rem, 100%);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-whisper);
  overflow: hidden;
}

/* ---- 头部：Tab + 关闭 ---- */
.img-dialog__header {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  padding-inline: var(--space-md) var(--space-xs);
  border-bottom: 1px solid var(--color-rule);
}

.img-dialog__tabs {
  display: flex;
  gap: var(--space-md);
}

.img-dialog__tab {
  position: relative;
  padding: var(--space-sm) var(--space-2xs);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  color: var(--color-ink-3);
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-ink);
  }

  /* 选中态：强调色文字 + 底部短线 */
  &.is-active {
    color: var(--color-accent);

    &::after {
      content: '';
      position: absolute;
      inset-inline: var(--space-2xs);
      bottom: -1px;
      height: 2px;
      border-radius: var(--radius-xs);
      background-color: var(--color-accent);
    }
  }
}

.img-dialog__close {
  align-self: center;
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--color-ink-4);
  cursor: pointer;
  transition:
    color var(--dur-micro) var(--ease-out),
    background-color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-ink);
    background-color: var(--color-paper-3);
  }
}

/* ---- 主体：大面积留白，内容居中 ---- */
.img-dialog__body {
  display: grid;
  place-items: center;
  min-height: 17rem;
  padding: var(--space-lg) var(--space-xl);

  &.is-dragover {
    outline: 1px dashed var(--color-rule-strong);
    outline-offset: calc(-1 * var(--space-xs));
  }
}

.img-dialog__center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
  text-align: center;
  max-width: 100%;
}

.img-dialog__hint {
  font-size: var(--text-xs);
  color: var(--color-ink-4);
}

.img-dialog__error {
  font-size: var(--text-sm);
  color: var(--color-danger);
}

.img-dialog__input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

/* ---- 成功态预览 ---- */
.img-dialog__result {
  gap: var(--space-sm);
}

.img-dialog__preview {
  max-width: 100%;
  max-height: 14rem;
  object-fit: contain;
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  background-color: var(--color-paper);
}

.img-dialog__filename {
  font-size: var(--text-xs);
  color: var(--color-ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.img-dialog__actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

/* ---- 上传中 ---- */
.img-dialog__spinner {
  width: 1.5rem;
  height: 1.5rem;
  border: 2px solid var(--color-rule);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: img-dialog-spin 0.8s linear infinite;
}

@keyframes img-dialog-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ---- 链接添加 ---- */
.img-dialog__link {
  width: 100%;
}

.img-dialog__link-form {
  display: flex;
  align-items: stretch;
  gap: var(--space-xs);
  width: min(24rem, 100%);
}

.img-dialog__url {
  flex: 1;
  min-width: 0;
  height: 2.25rem;
  padding-inline: var(--space-sm);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  color: var(--color-ink);
  background-color: var(--color-paper);
  border: 1px solid var(--color-rule-strong);
  border-radius: var(--radius-sm);
  outline: none;
  transition: border-color var(--dur-micro) var(--ease-out);

  &::placeholder {
    color: var(--color-ink-4);
  }

  &:focus-visible {
    border-color: var(--color-focus);
  }
}

/* ---- 按钮：主色实心 / 描边次级 / 弱化危险 ---- */
.btn-primary,
.btn-secondary,
.btn-ghost-danger {
  height: 2.25rem;
  padding-inline: var(--space-md);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  border-radius: var(--radius-sm);
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color var(--dur-micro) var(--ease-out),
    color var(--dur-micro) var(--ease-out),
    border-color var(--dur-micro) var(--ease-out),
    transform 100ms var(--ease-out);

  &:active:not(:disabled) {
    transform: translateY(1px);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.btn-primary {
  color: var(--color-paper-2);
  background-color: var(--color-accent);
  border: 1px solid var(--color-accent);

  &:hover:not(:disabled) {
    background-color: var(--color-accent-ink);
    border-color: var(--color-accent-ink);
  }
}

.btn-secondary {
  color: var(--color-ink-2);
  background-color: transparent;
  border: 1px solid var(--color-rule-strong);

  &:hover:not(:disabled) {
    color: var(--color-ink);
    background-color: var(--color-paper-3);
  }
}

.btn-ghost-danger {
  color: var(--color-danger);
  background-color: transparent;
  border: 1px solid transparent;

  &:hover:not(:disabled) {
    background-color: var(--color-accent-wash);
  }
}

/* ---- 过渡：轻淡入 + 微缩放 ---- */
.img-dialog-enter-active,
.img-dialog-leave-active {
  transition: opacity var(--dur-short) var(--ease-out);

  .img-dialog__panel {
    transition: transform var(--dur-short) var(--ease-out);
  }
}

.img-dialog-enter-from,
.img-dialog-leave-to {
  opacity: 0;

  .img-dialog__panel {
    transform: scale(0.97);
  }
}
</style>
