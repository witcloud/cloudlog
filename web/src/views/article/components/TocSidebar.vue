<template>
  <div class="toc-sidebar">
    <section class="toc-panel">
      <p class="toc-panel__label">目录</p>
      <div class="toc-panel__content">
        <button
          v-for="item in tocItems"
          :key="item.id"
          :class="['toc-item', { 'is-active': activeId === item.id, 'is-sub': item.level > 1 }]"
          @click="scrollTo(item.id)"
        >
          {{ item.text }}
        </button>
        <p v-if="tocItems.length === 0" class="toc-panel__empty">暂无目录</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface TocItem {
  id: string
  text: string
  level: number
}

defineProps<{
  tocItems: TocItem[]
}>()

const activeId = ref('')

const scrollTo = (id: string) => {
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth' })
    activeId.value = id
  }
}
</script>

<style scoped>
.toc-sidebar {
  position: sticky;
  top: calc(var(--header-h) + var(--space-lg));
}

.toc-panel {
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  background-color: var(--color-paper-2);
  padding: var(--space-md);
}

.toc-panel__label {
  font-size: var(--text-xs);
  letter-spacing: 0.08em;
  color: var(--color-ink-4);
  margin-bottom: var(--space-sm);
}

.toc-panel__content {
  max-height: calc(100vh - 14rem);
  overflow-y: auto;
}

.toc-item {
  display: block;
  width: 100%;
  text-align: start;
  font-size: var(--text-sm);
  line-height: 1.6;
  padding-block: var(--space-3xs);
  color: var(--color-ink-2);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  border-inline-start: 2px solid transparent;
  transition:
    color var(--dur-micro) var(--ease-out),
    border-color var(--dur-micro) var(--ease-out);
}

.toc-item:hover {
  color: var(--color-ink);
}

.toc-item.is-active {
  color: var(--color-accent);
  border-inline-start-color: var(--color-accent);
}

.toc-item.is-sub {
  padding-inline-start: var(--space-md);
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.toc-panel__empty {
  font-size: var(--text-sm);
  color: var(--color-ink-4);
}
</style>
