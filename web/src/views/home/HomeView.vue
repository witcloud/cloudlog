<template>
  <div class="home">
    <!-- 移动端：侧栏收为抽屉的入口 -->
    <button class="filter-toggle" @click="drawerOpen = true">
      <svg viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true">
        <path
          d="M2 4h8M12 4h2M2 8h2M6 8h8M2 12h8M12 12h2"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
        />
        <circle cx="10" cy="4" r="1.4" stroke="currentColor" stroke-width="1.2" />
        <circle cx="4" cy="8" r="1.4" stroke="currentColor" stroke-width="1.2" />
        <circle cx="10" cy="12" r="1.4" stroke="currentColor" stroke-width="1.2" />
      </svg>
      筛选与数据
    </button>

    <div class="home__grid">
      <!-- 桌面左栏：分类筛选 -->
      <aside v-if="showAside" class="home__aside home__aside--left">
        <section class="panel" aria-label="分类筛选">
          <p class="panel__label">分类</p>
          <ul class="category-list">
            <li>
              <button
                class="category-item"
                :class="{ 'is-active': !currentCategoryName }"
                @click="handleCategorySelect(undefined)"
              >
                <span class="category-item__name">全部文章</span>
                <span class="category-item__count tnum">{{ allTotal ?? '' }}</span>
              </button>
            </li>
            <li v-for="cat in categories" :key="cat.id">
              <button
                class="category-item"
                :class="{ 'is-active': currentCategoryName === cat.name }"
                @click="handleCategorySelect(cat.name)"
              >
                <span class="category-item__name">{{ cat.name }}</span>
                <span v-if="categoryCounts[cat.id] != null" class="category-item__count tnum">
                  {{ categoryCounts[cat.id] }}
                </span>
              </button>
            </li>
          </ul>
        </section>
      </aside>

      <!-- 中栏：文章列表 + 页脚 -->
      <div class="home__center">
        <div v-if="filterInfo" class="filter-bar">
          <p class="filter-bar__text">
            <span class="filter-bar__type">{{ filterInfo.type }}</span>
            <strong class="filter-bar__value">{{ filterInfo.value }}</strong>
            <span class="filter-bar__count tnum">共 {{ total }} 篇</span>
          </p>
          <button class="filter-bar__clear" @click="clearFilter">清除筛选</button>
        </div>

        <div v-loading="loading" class="article-area">
          <div v-if="articles.length > 0" class="article-list">
            <ArticleCard v-for="article in articles" :key="article.id" :article="article" />
          </div>

          <div v-else-if="!loading" class="empty-state">
            <p class="empty-state__title">未找到相关文章。</p>
            <p class="empty-state__hint">换个分类或关键词，也许能找到想读的内容。</p>
            <button class="empty-state__action" @click="clearFilter">查看全部文章</button>
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
        </div>

        <SiteFooter />
      </div>

      <!-- 桌面右栏：站点统计 + 热门文章 -->
      <aside v-if="showAside" class="home__aside home__aside--right">
        <section class="panel" aria-label="站点统计">
          <p class="panel__label">站点统计</p>
          <dl class="stats">
            <div class="stats__row">
              <dt>文章总数</dt>
              <dd class="tnum">{{ allTotal ?? '—' }}</dd>
            </div>
            <div class="stats__row">
              <dt>分类数</dt>
              <dd class="tnum">{{ categories.length || '—' }}</dd>
            </div>
            <div class="stats__row">
              <dt>最近更新</dt>
              <dd class="tnum">{{ latestUpdate || '—' }}</dd>
            </div>
          </dl>
        </section>

        <PopularArticles />
      </aside>
    </div>

    <!-- 移动端抽屉：与桌面侧栏同一份内容 -->
    <el-drawer v-model="drawerOpen" direction="ltr" size="82%" :with-header="false">
      <div v-if="!showAside" class="drawer-body">
        <section class="panel" aria-label="分类筛选">
          <p class="panel__label">分类</p>
          <ul class="category-list">
            <li>
              <button
                class="category-item"
                :class="{ 'is-active': !currentCategoryName }"
                @click="handleCategorySelect(undefined)"
              >
                <span class="category-item__name">全部文章</span>
                <span class="category-item__count tnum">{{ allTotal ?? '' }}</span>
              </button>
            </li>
            <li v-for="cat in categories" :key="cat.id">
              <button
                class="category-item"
                :class="{ 'is-active': currentCategoryName === cat.name }"
                @click="handleCategorySelect(cat.name)"
              >
                <span class="category-item__name">{{ cat.name }}</span>
                <span v-if="categoryCounts[cat.id] != null" class="category-item__count tnum">
                  {{ categoryCounts[cat.id] }}
                </span>
              </button>
            </li>
          </ul>
        </section>

        <section class="panel" aria-label="站点统计">
          <p class="panel__label">站点统计</p>
          <dl class="stats">
            <div class="stats__row">
              <dt>文章总数</dt>
              <dd class="tnum">{{ allTotal ?? '—' }}</dd>
            </div>
            <div class="stats__row">
              <dt>分类数</dt>
              <dd class="tnum">{{ categories.length || '—' }}</dd>
            </div>
            <div class="stats__row">
              <dt>最近更新</dt>
              <dd class="tnum">{{ latestUpdate || '—' }}</dd>
            </div>
          </dl>
        </section>

        <PopularArticles />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getArticles } from '@/api/article'
import { getCategories } from '@/api/category'
import type { Article, ArticleListParams } from '@/api/types/article'
import type { Category } from '@/api/types/category'
import ArticleCard from '@/components/ArticleCard.vue'
import PopularArticles from '@/components/PopularArticles.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const articles = ref<Article[]>([])
const categories = ref<Category[]>([])
const loading = ref(false)
const drawerOpen = ref(false)
const allTotal = ref<number | null>(null)
const categoryCounts = ref<Record<number, number>>({})

/** ≤960px 时侧栏收进抽屉，同一份内容只渲染一处 */
const wideQuery = window.matchMedia('(min-width: 60rem)')
const showAside = ref(wideQuery.matches)
const onWideChange = (event: MediaQueryListEvent) => {
  showAside.value = event.matches
}

/** 分类筛选契约：?category=（分类名），兼容历史 ?categoryId= */
const currentCategoryName = computed(() =>
  typeof route.query.category === 'string' && route.query.category
    ? route.query.category
    : undefined,
)

const currentCategoryId = computed(() => {
  if (currentCategoryName.value) {
    const found = categories.value.find((c) => c.name === currentCategoryName.value)
    if (found) return found.id
  }
  return route.query.categoryId ? Number(route.query.categoryId) : undefined
})

const filterInfo = computed(() => {
  if (route.query.q) return { type: '搜索', value: route.query.q as string }
  if (currentCategoryName.value) return { type: '分类', value: currentCategoryName.value }
  if (route.query.tag) return { type: '标签', value: route.query.tag as string }
  return null
})

const latestUpdate = computed(() => {
  const first = articles.value[0]?.publishedAt
  return first ? dayjs(first).format('YYYY-MM-DD') : ''
})

const clearFilter = () => {
  router.push({ name: 'Home' })
}

const handleCategorySelect = (name?: string) => {
  const query: Record<string, string> = {}
  // 切换分类即重置筛选
  if (name) query.category = name
  currentPage.value = 1
  drawerOpen.value = false
  router.push({ name: 'Home', query })
}

const fetchCategories = async () => {
  try {
    const { data } = await getCategories()
    categories.value = data
    fetchCategoryCounts(data)
  } catch (error) {
    console.error('Failed to fetch categories:', error)
  }
}

/** 分类计数与全站文章数都取自列表接口的真实 total（pageSize=1） */
const fetchCategoryCounts = async (cats: Category[]) => {
  try {
    const [allRes, ...catResults] = await Promise.allSettled([
      getArticles({ page: 1, pageSize: 1 }),
      ...cats.map((cat) => getArticles({ page: 1, pageSize: 1, categoryId: cat.id })),
    ])
    if (allRes && allRes.status === 'fulfilled') {
      allTotal.value = allRes.value.data.total
    }
    cats.forEach((cat, index) => {
      const result = catResults[index]
      if (result && result.status === 'fulfilled') {
        categoryCounts.value[cat.id] = result.value.data.total
      }
    })
  } catch (error) {
    console.error('Failed to fetch category counts:', error)
  }
}

const fetchArticles = async () => {
  loading.value = true
  try {
    const params: ArticleListParams = {
      page: currentPage.value,
      pageSize: pageSize.value,
      keyword: route.query.q as string,
      categoryId: currentCategoryId.value,
      tagId: route.query.tagId ? Number(route.query.tagId) : undefined,
    }
    const { data } = await getArticles(params)
    articles.value = data.list
    total.value = data.total
  } catch (error) {
    console.error('Failed to fetch articles:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  wideQuery.addEventListener('change', onWideChange)
  fetchCategories()
  fetchArticles()
})

onUnmounted(() => {
  wideQuery.removeEventListener('change', onWideChange)
})

watch(
  () => [route.query, currentPage.value],
  () => {
    fetchArticles()
  },
)

const handlePageChange = (page: number) => {
  currentPage.value = page
}
</script>

<style scoped lang="scss">
.home {
  width: 100%;
}

/* 移动端筛选入口 */
.filter-toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  margin-bottom: var(--space-lg);
  padding: var(--space-xs) var(--space-sm);
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  border: 1px solid var(--color-rule-strong);
  border-radius: var(--radius-sm);
  background-color: var(--color-paper-2);

  &:hover {
    color: var(--color-accent);
    border-color: var(--color-accent);
  }
}

.home__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-xl);
  align-items: start;
}

.home__aside {
  position: sticky;
  top: calc(var(--header-h) + var(--space-lg));
}

.home__aside--left {
  grid-column: 1;
  grid-row: 1;
}

.home__center {
  grid-column: 1;
  display: flex;
  flex-direction: column;
}

.home__aside--right {
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
}

/* ---- 面板：发丝线框，不堆卡片 ---- */
.panel {
  & + .panel {
    margin-top: var(--space-xl);
  }

  &__label {
    margin-bottom: var(--space-sm);
    font-size: var(--text-xs);
    letter-spacing: 0.08em;
    color: var(--color-ink-4);
  }
}

.category-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
}

.category-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  width: 100%;
  padding: var(--space-2xs) var(--space-xs);
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  border-radius: var(--radius-sm);
  text-align: start;
  transition:
    color var(--dur-micro) var(--ease-out),
    background-color var(--dur-micro) var(--ease-out);

  &__name {
    display: inline-flex;
    align-items: center;
    gap: var(--space-2xs);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__count {
    font-size: var(--text-xs);
    color: var(--color-ink-4);
  }

  &:hover {
    color: var(--color-ink);
    background-color: var(--color-paper-3);
  }

  &.is-active {
    color: var(--color-accent);
    background-color: var(--color-accent-wash);

    .category-item__count {
      color: var(--color-accent);
    }

    .category-item__name::before {
      content: '';
      width: 0.375rem;
      height: 0.375rem;
      background-color: var(--color-accent);
      border-radius: var(--radius-xs);
      flex-shrink: 0;
    }
  }
}

/* ---- 筛选信息条 ---- */
.filter-bar {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-md);
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--color-rule);

  &__type {
    font-size: var(--text-sm);
    color: var(--color-ink-3);
    margin-right: var(--space-2xs);
  }

  &__value {
    font-family: var(--font-display);
    font-size: var(--text-md);
    font-weight: 600;
    color: var(--color-ink);
  }

  &__count {
    margin-left: var(--space-sm);
    font-size: var(--text-sm);
    color: var(--color-ink-4);
  }

  &__clear {
    font-size: var(--text-sm);
    color: var(--color-ink-3);
    white-space: nowrap;

    &:hover {
      color: var(--color-accent);
    }
  }
}

/* ---- 文章列表：发丝线分行的编辑式列表 ---- */
.article-area {
  min-height: 12rem;
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

/* ---- 抽屉内复用侧栏内容 ---- */
.drawer-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
  padding-block: var(--space-sm);
}

/* ---- 断点 ---- */
@media (min-width: 60rem) {
  .filter-toggle {
    display: none;
  }

  .home__grid {
    grid-template-columns: 11rem minmax(0, 1fr) 15rem;
  }

  .home__center {
    grid-column: 2;
  }
}

@media (min-width: 75rem) {
  .home__grid {
    grid-template-columns: 12.5rem minmax(0, 1fr) 17rem;
  }
}
</style>
