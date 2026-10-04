<template>
  <section class="popular" aria-label="热门文章">
    <header class="popular__head">
      <p class="panel-label">热门文章</p>
      <!-- 池子不足一页时「换一换」点了不会有任何变化（重拉回来还是那几条），那一句就说「刷新」 -->
      <button class="popular__refresh" :disabled="loading" @click="handleRefresh">
        {{ canRotate ? '换一换' : '刷新' }}
      </button>
    </header>

    <Transition name="popular-fade" mode="out-in">
      <ol v-if="displayArticles.length" :key="currentPage" class="popular__list">
        <li
          v-for="(item, index) in displayArticles"
          :key="item.id"
          class="popular__item"
          @click="goToDetail(item.id)"
        >
          <span class="popular__rank tnum">{{ getGlobalIndex(index) + 1 }}</span>
          <!-- 真链接：整行 @click 只有鼠标能走，Tab 到不了、中键/Ctrl+点击也开不了新标签 -->
          <RouterLink class="popular__title" :to="`/articles/${item.id}`" :title="item.title">
            {{ item.title }}
          </RouterLink>
        </li>
      </ol>
    </Transition>

    <p v-if="loadFailed" class="popular__empty">
      热榜没有加载出来。
      <button type="button" class="popular__retry" @click="fetchHotArticles">重试</button>
    </p>
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
/** 拉失败要单独说：热榜取不到时屏幕上只剩一个标题，读起来像「这位作者没有热文」 */
const loadFailed = ref(false)

/** 池子里是否还有下一页可换 */
const canRotate = computed(() => articlePool.value.length > props.pageSize)

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
  loadFailed.value = false
  try {
    const { data } = await getHotArticles({ limit: props.poolSize })
    articlePool.value = data
    currentPage.value = 0
  } catch {
    // 失败提示由响应拦截器统一弹出，这里只记下故障，让空位上能写一句真话
    loadFailed.value = true
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
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color var(--dur-micro) var(--ease-out);
}

.popular__item:hover .popular__title {
  color: var(--color-accent);
}

/* 拉失败时占住列表的位置：右栏这里空着，读起来会像「还没有热文」而不是「没拉到」 */
.popular__empty {
  padding-block: var(--space-2xs);
  font-size: var(--text-sm);
  color: var(--color-ink-4);
}

.popular__retry {
  border: 0;
  padding: 0;
  background: none;
  font-size: inherit;
  color: var(--color-accent-ink);
  cursor: pointer;
}

.popular__retry:hover {
  text-decoration: underline;
  text-underline-offset: 0.25em;
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
