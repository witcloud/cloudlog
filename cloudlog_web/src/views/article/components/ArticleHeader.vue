<template>
  <header class="article-header">
    <h1 class="article-header__title">{{ article.title }}</h1>

    <!-- 右栏作者卡要到 60rem 才渲染，窄屏的关注入口落在标题区右上角 -->
    <FollowButton
      v-if="showFollow"
      class="article-header__follow"
      :followed="followed"
      :pending="followPending"
      @toggle="$emit('toggle-follow')"
    />

    <div class="article-header__meta tnum">
      <span>发布于 {{ formatDate(article.publishedAt) }}</span>
      <span aria-hidden="true">·</span>
      <RouterLink class="article-header__author" :to="`/authors/${article.author.id}`">{{
        article.author.username
      }}</RouterLink>
      <template v-if="article.category">
        <span aria-hidden="true">·</span>
        <RouterLink
          class="article-header__category"
          :to="{ path: '/', query: { categoryId: article.category.id } }"
          >{{ article.category.name }}</RouterLink
        >
      </template>
      <span aria-hidden="true">·</span>
      <span>{{ article.views }} 次阅读</span>
      <span aria-hidden="true">·</span>
      <span>约 {{ readingMinutes }} 分钟读完</span>
      <button v-if="canEdit" class="article-header__edit" @click="$emit('edit')">编辑</button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Article } from '@/api/types/article'
import dayjs from 'dayjs'
import { useUserStore } from '@/stores/user'
import FollowButton from './FollowButton.vue'

const props = defineProps<{
  article: Article
  canEdit: boolean
  followPending: boolean
}>()

defineEmits(['edit', 'toggle-follow'])

const userStore = useUserStore()

// 与作者卡同一判据：本人不出现关注入口，服务端另有 400 兜底
const showFollow = computed(() => userStore.userInfo?.id !== props.article.author.id)
const followed = computed(() => props.article.author.followed ?? false)

const formatDate = (date: string) => {
  return dayjs(date).format('YYYY-MM-DD')
}

// 预计阅读时长：CJK 350 字/分钟 + 英文 200 词/分钟（主流平台同一量级口径），代码块不计
const readingMinutes = computed(() => {
  const text = (props.article.content ?? '').replace(/```[\s\S]*?```/g, ' ')
  const cjk = (text.match(/[\u4e00-\u9fff]/g) ?? []).length
  const words = (text.match(/[A-Za-z0-9][A-Za-z0-9'’._-]*/g) ?? []).length
  return Math.max(1, Math.round(cjk / 350 + words / 200))
})
</script>

<style scoped>
/* 两列网格：按钮只与标题同一行，meta 通栏。
   若把按钮与「标题+meta」整块并排，meta 会被挤到换行并留下悬空的分隔点 */
.article-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  column-gap: var(--space-md);
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--color-rule);
  margin-bottom: var(--space-md);
}

.article-header__title {
  grid-column: 1;
  grid-row: 1;
  font-size: var(--text-2xl);
  font-weight: 700;
  line-height: 1.3;
  color: var(--color-ink);
}

.article-header__meta {
  grid-column: 1 / -1;
  grid-row: 2;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
  margin-top: var(--space-md);
  /* 12px 的中文 meta 在移动端贴近可读下限，主流平台 meta 行是 13–14px */
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

/* 关注入口全站只有一处：≥60rem 由右栏作者卡承载，窄屏右栏不渲染所以落在这里 */
.article-header__follow {
  display: none;
  grid-column: 2;
  grid-row: 1;
  justify-self: end;
}

@media (max-width: 59.9375rem) {
  .article-header .article-header__follow {
    display: inline-flex;
  }
}

.article-header__author {
  color: inherit;
  transition: color var(--dur-micro) var(--ease-out);
}

.article-header__author:hover {
  color: var(--color-accent);
}

.article-header__category {
  color: inherit;
  transition: color var(--dur-micro) var(--ease-out);
}

.article-header__category:hover {
  color: var(--color-accent);
}

.article-header__edit {
  /* 12px 纯文本的点击目标太小：内衬扩出 hit area，再用等值负 margin 抵回版面 */
  padding: var(--space-2xs);
  margin-block: calc(-1 * var(--space-2xs));
  font-size: var(--text-xs);
  color: var(--color-accent);
}

.article-header__edit:hover {
  text-decoration: underline;
  text-underline-offset: 0.25em;
}

@media (max-width: 40rem) {
  .article-header__title {
    font-size: var(--text-lg);
  }
}
</style>
