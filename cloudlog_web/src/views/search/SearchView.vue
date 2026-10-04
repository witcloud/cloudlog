<template>
  <div class="search-view">
    <header class="search-view__heading">
      <h1 class="search-view__title">搜索</h1>
      <p v-if="submittedKeyword" class="search-view__count tnum">
        「{{ submittedKeyword }}」共 {{ total }} 篇
      </p>
    </header>

    <!-- 顶栏那个框在窄屏不渲染，从抽屉进 /search 时不带词——此时屏幕上只剩一个「搜索」
         标题，得说一句让人往哪儿输入 -->
    <div v-if="!submittedKeyword && !loading" class="search-view__results">
      <div class="empty-state">
        <p class="empty-state__title">还没有输入关键词。</p>
        <p class="empty-state__hint">输入标题或正文里的词，回车就能搜。</p>
      </div>
    </div>

    <div v-else v-loading="loading" class="search-view__results">
      <div v-if="articles.length > 0" class="article-list">
        <ArticleCard v-for="article in articles" :key="article.id" :article="article" />
      </div>

      <div v-else-if="loadFailed && !loading" class="empty-state">
        <p class="empty-state__title">这一页没有加载出来。</p>
        <p class="empty-state__hint">服务没有正常响应，与有没有结果无关。</p>
        <button class="empty-state__action" @click="runSearch">重试</button>
      </div>

      <div v-else-if="!loading" class="empty-state">
        <p class="empty-state__title">没有找到与「{{ submittedKeyword }}」相关的文章。</p>
        <p class="empty-state__hint">试试更短的关键词，或换一个说法。</p>
      </div>
    </div>

    <div v-if="total > pageSize" class="pagination">
      <el-pagination
        :current-page="currentPage"
        :page-size="pageSize"
        layout="prev, pager, next"
        :total="total"
        @current-change="goPage"
      />
    </div>

    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticles } from '@/api/article'
import type { Article } from '@/api/types/article'
import ArticleCard from '@/components/ArticleCard.vue'
import SiteFooter from '@/components/SiteFooter.vue'

const route = useRoute()
const router = useRouter()

const submittedKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const articles = ref<Article[]>([])
const loading = ref(false)
/** 拉失败与「没搜到」是两件事，混在一起说就是把服务的故障报成作者没写过东西 */
const loadFailed = ref(false)

/** 连点页码时旧请求可能后到并覆盖新结果（第 3 页的内容画在第 1 页的选中态上）。
 *  每次拉取记一个「词@页」指纹，回来时不是最新那一个就整包丢掉——首页那条闸门同理解 */
let lastFingerprint = ''

const runSearch = async () => {
  const q = submittedKeyword.value.trim()
  const fingerprint = `${q}@${currentPage.value}`
  lastFingerprint = fingerprint
  if (!q) {
    articles.value = []
    total.value = 0
    loadFailed.value = false
    return
  }
  loading.value = true
  loadFailed.value = false
  try {
    const { data } = await getArticles({
      page: currentPage.value,
      pageSize: pageSize.value,
      keyword: q,
    })
    if (fingerprint !== lastFingerprint) return
    articles.value = data.list
    total.value = Number(data.total) // PageResult.total 以字符串下发（Long 统一转字符串），分页器要 number
  } catch (error) {
    if (fingerprint !== lastFingerprint) return
    console.error('Failed to search articles:', error)
    loadFailed.value = true
  } finally {
    if (fingerprint === lastFingerprint) loading.value = false
  }
}

/** 页码进 URL：搜索结果要能分享，后退时也得回到离开前那一页 */
const goPage = (page: number) => {
  router.push({
    path: '/search',
    query: { ...route.query, page: page > 1 ? String(page) : undefined },
  })
}

const syncFromRoute = () => {
  submittedKeyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  const page = Number(route.query.page)
  currentPage.value = Number.isFinite(page) && page > 0 ? page : 1
  runSearch()
}

onMounted(syncFromRoute)

watch(
  () => [route.query.q, route.query.page],
  () => {
    if (route.name !== 'Search') return
    syncFromRoute()
  },
)
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

  /* 与首页空态那颗同款：文字链，不另起一套按钮 */
  &__action {
    margin-top: var(--space-lg);
    font-size: var(--text-sm);
    color: var(--color-accent);

    &:hover {
      text-decoration: underline;
      text-underline-offset: 0.25em;
    }
  }
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--space-xl);
}
</style>
