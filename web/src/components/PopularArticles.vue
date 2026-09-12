<template>
  <el-card class="popular-card" shadow="never">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <el-icon><TrendCharts /></el-icon>
          <span>热门文章</span>
        </div>
        <el-button
          link
          type="primary"
          class="refresh-btn"
          :loading="loading"
          @click="handleRefresh"
        >
          <template #icon>
            <el-icon :class="{ 'is-refreshing': isRotating }"><Refresh /></el-icon>
          </template>
          换一换
        </el-button>
      </div>
    </template>

    <div class="popular-list-wrapper">
      <Transition name="fade-slide" mode="out-in">
        <div :key="currentPage" class="popular-list">
          <div
            v-for="(item, index) in displayArticles"
            :key="item.id"
            class="popular-item"
            @click="goToDetail(item.id)"
          >
            <span class="rank" :class="{ 'top-three': getGlobalIndex(index) < 3 }">
              {{ getGlobalIndex(index) + 1 }}
            </span>
            <span class="title" :title="item.title">{{ item.title }}</span>
          </div>
        </div>
      </Transition>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { TrendCharts, Refresh } from '@element-plus/icons-vue'
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
const isRotating = ref(false)
const articlePool = ref<HotArticle[]>([])
const currentPage = ref(0)

// 计算当前展示的文章
const displayArticles = computed(() => {
  if (articlePool.value.length === 0) return []
  const start = currentPage.value * props.pageSize
  return articlePool.value.slice(start, start + props.pageSize)
})

// 获取全局排名
const getGlobalIndex = (index: number) => {
  return currentPage.value * props.pageSize + index
}

// 获取热门文章数据
const fetchHotArticles = async () => {
  loading.value = true
  try {
    const { data } = await getHotArticles({ limit: props.poolSize })
    articlePool.value = data
    currentPage.value = 0
  } catch (error) {
    console.error('Failed to fetch hot articles:', error)
  } finally {
    loading.value = false
  }
}

// 换一换逻辑
const handleRefresh = async () => {
  if (loading.value) return

  isRotating.value = true
  // 动画持续一段时间
  setTimeout(() => {
    isRotating.value = false
  }, 600)

  const maxPage = Math.ceil(articlePool.value.length / props.pageSize) - 1

  if (currentPage.value < maxPage) {
    // 缓存内切换
    currentPage.value++
  } else {
    // 缓存用完，重新请求
    await fetchHotArticles()
  }
}

const goToDetail = (id: number) => {
  router.push(`/article/${id}`)
}

onMounted(() => {
  fetchHotArticles()
})
</script>

<style scoped lang="scss">
@use '../styles/variables.scss' as *;

.popular-card {
  border-radius: $border-radius-large;
  border: 1px solid $border-color;
  box-shadow: $box-shadow-light;
  overflow: hidden;

  :deep(.el-card__header) {
    padding: $spacing-md $spacing-lg;
    border-bottom: 1px solid rgba($border-color, 0.5);
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-left {
      display: flex;
      align-items: center;
      gap: $spacing-sm;
      font-weight: 700;
      font-size: 15px;
      color: $text-primary;

      .el-icon {
        color: $primary-color;
      }
    }

    .refresh-btn {
      font-size: 13px;
      font-weight: normal;
      padding: 0;

      .el-icon {
        margin-right: 4px;
        transition: transform 0.6s ease-in-out;
      }

      .is-refreshing {
        transform: rotate(360deg);
      }
    }
  }
}

.popular-list-wrapper {
  min-height: 200px; // 保持高度稳定
}

.popular-list {
  display: flex;
  flex-direction: column;

  .popular-item {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    cursor: pointer;
    padding: $spacing-sm 0px;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    border-radius: $border-radius-base;
    margin: 2px $spacing-xs;

    &:hover {
      background-color: rgba($primary-color, 0.05);
      transform: translateX(4px);

      .title {
        color: $primary-color;
      }
    }

    .rank {
      font-size: 14px;
      font-weight: 700;
      color: $text-placeholder;
      width: 20px;
      text-align: center;
      font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;

      &.top-three {
        color: #f56c6c; // 使用更醒目的红色
        font-style: italic;
        font-size: 16px;
      }

      // 前三名不同的颜色梯度（可选）
      &:nth-child(1).top-three {
        color: #ff4d4f;
      }
      &:nth-child(2).top-three {
        color: #ff7875;
      }
      &:nth-child(3).top-three {
        color: #ffa39e;
      }
    }

    .title {
      flex: 1;
      font-size: 14px;
      color: $text-regular;
      transition: color 0.3s;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      line-height: 1.5;
    }
  }
}

// 动画：淡入淡出 + 位移
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}

// 适配移动端
@media (max-width: 850px) {
  .popular-card {
    display: none;
  }
}
</style>
