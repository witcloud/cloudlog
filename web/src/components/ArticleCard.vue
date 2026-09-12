<script setup lang="ts">
import type { Article } from '@/api/types/article'
import { useRouter } from 'vue-router'
import { computed } from 'vue'
import dayjs from 'dayjs'
import { formatMinioUrl } from '@/config'

const props = defineProps<{
  article: Article
}>()

const router = useRouter()

const goToDetail = () => {
  router.push(`/articles/${props.article.id}`)
}

const goToCategory = () => {
  if (props.article.category?.name) {
    router.push({ path: '/', query: { category: props.article.category.name } })
  }
}

const formatDate = computed(() => dayjs(props.article.publishedAt).format('YYYY-MM-DD'))

/** 阅读时长按正文约 400 字/分估算，无正文时退回摘要 */
const readingTime = computed(() => {
  const text = props.article.content || props.article.excerpt || ''
  const minutes = Math.max(1, Math.round(text.replace(/\s/g, '').length / 400))
  return `约 ${minutes} 分钟`
})

const coverUrl = computed(() => formatMinioUrl(props.article.cover))
</script>

<template>
  <article class="article-card" @click="goToDetail">
    <div class="article-card__body">
      <div v-if="article.isTop" class="article-card__pin">置顶</div>
      <h2 class="article-card__title">{{ article.title }}</h2>
      <p class="article-card__excerpt">{{ article.excerpt }}</p>
      <p class="article-card__meta tnum">
        <button
          v-if="article.category?.name"
          class="article-card__category"
          @click.stop="goToCategory"
        >
          {{ article.category.name }}
        </button>
        <span aria-hidden="true">·</span>
        <time :datetime="article.publishedAt">{{ formatDate }}</time>
        <span aria-hidden="true">·</span>
        <span>{{ readingTime }}</span>
      </p>
    </div>

    <img
      v-if="article.cover"
      class="article-card__cover"
      :src="coverUrl"
      :alt="article.title"
      width="108"
      height="76"
      loading="lazy"
    />
  </article>
</template>

<style scoped>
.article-card {
  display: flex;
  gap: var(--space-lg);
  align-items: flex-start;
  padding-block: var(--space-lg);
  cursor: pointer;
}

.article-card__body {
  flex: 1;
  min-width: 0;
}

.article-card__pin {
  display: inline-block;
  margin-bottom: var(--space-2xs);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-accent);
}

.article-card__title {
  font-size: var(--text-md);
  font-weight: 700;
  line-height: 1.4;
  color: var(--color-ink);
  transition: color var(--dur-micro) var(--ease-out);
}

.article-card:hover .article-card__title {
  color: var(--color-accent);
}

.article-card__excerpt {
  margin-top: var(--space-2xs);
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--color-ink-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-card__meta {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-top: var(--space-sm);
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.article-card__category {
  font-size: var(--text-xs);
  font-family: inherit;
  color: var(--color-ink-3);
  white-space: nowrap;
  transition: color var(--dur-micro) var(--ease-out);
}

.article-card__category:hover {
  color: var(--color-accent);
  text-decoration: underline;
  text-underline-offset: 0.25em;
}

.article-card__cover {
  width: 108px;
  height: 76px;
  flex-shrink: 0;
  object-fit: cover;
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
}

@media (max-width: 40rem) {
  .article-card__cover {
    width: 84px;
    height: 64px;
  }
}
</style>
