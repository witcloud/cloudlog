<template>
  <div class="toc-sidebar" :class="{ 'is-embedded': embedded }">
    <section class="toc-panel" :class="{ 'is-embedded': embedded }">
      <p v-if="!embedded" class="toc-panel__label">目录</p>
      <div ref="listRef" class="toc-panel__content">
        <button
          v-for="item in tocItems"
          :key="item.key"
          :class="['toc-item', { 'is-active': activeIndex === item.index, 'is-sub': item.level > minLevel }]"
          @click="emit('navigate', item.index)"
        >
          {{ item.text }}
        </button>
        <p v-if="tocItems.length === 0" class="toc-panel__empty">暂无目录</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface TocItem {
  key: string
  id: string
  text: string
  level: number
  index: number
}

const props = defineProps<{
  tocItems: TocItem[]
  activeIndex: number | null
  /** 嵌入宿主面板（编辑器侧栏 tab）时为 true：去 sticky 与外框，边框由宿主提供。
      默认 false，详情页零影响 */
  embedded?: boolean
}>()

const emit = defineEmits<{ (e: 'navigate', index: number): void }>()

// 顶级条目落在哪一级数字不固定（作者从 `#` 还是 `##` 起写、DOM 与推导两条来源给的绝对级别也不同），
// 所以缩进按相对最浅一级的深度判，两条来源就自动一致
const minLevel = computed(() => Math.min(...props.tocItems.map((i) => i.level), 6))

const listRef = ref<HTMLElement>()

// 条目多于面板高度时把高亮项保持在列表可视范围内。手动改 scrollTop 而非 scrollIntoView：
// 后者会连带滚动祖先，吸顶面板虽在视口内、页面仍可能被推走
watch(
  () => props.activeIndex,
  () => {
    const list = listRef.value
    const active = list?.children[props.activeIndex ?? -1] as HTMLElement | undefined
    if (!list || !active) return
    const listBox = list.getBoundingClientRect()
    const activeBox = active.getBoundingClientRect()
    if (activeBox.top < listBox.top) list.scrollTop -= listBox.top - activeBox.top
    else if (activeBox.bottom > listBox.bottom) list.scrollTop += activeBox.bottom - listBox.bottom
  },
)
</script>

<style scoped>
/* 吸顶：top 随 Header 高度 Token，超出视口部分由目录内部滚动 */
.toc-sidebar {
  position: sticky;
  top: calc(var(--header-h) + var(--space-lg));
  max-height: calc(100vh - var(--header-h) - var(--space-xl));
  display: flex;
  flex-direction: column;
}

.toc-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  background-color: var(--color-paper-2);
  padding: var(--space-md);
}

.toc-panel__label {
  flex-shrink: 0;
  padding-bottom: var(--space-sm);
  margin-bottom: var(--space-sm);
  border-bottom: 1px solid var(--color-rule);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-ink);
}

.toc-panel__content {
  flex: 1;
  min-height: 0;
  /* 抵消面板内边距，使内容区延伸到面板边框，指示条可贴住边框且不被滚动容器裁剪 */
  margin-inline: calc(-1 * var(--space-md));
  overflow-y: auto;
}

.toc-item {
  display: block;
  width: 100%;
  text-align: start;
  font-size: var(--text-sm);
  line-height: 1.6;
  padding-block: var(--space-xs);
  color: var(--color-ink-2);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  /* 文字与贴边的指示条之间保持间距 */
  padding-inline-start: var(--space-md);
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
  padding-inline-start: calc(var(--space-md) + var(--space-md));
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.toc-panel__empty {
  /* 内容区被负外边距拉到面板边框，空状态需与标题缩进对齐 */
  padding-inline-start: var(--space-md);
  font-size: var(--text-sm);
  color: var(--color-ink-4);
}

/* ---- 嵌入态（编辑器侧栏）---- *
 * sticky 与卡片外框都不做：宿主面板提供定位、边框与纵向约束，列表撑满剩余高度内部滚动，
 * 高亮项保持可见的机制照常生效 */
.toc-sidebar.is-embedded {
  position: static;
  max-height: none;
  flex: 1;
  min-height: 0;
}

.toc-panel.is-embedded {
  flex: 1;
  min-height: 0;
  padding: 0;
  border: none;
  border-radius: 0;
  background-color: transparent;
}

/* 宿主面板无内衬，内容区不再负外边距外扩，指示条直接贴住面板左缘的边框线 */
.toc-panel.is-embedded .toc-panel__content {
  margin-inline: 0;
}
</style>
