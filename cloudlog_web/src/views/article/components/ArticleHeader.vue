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
      <span aria-hidden="true">·</span>
      <span>{{ article.views }} 次阅读</span>
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
</script>

<style scoped>
/* 两列网格：按钮只与标题同一行，meta 通栏。
   若把按钮与「标题+meta」整块并排，meta 会被挤到换行并留下悬空的分隔点 */
.article-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  column-gap: var(--space-md);
  padding-bottom: var(--space-lg);
  border-bottom: 1px solid var(--color-rule);
  margin-bottom: var(--space-lg);
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
  font-size: var(--text-xs);
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

.article-header__edit {
  margin-left: var(--space-2xs);
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
