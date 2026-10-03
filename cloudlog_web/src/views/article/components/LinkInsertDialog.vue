<template>
  <Teleport to="body">
    <Transition name="link-dialog">
      <div
        v-if="modelValue"
        class="link-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="插入链接"
        @mousedown.self="close"
      >
        <div class="link-dialog__panel">
          <header class="link-dialog__header">
            <h2 class="link-dialog__title">插入链接</h2>
            <button class="link-dialog__close" aria-label="关闭" @click="close">
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

          <div class="link-dialog__body">
            <label class="link-dialog__label" for="link-dialog-url">链接地址</label>
            <input
              id="link-dialog-url"
              ref="urlInputEl"
              v-model="url"
              type="url"
              class="link-dialog__url"
              :class="{ 'is-invalid': !!error }"
              placeholder="https://…"
              spellcheck="false"
              autocomplete="off"
              @keydown.enter.prevent="confirm"
              @input="error = ''"
            />
            <p v-if="error" class="link-dialog__error" role="alert">{{ error }}</p>
          </div>

          <footer class="link-dialog__footer">
            <button class="btn-secondary" @click="close">取消</button>
            <button class="btn-primary" @click="confirm">确认</button>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onUnmounted } from 'vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'confirm', url: string): void }>()

const urlInputEl = ref<HTMLInputElement>()
const url = ref('')
const error = ref('')

const close = () => emit('update:modelValue', false)

/** 校验：非空 + http(s) 前缀，失败时提示并保持表单可修正 */
const confirm = () => {
  const value = url.value.trim()
  if (!value) {
    error.value = '请输入链接地址'
    urlInputEl.value?.focus()
    return
  }
  if (!/^https?:\/\//i.test(value)) {
    error.value = '链接需以 http:// 或 https:// 开头'
    urlInputEl.value?.focus()
    return
  }
  emit('confirm', value)
  close()
}

/** 打开时清空并自动聚焦，方便直接粘贴链接 */
watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      url.value = ''
      error.value = ''
      await nextTick()
      urlInputEl.value?.focus()
    }
  },
)

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && props.modelValue) close()
}
window.addEventListener('keydown', onKeydown)
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped lang="scss">
/* 弹窗结构/Token 与 ImageUploadDialog 同一语言：抬升面 + 发丝线，不引入新颜色 */

.link-dialog {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: grid;
  place-items: center;
  padding: var(--space-md);
  background-color: var(--color-overlay);
}

.link-dialog__panel {
  width: min(28rem, 100%);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-whisper);
}

.link-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg) 0;
}

.link-dialog__title {
  font-family: var(--font-display);
  font-size: var(--text-md);
  font-weight: 700;
  color: var(--color-ink);
}

.link-dialog__close {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  margin-inline-end: calc(-1 * var(--space-sm));
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

.link-dialog__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  padding: var(--space-lg) var(--space-lg) var(--space-md);
}

.link-dialog__label {
  font-size: var(--text-sm);
  color: var(--color-ink-2);
}

.link-dialog__url {
  height: 2.5rem;
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

  &.is-invalid {
    border-color: var(--color-danger);
  }
}

.link-dialog__error {
  font-size: var(--text-xs);
  color: var(--color-danger);
}

.link-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-xs);
  padding: var(--space-sm) var(--space-lg) var(--space-md);
}

/* 按钮：主色实心 / 描边次级，与图片弹窗一致 */
.btn-primary,
.btn-secondary {
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

  &:active {
    transform: translateY(1px);
  }
}

.btn-primary {
  color: var(--color-paper-2);
  background-color: var(--color-accent);
  border: 1px solid var(--color-accent);

  &:hover {
    background-color: var(--color-accent-ink);
    border-color: var(--color-accent-ink);
  }
}

.btn-secondary {
  color: var(--color-ink-2);
  background-color: transparent;
  border: 1px solid var(--color-rule-strong);

  &:hover {
    color: var(--color-ink);
    background-color: var(--color-paper-3);
  }
}

/* 过渡：轻淡入 + 微缩放 */
.link-dialog-enter-active,
.link-dialog-leave-active {
  transition: opacity var(--dur-short) var(--ease-out);

  .link-dialog__panel {
    transition: transform var(--dur-short) var(--ease-out);
  }
}

.link-dialog-enter-from,
.link-dialog-leave-to {
  opacity: 0;

  .link-dialog__panel {
    transform: scale(0.97);
  }
}
</style>
