<template>
  <div class="home-container">
    <div class="home-layout">
      <!-- 左侧：分类导航 (Sticky) -->
      <aside class="aside-left">
        <nav class="category-nav">
          <div class="category-list">
            <div
              class="category-item"
              :class="{ active: !currentCategoryId }"
              @click="handleCategorySelect(undefined)"
            >
              <span class="category-name">全部文章</span>
            </div>
            <div
              v-for="cat in categories"
              :key="cat.id"
              class="category-item"
              :class="{ active: currentCategoryId === cat.id }"
              @click="handleCategorySelect(cat.id)"
            >
              <span class="category-name">{{ cat.name }}</span>
            </div>
          </div>
        </nav>
      </aside>

      <!-- 中间：文章列表 -->
      <main class="main-content">
        <div v-loading="loading" class="article-section">
          <!-- 筛选信息头部 -->
          <div v-if="filterInfo" class="filter-header">
            <div class="filter-text">
              <span class="filter-label">{{ filterInfo.type }}：</span>
              <span class="filter-value">{{ filterInfo.value }}</span>
              <span class="result-count">共找到 {{ total }} 篇相关文章</span>
            </div>
            <div class="filter-actions">
              <el-select
                v-model="sortBy"
                placeholder="排序方式"
                size="small"
                style="width: 120px; margin-right: 15px"
              >
                <el-option label="最新发布" value="latest" />
                <el-option label="最多阅读" value="views" />
              </el-select>
              <el-button link type="primary" @click="clearFilter">
                清除筛选 <el-icon class="el-icon--right"><Close /></el-icon>
              </el-button>
            </div>
          </div>

          <!-- 文章列表 -->
          <div v-if="articles.length > 0" class="article-list">
            <ArticleCard v-for="article in articles" :key="article.id" :article="article" />

            <!-- 分页 -->
            <div class="pagination-container">
              <el-pagination
                v-model:current-page="currentPage"
                v-model:page-size="pageSize"
                :page-sizes="[10, 30, 50, 100]"
                layout="total, sizes, prev, pager, next"
                :total="total"
                @current-change="handlePageChange"
                @size-change="handlePageSizeChange"
              />
            </div>
          </div>

          <!-- 空状态 -->
          <el-empty v-else description="未找到相关内容" :image-size="200" class="empty-results">
            <el-button type="primary" @click="clearFilter">查看全部文章</el-button>
          </el-empty>
        </div>
      </main>

      <!-- 右侧：侧边栏 -->
      <aside class="aside-right">
        <div class="sidebar-sticky-wrapper">
          <!-- 用户个人资料 -->
          <el-card class="sidebar-card profile-card" shadow="never">
            <template v-if="isLoggedIn">
              <div class="profile-header">
                <el-avatar :size="64" :src="formatMinioUrl(user.avatar)" />
                <h3>{{ user.username || 'Ayun' }}</h3>
                <p>{{ user.bio }}</p>
              </div>
              <div class="profile-stats">
                <div class="stat-item">
                  <span class="count">{{ user.articlesCount || 0 }}</span>
                  <span class="label">文章</span>
                </div>
                <div class="stat-item">
                  <span class="count">{{ user.likesCount || 0 }}</span>
                  <span class="label">获赞</span>
                </div>
                <div class="stat-item">
                  <span class="count">{{ user.followersCount || 0 }}</span>
                  <span class="label">粉丝</span>
                </div>
              </div>
              <el-button type="primary" class="action-btn" plain @click="$router.push('/profile')"
                >进入主页</el-button
              >
            </template>
            <template v-else>
              <div class="login-prompt">
                <el-icon class="prompt-icon"><UserFilled /></el-icon>
                <h3>欢迎来到 CloudLog</h3>
                <p>登录后可发表评论、收藏文章和管理个人内容</p>
                <div class="prompt-actions">
                  <el-button type="primary" @click="$router.push('/login')">立即登录</el-button>
                  <el-button @click="$router.push('/register')">注册账号</el-button>
                </div>
              </div>
            </template>
          </el-card>

          <!-- 热门文章 -->
          <PopularArticles />

          <!-- 全站统计 -->
          <el-card class="sidebar-card stats-card" shadow="never">
            <template #header>
              <div class="card-header">
                <el-icon><DataLine /></el-icon>
                <span>全站统计</span>
              </div>
            </template>
            <div class="stats-grid">
              <div class="stats-item">
                <span class="label">文章总数</span>
                <span class="value">128</span>
              </div>
              <div class="stats-item">
                <span class="label">运行天数</span>
                <span class="value">452</span>
              </div>
              <div class="stats-item">
                <span class="label">访问总量</span>
                <span class="value">12.5k</span>
              </div>
            </div>
          </el-card>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getArticles } from '@/api/article'
import { getCategories } from '@/api/category'
import type { Article } from '@/api/types/article'
import type { Category } from '@/api/types/category'
import ArticleCard from '@/components/ArticleCard.vue'
import PopularArticles from '@/components/PopularArticles.vue'
import { getUserProfile, type UserProfile } from '@/api/user'
import { formatMinioUrl } from '@/config'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const articles = ref<Article[]>([])
const categories = ref<Category[]>([])
const loading = ref(false)
const isLoggedIn = computed(() => !!userStore.token)
const sortBy = ref('latest')

const user = reactive<UserProfile>({
  username: '',
  email: '',
  avatar: '',
  bio: '',
})

const currentCategoryId = computed(() =>
  route.query.categoryId ? Number(route.query.categoryId) : undefined,
)

const filterInfo = computed(() => {
  if (route.query.q) return { type: '搜索', value: route.query.q as string }
  if (route.query.category) return { type: '分类', value: route.query.category as string }
  if (route.query.tag) return { type: '标签', value: route.query.tag as string }
  if (currentCategoryId.value) {
    const cat = categories.value.find((c) => c.id === currentCategoryId.value)
    if (cat) return { type: '分类', value: cat.name }
  }
  return null
})

const clearFilter = () => {
  router.push({ name: 'Home' })
}

// 获取用户信息
const fetchProfile = async () => {
  try {
    const res = await getUserProfile()
    if (res.code === 200 && res.data) {
      Object.assign(user, res.data)
    }
  } catch (error) {
    console.error('Failed to fetch profile:', error)
  }
}

const fetchCategories = async () => {
  try {
    const { data } = await getCategories()
    categories.value = data
  } catch (error) {
    console.error('Failed to fetch categories:', error)
  }
}

const fetchArticles = async () => {
  loading.value = true
  try {
    const params: any = {
      page: currentPage.value,
      pageSize: pageSize.value,
      keyword: route.query.q as string,
      categoryId: currentCategoryId.value,
      tagId: route.query.tagId,
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

const handleCategorySelect = (id?: number) => {
  const query: any = { ...route.query }
  // 切换分类时清除搜索和其他筛选
  delete query.q
  delete query.tagId
  delete query.tag
  delete query.category

  if (id) {
    query.categoryId = id
  } else {
    delete query.categoryId
  }
  currentPage.value = 1
  router.push({ name: 'Home', query })
}

onMounted(() => {
  fetchCategories()
  fetchArticles()
  fetchProfile()
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

const handlePageSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchArticles()
}
</script>

<style scoped lang="scss">
@use '../../styles/variables.scss' as *;

.home-container {
  // 确保没有父级容器限制 sticky
  width: 100%;
}

.home-layout {
  display: flex;
  gap: $spacing-lg;
  align-items: flex-start; // 关键：防止侧边栏拉伸到 100% 高度，从而让 sticky 生效
  position: relative;
}

// 左侧分类导航
.aside-left {
  min-width: 180px;
  flex-shrink: 0;
  position: sticky;
  top: 88px; // 距离顶部的距离 (Header 64px + 20px 间距)
  z-index: 10;

  @media (max-width: 1100px) {
    display: none;
  }

  .category-nav {
    background: $white;
    border-radius: $border-radius-large;
    border: 1px solid $border-color;
    box-shadow: $box-shadow-light;
    display: flex;
    flex-direction: column;
    max-height: calc(100vh - 120px); // 限制高度以支持内部滚动

    .category-list {
      overflow-y: auto;
      padding: $spacing-sm 0;

      &::-webkit-scrollbar {
        width: 4px;
      }
      &::-webkit-scrollbar-thumb {
        background: rgba($text-placeholder, 0.2);
        border-radius: 2px;
      }

      .category-item {
        padding: 10px $spacing-lg;
        cursor: pointer;
        transition: all 0.2s;
        font-size: 14px;
        color: $text-regular;
        margin: 2px $spacing-sm;
        border-radius: $border-radius-base;

        &:hover {
          background-color: rgba($primary-color, 0.05);
          color: $primary-color;
        }

        &.active {
          background-color: rgba($primary-color, 0.1);
          color: $primary-color;
          font-weight: 600;
        }

        .category-name {
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }
    }
  }
}

// 中间主内容
.main-content {
  flex: 1;
  min-width: 0; // 防止内容溢出

  .article-section {
    display: flex;
    flex-direction: column;
    gap: $spacing-md;
  }

  .filter-header {
    background: $white;
    padding: $spacing-md $spacing-lg;
    border-radius: $border-radius-large;
    border: 1px solid $border-color;
    display: flex;
    justify-content: space-between;
    align-items: center;
    box-shadow: $box-shadow-light;

    .filter-text {
      display: flex;
      align-items: center;
      gap: $spacing-xs;

      .filter-label {
        color: $text-secondary;
        font-size: 14px;
      }
      .filter-value {
        font-weight: 700;
        font-size: 18px;
        color: $primary-color;
        margin-right: $spacing-md;
      }
      .result-count {
        font-size: 13px;
        color: $text-placeholder;
      }
    }
  }

  .article-list {
    display: flex;
    flex-direction: column;
    gap: $spacing-md;
  }

  .pagination-container {
    margin-top: $spacing-md;
    display: flex;
    justify-content: center;
    background: $white;
    padding: $spacing-md;
    border-radius: $border-radius-large;
    border: 1px solid $border-color;
  }

  .empty-results {
    background: $white;
    border-radius: $border-radius-large;
    padding: $spacing-xl * 2 0;
  }
}

// 右侧侧边栏
.aside-right {
  width: 260px;
  flex-shrink: 0;

  @media (max-width: 850px) {
    display: none; // 手机端隐藏
  }

  .sidebar-sticky-wrapper {
    display: flex;
    flex-direction: column;
    gap: $spacing-lg;
    // 右侧如果也想要 sticky，可以取消下面注释
    position: sticky;
    top: 80px;
  }
}

.sidebar-card {
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
    align-items: center;
    gap: $spacing-sm;
    font-weight: 700;
    font-size: 15px;
    color: $text-primary;
    .el-icon {
      color: $primary-color;
    }
  }
}

.profile-card {
  text-align: center;
  .profile-header {
    margin-bottom: $spacing-sm;
    h3 {
      margin: $spacing-sm 0 4px;
      font-size: 18px;
      color: $text-primary;
    }
    p {
      font-size: 13px;
      color: $text-secondary;
      margin: 0;
    }
  }
  .profile-stats {
    display: flex;
    justify-content: space-around;
    margin-bottom: $spacing-sm;
    padding: 0 $spacing-sm;
    .stat-item {
      display: flex;
      flex-direction: column;
      .count {
        font-weight: 700;
        font-size: 16px;
        color: $text-primary;
      }
      .label {
        font-size: 12px;
        color: $text-secondary;
        margin-top: 2px;
      }
    }
  }
  .action-btn {
    width: calc(100% - 40px);
    margin-bottom: $spacing-sm;
  }
}

.login-prompt {
  padding: $spacing-md 0;
  .prompt-icon {
    font-size: 48px;
    color: $text-placeholder;
    margin-bottom: $spacing-sm;
    opacity: 0.5;
  }
  h3 {
    margin: 0 0 $spacing-xs;
    font-size: 16px;
    color: $text-primary;
  }
  p {
    font-size: 13px;
    color: $text-secondary;
    margin-bottom: $spacing-lg;
    padding: 0 $spacing-md;
  }
  .prompt-actions {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
    padding: 0 $spacing-lg;
    .el-button {
      margin: 0;
      width: 100%;
    }
  }
}

.stats-grid {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  padding: $spacing-sm $spacing-lg;

  .stats-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;
    .label {
      color: $text-secondary;
    }
    .value {
      font-weight: 600;
      color: $text-primary;
    }
  }
}

// 暗色模式微调 (如果项目支持)
:deep(.html.dark) {
  .aside-left .category-nav,
  .main-content .filter-header,
  .main-content .pagination-container,
  .sidebar-card {
    background-color: #1d1e1f;
    border-color: #363637;
  }
}
</style>
