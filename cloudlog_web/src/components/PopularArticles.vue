<template>
  <section class="popular" aria-label="热门文章">
    <header class="popular__head">
      <p class="panel-label">热门文章</p>
      <button class="popular__refresh" :disabled="loading" @click="handleRefresh">换一换</button>
    </header>

    <Transition name="popular-fade" mode="out-in">
      <ol :key="currentPage" class="popular__list">
        <li
          v-for="(item, index) in displayArticles"
          :key="item.id"
          class="popular__item"
          @click="goToDetail(item.id)"
        >
          <span class="popular__rank tnum">{{ getGlobalIndex(index) + 1 }}</span>
          <span class="popular__title" :title="item.title">{{ item.title }}</span>
        </li>
      </ol>
    </Transition>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHotArticles } from '@/api/article'
import type { HotArticle } from '@/api/types/article'

interface Props {
  pageSize?: number
  poolSize?: number
}

const props = withDefaults(defineProps<Props>(), {
  pageSize: 5,
  poolSize: 20,
})

const router = useRouter()
const loading = ref(false)
const articlePool = ref<HotArticle[]>([])
const currentPage = ref(0)

const displayArticles = computed(() => {
  if (articlePool.value.length === 0) return []
  const start = currentPage.value * props.pageSize
  return articlePool.value.slice(start, start + props.pageSize)
})

const getGlobalIndex = (index: number) => {
  return currentPage.value * props.pageSize + index
}

const fetchHotArticles = async () => {
  loading.value = true
  try {
    const { data } = await getHotArticles({ limit: props.poolSize })
    articlePool.value = data
    currentPage.value = 0
  } catch {
    // 失败提示由响应拦截器统一弹出，这里不重复；热榜取不到就留空列表
  } finally {
    loading.value = false
  }
}

const handleRefresh = async () => {
  if (loading.value) return

  const maxPage = Math.ceil(articlePool.value.length / props.pageSize) - 1

  if (currentPage.value < maxPage) {
    // 缓存内切换
    currentPage.value++
  } else {
    // 缓存用完，重新请求
    await fetchHotArticles()
  }
}

const goToDetail = (id: string) => {
  router.push(`/articles/${id}`)
}

onMounted(() => {
  fetchHotArticles()
})
</script>

<style scoped>
.popular {
  display: flex;
  flex-direction: column;
}

.popular__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-xs);
}

.panel-label {
  font-size: var(--text-xs);
  letter-spacing: 0.08em;
  color: var(--color-ink-4);
}

.popular__refresh {
  font-size: var(--text-xs);
  color: var(--color-ink-3);
  white-space: nowrap;
  transition: color var(--dur-micro) var(--ease-out);
}

.popular__refresh:hover:not(:disabled) {
  color: var(--color-accent);
}

.popular__list {
  display: flex;
  flex-direction: column;
}

.popular__item {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
  padding-block: var(--space-2xs);
  cursor: pointer;
  min-width: 0;
}

/* 去掉条目间发丝线并统一降色：发丝线列表是中栏的语言，右栏再叠一层会平分权重 */
.popular__rank {
  font-size: var(--text-xs);
  color: var(--color-ink-4);
  min-width: 1.25em;
  text-align: center;
}

.popular__title {
  flex: 1;
  min-width: 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-ink-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color var(--dur-micro) var(--ease-out);
}

.popular__item:hover .popular__title {
  color: var(--color-accent);
}

.popular-fade-enter-active,
.popular-fade-leave-active {
  transition: opacity 150ms var(--ease-out);
}

.popular-fade-enter-from,
.popular-fade-leave-to {
  opacity: 0;
}
</style>
