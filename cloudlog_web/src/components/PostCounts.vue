<script setup lang="ts">
defineProps<{
  views: number
  comments: number
  likes: number
}>()
</script>

<template>
  <span class="post-counts tnum">
    <span class="post-count">
      <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
        <path
          d="M1.8 8C3.4 5.3 5.5 4 8 4s4.6 1.3 6.2 4C12.6 10.7 10.5 12 8 12S3.4 10.7 1.8 8Z"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linejoin="round"
        />
        <circle cx="8" cy="8" r="1.75" stroke="currentColor" stroke-width="1.5" />
      </svg>
      <span class="post-count__label">阅读</span>
      {{ views }}
    </span>
    <span class="post-count">
      <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
        <path
          d="M4 3h8a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H8l-3.5 3v-3H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linejoin="round"
        />
      </svg>
      <span class="post-count__label">评论</span>
      {{ comments }}
    </span>
    <span class="post-count">
      <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
        <path
          d="M8 13.25 2.9 8.15a3.1 3.1 0 0 1 4.38-4.38L8 4.5l.72-.73a3.1 3.1 0 0 1 4.38 4.38L8 13.25Z"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linejoin="round"
        />
      </svg>
      <span class="post-count__label">点赞</span>
      {{ likes }}
    </span>
  </span>
</template>

<style scoped>
/* 图标即分组边界，组间一档、图标与数字之间一小档 */
.post-counts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-sm);
  white-space: nowrap;
}

.post-count {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3xs);
}

/* 图标不承载可读语义，文字标签收给读屏 */
.post-count__label {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}
</style>
