<template>
  <header class="article-header">
    <h1 class="article-header__title">{{ article.title }}</h1>
    <div class="article-header__meta tnum">
      <span>发布于 {{ formatDate(article.publishedAt) }}</span>
      <span aria-hidden="true">·</span>
      <span>{{ article.author.username }}</span>
      <span aria-hidden="true">·</span>
      <span>{{ article.views }} 次阅读</span>
      <button v-if="canEdit" class="article-header__edit" @click="$emit('edit')">编辑</button>
    </div>
    <div v-if="article.tags?.length" class="article-header__tags tnum">
      <RouterLink
        v-for="tag in article.tags"
        :key="tag.id"
        class="article-header__tag"
        :to="{ path: '/', query: { tagId: tag.id } }"
      >
        #{{ tag.name }}
      </RouterLink>
    </div>
  </header>
</template>

<script setup lang="ts">
import type { Article } from '@/api/types/article'
import dayjs from 'dayjs'

defineProps<{
  article: Article
  canEdit: boolean
}>()

defineEmits(['edit'])

const formatDate = (date: string) => {
  return dayjs(date).format('YYYY-MM-DD')
}
</script>

<style scoped>
.article-header {
  padding-bottom: var(--space-lg);
  border-bottom: 1px solid var(--color-rule);
  margin-bottom: var(--space-xl);
}

.article-header__title {
  font-size: var(--text-2xl);
  font-weight: 700;
  line-height: 1.3;
  color: var(--color-ink);
}

.article-header__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
  margin-top: var(--space-md);
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.article-header__edit {
  margin-left: var(--space-2xs);
  font-size: var(--text-xs);
  color: var(--color-accent);
}

.article-header__edit:hover {
  text-decoration: underline;
  text-underline-offset: 0.25em;
}

.article-header__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.article-header__tag {
  font-size: var(--text-xs);
  color: var(--color-ink-3);
  transition: color var(--dur-micro) var(--ease-out);
}

.article-header__tag:hover {
  color: var(--color-accent);
}

@media (max-width: 40rem) {
  .article-header__title {
    font-size: var(--text-lg);
  }
}
</style>
