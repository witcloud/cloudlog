<template>
  <section class="author-card">
    <div class="author-card__head">
      <el-avatar :size="48" :src="formatMinioUrl(author.avatar || defaultAvatar)" />
      <div class="author-card__intro">
        <h3 class="author-card__name">{{ author.username }}</h3>
        <p class="author-card__role">前端开发工程师 / 博主</p>
      </div>
    </div>
    <p class="author-card__bio">专注于 Vue.js 和 TypeScript 领域，热衷于分享技术干货和开发心得。</p>
    <dl class="author-card__stats">
      <div class="author-card__stat">
        <dt>文章</dt>
        <dd class="tnum">{{ stats.articles ?? '—' }}</dd>
      </div>
      <div class="author-card__stat">
        <dt>获赞</dt>
        <dd class="tnum">{{ stats.likes ?? 0 }}</dd>
      </div>
    </dl>
    <el-button type="primary" plain class="author-card__follow">关注作者</el-button>
  </section>
</template>

<script setup lang="ts">
import type { Author } from '@/api/types/article'
import { formatMinioUrl } from '@/config'

defineProps<{
  author: Author
  stats: {
    articles?: number
    likes?: number
  }
}>()

const defaultAvatar = '/default-avatar.png'
</script>

<style scoped>
.author-card {
  padding: var(--space-lg);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  background-color: var(--color-paper-2);
}

.author-card__head {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.author-card__name {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-ink);
}

.author-card__role {
  margin-top: var(--space-3xs);
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.author-card__bio {
  margin-top: var(--space-md);
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--color-ink-2);
}

.author-card__stats {
  display: flex;
  gap: var(--space-xl);
  margin-top: var(--space-md);
  padding-block: var(--space-md);
  border-top: 1px solid var(--color-rule);
}

.author-card__stat dt {
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.author-card__stat dd {
  margin-top: var(--space-3xs);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-ink);
}

.author-card__follow {
  width: 100%;
}
</style>
