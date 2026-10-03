<template>
  <div class="search-view">
    <header class="search-view__heading">
      <h1 class="search-view__title">搜索</h1>
      <p v-if="submittedKeyword" class="search-view__count tnum">
        「{{ submittedKeyword }}」共 {{ total }} 篇
      </p>
    </header>

    <div v-if="submittedKeyword || loading" v-loading="loading" class="search-view__results">
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
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getArticles } from '@/api/article'
import type { Article } from '@/api/types/article'
import ArticleCard from '@/components/ArticleCard.vue'
import SiteFooter from '@/components/SiteFooter.vue'

const route = useRoute()

const submittedKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const articles = ref<Article[]>([])
const loading = ref(false)

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
    total.value = Number(data.total) // PageResult.total 以字符串下发（Long 统一转字符串），分页器要 number
  } catch (error) {
    console.error('Failed to search articles:', error)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  currentPage.value = page
}

onMounted(() => {
  submittedKeyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  runSearch()
})

watch(
  () => route.query.q,
  (q) => {
    if (route.name !== 'Search') return
    submittedKeyword.value = typeof q === 'string' ? q : ''
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
}

/* 计数并入标题行，落在标题基线上（与草稿箱页头同一处理） */
.search-view__heading {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.search-view__count {
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

/* 结果列表抬升为面板，配方同首页 .article-area（§6.2 卡片范式）；分页留在面板之外 */
.search-view__results {
  min-height: 10rem;
  margin-top: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
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
