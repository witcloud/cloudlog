<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

// 右下角回到顶部：滚过一屏后出现，尊重系统减弱动效设置。
const visible = ref(false)

const onScroll = () => {
  visible.value = window.scrollY > 420
}

const scrollToTop = () => {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: 0, behavior: reduced ? 'auto' : 'smooth' })
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <Transition name="backtop">
    <button v-show="visible" class="back-to-top" aria-label="回到顶部" @click="scrollToTop">
      <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
        <path
          d="M8 13V3M3.5 7.5 8 3l4.5 4.5"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>
  </Transition>
</template>

<style scoped>
.back-to-top {
  position: fixed;
  right: var(--space-lg);
  bottom: var(--space-xl);
  z-index: var(--z-raised);
  display: grid;
  place-items: center;
  width: 2.5rem;
  height: 2.5rem;
  border: 1px solid var(--color-rule-strong);
  border-radius: var(--radius-sm);
  background-color: var(--color-paper-2);
  color: var(--color-ink-2);
  box-shadow: var(--shadow-whisper);
  transition:
    color var(--dur-micro) var(--ease-out),
    border-color var(--dur-micro) var(--ease-out);
}

.back-to-top:hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
}

.back-to-top:active {
  transform: translateY(1px);
}

.backtop-enter-active,
.backtop-leave-active {
  transition: opacity var(--dur-short) var(--ease-out);
}

.backtop-enter-from,
.backtop-leave-to {
  opacity: 0;
}
</style>
