<template>
  <div class="home">
    <div class="home__grid">
      <!-- 桌面左栏：分类筛选 -->
      <aside v-if="showAside" class="home__aside home__aside--left">
        <section class="panel" aria-label="分类筛选">
          <ul class="category-list">
            <li>
              <button
                class="category-item"
                :class="{ 'is-active': !currentCategoryId }"
                @click="handleCategorySelect(undefined)"
              >
                <span class="category-item__name">全部文章</span>
                <span class="category-item__count tnum">{{ allTotal ?? '' }}</span>
              </button>
            </li>
            <li v-for="cat in categories" :key="cat.id">
              <button
                class="category-item"
                :class="{
                  'is-active': currentCategoryId === cat.id,
                  'is-empty': categoryCounts[cat.id] === 0,
                }"
                @click="handleCategorySelect(cat.id)"
              >
                <span class="category-item__name" :title="cat.name">{{ cat.name }}</span>
                <span v-if="categoryCounts[cat.id] != null" class="category-item__count tnum">
                  {{ categoryCounts[cat.id] }}
                </span>
              </button>
            </li>
          </ul>
        </section>
      </aside>

      <!-- 中栏：文章列表（滚动加载） -->
      <div class="home__center">
        <!-- 窄屏分类条与桌面左栏分类互斥挂载，而非两份 DOM 靠显示隐藏切换（docs/ui-design.md §6.4） -->
        <nav v-if="!showAside" class="category-tabs" aria-label="分类">
          <div class="category-tabs__scroll">
            <button
              class="category-tabs__item"
              :class="{ 'is-active': !currentCategoryId }"
              @click="handleCategorySelect(undefined)"
            >
              全部
            </button>
            <button
              v-for="cat in categories"
              :key="cat.id"
              class="category-tabs__item"
              :class="{ 'is-active': currentCategoryId === cat.id }"
              :aria-current="currentCategoryId === cat.id ? 'true' : undefined"
              @click="handleCategorySelect(cat.id)"
            >
              {{ cat.name }}
            </button>
          </div>
        </nav>

        <div v-if="filterInfo" class="filter-bar">
          <p class="filter-bar__text">
            <span class="filter-bar__type">{{ filterInfo.type }}</span>
            <strong class="filter-bar__value">{{ filterInfo.value }}</strong>
            <span class="filter-bar__count tnum">共 {{ total }} 篇</span>
          </p>
          <button class="filter-bar__clear" @click="clearFilter">清除筛选</button>
        </div>

        <!-- 排序 tab：掘金式下划线。工具条自成一块面板，不套进 .article-area（该框自带 --space-md 内衬，套进去就成了两倍） -->
        <div class="sort-tabs" role="tablist" aria-label="文章排序">
          <button
            v-for="tab in sortTabs"
            :key="tab.key"
            class="sort-tabs__item"
            :class="{ 'is-active': currentSort === tab.key }"
            role="tab"
            :aria-selected="currentSort === tab.key"
            @click="handleSortSelect(tab.key)"
          >
            {{ tab.label }}
          </button>
        </div>

        <div v-loading="loading" class="article-area">
          <div v-if="pinnedArticles.length" class="pinned-zone">
            <ArticleCard v-for="article in pinnedArticles" :key="article.id" :article="article" />
          </div>

          <div
            v-if="normalArticles.length"
            class="article-list"
            :class="{ 'article-list--after-pinned': pinnedArticles.length > 0 }"
          >
            <ArticleCard v-for="article in normalArticles" :key="article.id" :article="article" />
          </div>

          <div v-if="!loading && articles.length === 0" class="empty-state">
            <p class="empty-state__title">未找到相关文章。</p>
            <p class="empty-state__hint">换个分类或关键词，也许能找到想读的内容。</p>
            <button class="empty-state__action" @click="clearFilter">查看全部文章</button>
          </div>

          <div ref="sentinelRef" class="load-sentinel" aria-live="polite">
            <span v-if="loadingMore">加载中…</span>
            <span v-else-if="total > pageSize && !hasMore">没有更多了</span>
          </div>
        </div>

        <!-- 窄屏没有右栏，页脚回落到中栏底部；宽屏挂右栏末端，两处恒只渲染一处 -->
        <SiteFooter v-if="!showAside" />
      </div>

      <!-- 右栏：热门文章 + 站点统计 + 页脚；窄屏整栏不渲染，这两块只在宽屏出现 -->
      <aside v-if="showAside" class="home__aside home__aside--right">
        <PopularArticles />

        <section class="panel" aria-label="站点统计">
          <p class="panel__label">站点统计</p>
          <dl class="stats">
            <div class="stats__row">
              <dt>文章总数</dt>
              <dd class="tnum">{{ allTotal ?? '—' }}</dd>
            </div>
            <div class="stats__row">
              <dt>分类数</dt>
              <dd class="tnum">{{ categoryTotal ?? '—' }}</dd>
            </div>
            <div class="stats__row">
              <dt>最近更新</dt>
              <dd class="tnum">{{ latestUpdate || '—' }}</dd>
            </div>
          </dl>
        </section>

        <SiteFooter />
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getArticles } from '@/api/article'
import { getCategories } from '@/api/category'
import { getSiteStats } from '@/api/site'
import type { Article, ArticleListParams, ArticleSort } from '@/api/types/article'
import type { Category } from '@/api/types/category'
import type { SiteStats } from '@/api/types/site'
import ArticleCard from '@/components/ArticleCard.vue'
import PopularArticles from '@/components/PopularArticles.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const articles = ref<Article[]>([])
const categories = ref<Category[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const sentinelRef = ref<HTMLElement>()

/** 统计卡三项与左栏分类计数同源：一次 /site/stats 取回全部真值 */
const stats = ref<SiteStats | null>(null)
const allTotal = computed(() => stats.value?.articleCount ?? null)
const categoryTotal = computed(() => stats.value?.categoryCount ?? null)
const categoryCounts = computed(() => stats.value?.categoryCounts ?? {})

/** ≤960px 时侧栏收进抽屉，同一份内容只渲染一处 */
const wideQuery = window.matchMedia('(min-width: 60rem)')
const showAside = ref(wideQuery.matches)
const onWideChange = (event: MediaQueryListEvent) => {
  showAside.value = event.matches
}

/** 分类筛选契约：?categoryId=。id 稳定，分类重命名后旧链接仍能定位 */
const currentCategoryId = computed(() => {
  if (route.query.categoryId) return Number(route.query.categoryId)
  // 兼容历史 ?category=（分类名）：反查不到即回落全部文章
  const name = typeof route.query.category === 'string' ? route.query.category : ''
  return name ? categories.value.find((c) => c.name === name)?.id : undefined
})

/** 排序 tab 的取值：URL 无 sort 即「推荐」，?sort=latest 才是「最新」。
 *  推荐显式传 recommended 而不是复用后端缺省值——缺省是 latest，那是搜索页与作者主页在走的口径 */
const currentSort = computed<ArticleSort>(() =>
  route.query.sort === 'latest' ? 'latest' : 'recommended',
)

const sortTabs = [
  { key: 'recommended', label: '推荐' },
  { key: 'latest', label: '最新' },
] as const

/** 筛选横幅只承载「列表之外无处可寻」的状态：分类的选中态由筛选控件自身显示 */
const filterInfo = computed(() => {
  if (route.query.q) return { type: '搜索', value: route.query.q as string }
  if (route.query.tag) return { type: '标签', value: route.query.tag as string }
  return null
})

/** 两个 tab 走的 SQL 分支都以 is_top DESC 打头（latest 与 recommended 都是），置顶恒在当前页头部，
 *  此处按标记切分即可，无需额外请求 */
const pinnedArticles = computed(() => articles.value.filter((a) => a.isTop))
const normalArticles = computed(() => articles.value.filter((a) => !a.isTop))

/** 拉到空页即收口：哨兵会自我复查，没有这个闸门，total 比实际可取行数大时会请求到死 */
const exhausted = ref(false)

/** 还有下一页的判据：已加载条数未达接口 total，且没收到过空页。不据「本页不满一页」推断 */
const hasMore = computed(() => !exhausted.value && articles.value.length < total.value)

/** 全站最近发布时刻，不随筛选与翻页变化；无已发布文章时为空，模板回落 '—' */
const latestUpdate = computed(() =>
  stats.value?.latestPublishedAt ? dayjs(stats.value.latestPublishedAt).format('YYYY-MM-DD') : '',
)

/** 「推荐」是默认序，URL 不带 sort；只有「最新」落 ?sort=latest，刷新与分享链接都按 URL 还原 */
const sortQuery = (sort: ArticleSort) => (sort === 'latest' ? { sort: 'latest' } : {})

/** 当前筛选的 query 片段。排序与筛选是两个正交的轴：切 tab 要保留筛选，改筛选要保留 tab */
const activeFilters = computed(() => {
  const { q, tag, tagId } = route.query
  const categoryId = currentCategoryId.value
  return {
    ...(q ? { q } : {}),
    ...(tag ? { tag } : {}),
    ...(tagId ? { tagId } : {}),
    ...(categoryId ? { categoryId: String(categoryId) } : {}),
  }
})

/** 切 tab 只写 query，取数仍由 fetchKey 的 watch 统一负责（重置页码 + 整表替换），不另起本地状态 */
const handleSortSelect = (sort: ArticleSort) => {
  if (sort === currentSort.value) return
  router.replace({ name: 'Home', query: { ...activeFilters.value, ...sortQuery(sort) } })
}

const clearFilter = () => {
  router.push({ name: 'Home', query: sortQuery(currentSort.value) })
}

const handleCategorySelect = (categoryId?: number) => {
  // 切换分类即重置其余筛选，只保留当前排序 tab
  router.push({
    name: 'Home',
    query: {
      ...(categoryId ? { categoryId: String(categoryId) } : {}),
      ...sortQuery(currentSort.value),
    },
  })
}

const fetchCategories = async () => {
  try {
    const { data } = await getCategories()
    categories.value = data
  } catch (error) {
    console.error('Failed to fetch categories:', error)
  }
}

const fetchSiteStats = async () => {
  try {
    const { data } = await getSiteStats()
    stats.value = data
  } catch (error) {
    console.error('Failed to fetch site stats:', error)
  }
}

/** 上次拉取列表的筛选指纹，用于跳过无意义的重复请求（路由切换会生成新的 query 对象，
 *  仅凭对象引用比较会把「离开/返回首页」误判为筛选变化，造成缓存页闪 loading） */
const lastFetchKey = ref('')

/** 每次拉完都重新观察一次哨兵。observe() 必定异步回调一次当前相交状态，
 *  而「新进入视口」事件在首屏不必滚动时根本不会发生——不复查就会卡在第一条之后 */
let scrollObserver: IntersectionObserver | undefined

const observeSentinel = () => {
  if (!scrollObserver) return
  scrollObserver.disconnect()
  if (sentinelRef.value) scrollObserver.observe(sentinelRef.value)
}

/** append=true 追加到已加载列表尾部（滚动加载），false 整表替换（首屏与筛选变化）。
 *  返回值供调用方决定是否回退页码 */
const fetchArticles = async (append = false): Promise<boolean> => {
  const key = lastFetchKey.value
  if (append) loadingMore.value = true
  else loading.value = true
  try {
    const params: ArticleListParams = {
      page: page.value,
      pageSize: pageSize.value,
      keyword: route.query.q as string,
      categoryId: currentCategoryId.value,
      tagId: route.query.tagId ? Number(route.query.tagId) : undefined,
      sort: currentSort.value,
    }
    const { data } = await getArticles(params)
    // 请求期间筛选已切换：这一页属于旧指纹，丢掉，否则会串进新列表
    if (key !== lastFetchKey.value) return false
    if (!append) exhausted.value = false
    articles.value = append ? [...articles.value, ...data.list] : data.list
    total.value = Number(data.total) // PageResult.total 以字符串下发（Long 统一转字符串）
    if (data.list.length === 0) exhausted.value = true
    return true
  } catch (error) {
    console.error('Failed to fetch articles:', error)
    return false
  } finally {
    loading.value = false
    loadingMore.value = false
    observeSentinel()
  }
}

const loadMore = async () => {
  if (loading.value || loadingMore.value || !hasMore.value) return
  const key = lastFetchKey.value
  page.value += 1
  const ok = await fetchArticles(true)
  // 仅在筛选未变时回退页码；筛选已切换则页码被重置为 1，回退会退到 0
  if (!ok && key === lastFetchKey.value) page.value -= 1
}

onMounted(async () => {
  wideQuery.addEventListener('change', onWideChange)
  lastFetchKey.value = fetchKey()
  // 旧 ?category=（按分类名）要靠分类表反查 id：分类没先落到手，首帧反查不到就拉成了全部文章
  await fetchCategories()
  fetchSiteStats()
  fetchArticles()
  scrollObserver = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) loadMore()
    },
    { rootMargin: '300px' },
  )
})

onUnmounted(() => {
  wideQuery.removeEventListener('change', onWideChange)
  scrollObserver?.disconnect()
})

/** 筛选指纹：路由名 + 筛选参数。页码不入指纹——滚动加载会自行推进页码，
 *  入指纹等于每追加一页就触发一次整表替换 */
const fetchKey = () => `${String(route.name)}|${JSON.stringify(route.query)}`

watch(fetchKey, (key, prevKey) => {
  // 离开首页（如去关于页）时路由名变化会触发本 watch，但不应发起请求；
  // 返回首页时指纹与离开前相同，也直接跳过
  if (route.name !== 'Home' || key === prevKey || key === lastFetchKey.value) return
  lastFetchKey.value = key
  page.value = 1
  fetchArticles()
})
</script>

<style scoped lang="scss">
.home {
  width: 100%;
}

.home__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-xl);
  align-items: start;
}

.home__aside--left {
  grid-column: 1;
  grid-row: 1;
  /* 阈值取初始位置（header 56 + 容器 padding-top 24 = 80）再 +1px：阈值一旦小于初始位置，
     左栏会先随页面滑到阈值才钉住，看起来就是"滑动时抖了一下"；不小于初始位置则第一帧起即固定。
     右栏不吸附，随页面滚走 */
  position: sticky;
  top: calc(var(--header-h) + var(--space-lg) + 1px);
}

.home__center {
  grid-column: 1;
  display: flex;
  flex-direction: column;
}

.home__aside--right {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);

  /* 页脚的 margin-top 按中栏底部设计；右栏已有 gap，去掉以免叠成两倍间距 */
  :deep(.site-footer) {
    margin-top: 0;
  }
}

/* ---- 面板：发丝线框，不堆卡片 ---- */
.panel {
  & + .panel {
    margin-top: var(--space-xl);
  }

  &__label {
    margin-bottom: var(--space-xs);
    font-size: var(--text-xs);
    letter-spacing: 0.08em;
    color: var(--color-ink-4);
  }
}

/* 左栏分类抬升为面板（卡片范式 docs/ui-design.md §6.2）：描边/圆角/阴影与中栏 .article-area 同款。
   横向只留 --space-xs：分类行自带 8px 内衬，选中底再叠一层面板内衬就成两倍。
   可见小标题「分类」已删，面板名称由 section 的 aria-label 承担；右栏与抽屉里的 .panel 未点名 */
.home__aside--left .panel {
  padding: var(--space-md) var(--space-xs);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
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

  /* 空分类降权：仍可点击进入空状态，不是死链，故只降色不禁用；置于 :hover 之前以保住悬停反馈 */
  &.is-empty {
    color: var(--color-ink-4);

    .category-item__count {
      color: var(--color-ink-4);
    }
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

/* ---- 窄屏分类横滑条：与桌面左栏同款抬升面板，选中/hover 语言沿用 .category-item ---- */
.category-tabs {
  margin-bottom: var(--space-md);
  /* 纵向只留 2xs：按钮自带 4px 内衬，再叠一层就成了两倍（桌面面板同口径倒推） */
  padding: var(--space-2xs) var(--space-xs);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);

  &__scroll {
    display: flex;
    gap: var(--space-2xs);
    overflow-x: auto;
    scrollbar-width: none;

    /* 渐隐落在滚动层而非面板层，否则面板描边会被一起淡掉；
       遮罩只取 alpha 通道，故用不透明的墨色 Token，不引入色板值 */
    mask-image: linear-gradient(
      to right,
      var(--color-ink) 0,
      var(--color-ink) calc(100% - var(--space-lg)),
      transparent
    );

    &::-webkit-scrollbar {
      display: none;
    }
  }

  &__item {
    flex-shrink: 0;
    padding: var(--space-2xs) var(--space-sm);
    font-size: var(--text-sm);
    color: var(--color-ink-2);
    white-space: nowrap;
    border-radius: var(--radius-sm);
    transition:
      color var(--dur-micro) var(--ease-out),
      background-color var(--dur-micro) var(--ease-out);

    &:hover {
      color: var(--color-ink);
      background-color: var(--color-paper-3);
    }

    &.is-active {
      color: var(--color-accent);
      background-color: var(--color-accent-wash);
    }
  }
}

/* ---- 站点统计：单行键值对，压低右栏视觉权重 ---- */
.stats {
  display: flex;
  flex-direction: column;
}

.stats__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-block: var(--space-2xs);
  font-size: var(--text-sm);
  line-height: 1.6;

  dt {
    color: var(--color-ink-3);
  }

  dd {
    color: var(--color-ink-2);
  }
}

/* ---- 筛选信息条 ---- */
.filter-bar {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-md);
  padding-bottom: var(--space-md);
  /* 面板顶边已承担分隔，此处若再留一条边框会与面板描边叠成双线 */
  margin-bottom: var(--space-md);

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

/* ---- 排序 tab：掘金式下划线。悬停只变色（docs/ui-design.md:262），激活态的下划线由自身宽度撑出、
       不做位移动画（:246 禁位移动画，:245 的顶栏进度条已为「只作用于自身宽度」开了先例）。
       与窄屏 .category-tabs 同款抬升面板：横向取 --space-lg，与下方 .article-area 的内衬同值，
       标签因此与文章标题落在同一条内容边；纵向只留 --space-xs，下划线自带 2xs 偏移不再叠加 ---- */
.sort-tabs {
  display: flex;
  gap: var(--space-lg);
  /* 与下方列表框拼成同一块面板：不留间距、下缘圆角收平，描边由 .article-area 上移 1px 叠合，
     否则两条发丝线并排就成了双线 */
  padding: var(--space-xs) var(--space-lg);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;

  &__item {
    position: relative;
    padding-bottom: var(--space-2xs);
    font-size: var(--text-sm);
    color: var(--color-ink-3);
    transition: color var(--dur-micro) var(--ease-out);

    &:hover:not(.is-active) {
      color: var(--color-ink);
    }

    &.is-active {
      color: var(--color-accent);

      &::after {
        content: '';
        position: absolute;
        right: 0;
        bottom: 0;
        left: 0;
        height: 2px;
        background-color: var(--color-accent);
      }
    }
  }
}

/* ---- 文章列表：抬升面板（卡片范式见 docs/ui-design.md:310）+ 内部发丝线分行。
       上缘与 .sort-tabs 拼合：上移 1px 让两条描边叠成一条，上缘圆角收平 ---- */
.article-area {
  min-height: 12rem;
  margin-top: -1px;
  padding: var(--space-md) var(--space-lg);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

/* ---- 置顶区：凹陷面（paper-3）在抬升面板上呈极浅灰，配朱砂左边框；区内卡片归零纵向内衬 ----
   外扩量 = 自己的横向内衬（+ 左边框 2px），把面推进 .article-area 的 --space-lg 内衬里，
   文字因此落回与下方列表同一条内容边；分隔本来就由底色差 + 朱砂条 + 下方间距承担 */
.pinned-zone {
  margin-left: calc(-1 * (var(--space-md) + 2px));
  margin-right: calc(-1 * var(--space-md));
  padding: var(--space-sm) var(--space-md);
  background-color: var(--color-paper-3);
  border-left: 2px solid var(--color-accent);
  border-radius: var(--radius-sm);

  :deep(.article-card) {
    padding-block: 0;
  }

  :deep(.article-card + .article-card) {
    margin-top: var(--space-sm);
    padding-top: var(--space-sm);
    border-top: 1px solid var(--color-rule);
  }
}

.article-list {
  display: flex;
  flex-direction: column;

  :deep(.article-card + .article-card) {
    border-top: 1px solid var(--color-rule);
  }

  /* 首条不留上边距，末条保留下边距，让列表贴合容器节奏 */
  :deep(.article-card:first-child) {
    padding-top: 0;
  }

  :deep(.article-card:last-child) {
    padding-bottom: 0;
  }

  /* 紧跟置顶区：凹陷底色差本身已是分隔，只留间距，不再叠发丝线也不补内衬 */
  &--after-pinned {
    margin-top: var(--space-lg);
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

/* ---- 滚动加载哨兵：列表尾的一行状态，不占卡片位 ---- */
.load-sentinel {
  padding-top: var(--space-md);
  font-size: var(--text-sm);
  color: var(--color-ink-4);
  text-align: center;

  /* 首屏与空态都不需要这行留白撑高 */
  &:empty {
    padding-top: 0;
  }
}

/* ---- 断点 ---- */
@media (min-width: 60rem) {
  /* 左 16% : 中 60% : 右 24% 严格占比 —— 栏间缝不能占轨道，改由侧栏内侧衬承担，
     中栏因此拿到完整 60%（600px → 691px），两侧文字宽各减一个缝宽 */
  .home__grid {
    column-gap: 0;
    grid-template-columns: minmax(0, 16fr) minmax(0, 60fr) minmax(0, 24fr);
  }

  .home__aside--left {
    padding-right: var(--space-md);
  }

  .home__aside--right {
    padding-left: var(--space-md);
  }

  .home__center {
    grid-column: 2;
  }
}

@media (min-width: 75rem) {
  /* 容器到 72rem 上限后有余量，侧栏内衬升一档；比例不变 */
  .home__aside--left {
    padding-right: var(--space-lg);
  }

  .home__aside--right {
    padding-left: var(--space-lg);
  }
}
</style>
