<template>
  <div class="search-view">
    <header class="search-view__head">
      <h1 class="search-view__title">搜索</h1>
      <form class="search-view__form" role="search" @submit.prevent="submitSearch">
        <svg
          class="search-view__icon"
          viewBox="0 0 16 16"
          width="14"
          height="14"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="m10.5 10.5 3 3M7 11.5a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9Z"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
          />
        </svg>
        <input
          ref="inputRef"
          v-model="keyword"
          type="search"
          class="search-view__input"
          placeholder="输入关键词，按回车搜索…"
          aria-label="搜索文章"
        />
      </form>
    </header>

    <p v-if="submittedKeyword" class="search-view__count tnum">
      「{{ submittedKeyword }}」共 {{ total }} 篇
    </p>

    <div v-loading="loading" class="search-view__results">
      <div v-if="articles.length > 0" class="article-list">
        <ArticleCard v-for="article in articles" :key="article.id" :article="article" />
      </div>

      <div v-else-if="submittedKeyword && !loading" class="empty-state">
        <p class="empty-state__title">没有找到与「{{ submittedKeyword }}」相关的文章。</p>
        <p class="empty-state__hint">试试更短的关键词，或换一个说法。</p>
      </div>
    </div>

    <div v-if="total > pageSize" class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        layout="prev, pager, next"
        :total="total"
        @current-change="handlePageChange"
      />
    </div>

    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticles } from '@/api/article'
import type { Article } from '@/api/types/article'
import ArticleCard from '@/components/ArticleCard.vue'
import SiteFooter from '@/components/SiteFooter.vue'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const submittedKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const articles = ref<Article[]>([])
const loading = ref(false)
const inputRef = ref<HTMLInputElement>()

const runSearch = async () => {
  const q = submittedKeyword.value.trim()
  if (!q) {
    articles.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const { data } = await getArticles({
      page: currentPage.value,
      pageSize: pageSize.value,
      keyword: q,
    })
    articles.value = data.list
    total.value = data.total
  } catch (error) {
    console.error('Failed to search articles:', error)
  } finally {
    loading.value = false
  }
}

const submitSearch = () => {
  const q = keyword.value.trim()
  if (!q) return
  router.push({ path: '/search', query: { q } })
}

const handlePageChange = (page: number) => {
  currentPage.value = page
}

onMounted(async () => {
  const q = typeof route.query.q === 'string' ? route.query.q : ''
  keyword.value = q
  submittedKeyword.value = q
  if (!q) {
    await nextTick()
    inputRef.value?.focus()
  } else {
    runSearch()
  }
})

watch(
  () => route.query.q,
  (q) => {
    if (route.name !== 'Search') return
    const value = typeof q === 'string' ? q : ''
    keyword.value = value
    submittedKeyword.value = value
    currentPage.value = 1
    runSearch()
  },
)

watch(currentPage, runSearch)
</script>

<style scoped lang="scss">
.search-view {
  max-width: 44rem;
}

.search-view__title {
  font-size: var(--text-xl);
  margin-bottom: var(--space-lg);
}

.search-view__form {
  position: relative;
  display: flex;
  align-items: center;
}

.search-view__icon {
  position: absolute;
  inset-inline-start: var(--space-sm);
  color: var(--color-ink-4);
  pointer-events: none;
}

.search-view__input {
  width: 100%;
  height: 2.75rem;
  padding-inline: calc(var(--space-sm) * 2 + 0.875rem) var(--space-md);
  font-size: var(--text-base);
  color: var(--color-ink);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  transition: border-color var(--dur-micro) var(--ease-out);

  &::placeholder {
    color: var(--color-ink-4);
  }

  &:focus {
    outline: none;
    border-color: var(--color-rule-strong);
  }

  &::-webkit-search-cancel-button {
    -webkit-appearance: none;
  }
}

.search-view__count {
  margin-top: var(--space-md);
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

.search-view__results {
  min-height: 10rem;
}

.article-list {
  display: flex;
  flex-direction: column;

  :deep(.article-card + .article-card) {
    border-top: 1px solid var(--color-rule);
  }
}

.empty-state {
  padding: var(--space-2xl) 0;
  text-align: center;

  &__title {
    font-family: var(--font-display);
    font-size: var(--text-md);
    color: var(--color-ink);
  }

  &__hint {
    margin-top: var(--space-xs);
    font-size: var(--text-sm);
    color: var(--color-ink-3);
  }
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--space-xl);
}
</style>
