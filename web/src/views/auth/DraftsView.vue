<template>
  <div class="drafts-view">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">草稿箱</h1>
        <p class="page-subtitle">继续你的创作之旅，共有 {{ total }} 篇草稿</p>
      </div>
      <el-button type="danger" plain @click="handleClearAll">
        <el-icon><Delete /></el-icon> 清空草稿箱
      </el-button>
    </div>

    <!-- 筛选区域 -->
    <div class="filter-section">
      <el-input
        v-model="queryParams.keyword"
        placeholder="搜索草稿标题..."
        clearable
        class="search-input"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <div class="filter-group">
        <el-select v-model="queryParams.year" placeholder="年:不限" style="width: 120px">
          <el-option label="不限" :value="null" />
          <el-option v-for="y in years" :key="y" :label="`${y}年`" :value="y" />
        </el-select>
        <el-select v-model="queryParams.month" placeholder="月:不限" style="width: 100px">
          <el-option label="不限" :value="null" />
          <el-option v-for="m in 12" :key="m" :label="`${m}月`" :value="m" />
        </el-select>
        <el-button type="info" plain @click="clearFilters">
          <el-icon><RefreshRight /></el-icon> 重置
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="draft-list-container">
      <div v-if="drafts.length > 0" class="draft-list">
        <el-card v-for="draft in drafts" :key="draft.id" class="draft-card" shadow="hover">
          <div class="draft-content">
            <div class="draft-info">
              <div class="draft-cover" @click="editDraft(draft.id)">
                <el-image
                  fit="cover"
                  lazy
                  :src="
                    draft.cover || 'http://localhost:9005/cloud-blog/articles/default-cover.png'
                  "
                >
                  <template #error>
                    <div class="image-placeholder">
                      <el-icon><Picture /></el-icon>
                    </div>
                  </template>
                </el-image>
              </div>
              <div class="draft-main">
                <h3 class="draft-title" @click="editDraft(draft.id)">
                  {{ draft.title || '无标题草稿' }}
                </h3>
                <div class="draft-meta">
                  <el-tag
                    :type="getStatusType(draft.status)"
                    effect="light"
                    size="small"
                    class="meta-item"
                    style="border-radius: 4px; margin: 0 0 4px 0"
                  >
                    {{ getStatusLabel(draft.status) }}
                  </el-tag>
                </div>
                <div class="draft-meta">
                  <span class="meta-item">
                    <el-icon><Calendar /></el-icon> 最后修改：{{ draft.updatedAt }}
                  </span>
                  <el-divider direction="vertical" />
                  <span class="meta-item">
                    <el-icon><Folder /></el-icon> {{ draft.category.name || '未分类' }}
                  </span>
                </div>
              </div>
            </div>
            <div class="draft-actions">
              <el-button link type="primary" @click="editDraft(draft.id)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-button link type="danger" @click="confirmDelete(draft.id)">
                <el-icon><Delete /></el-icon> 删除
              </el-button>
            </div>
          </div>
        </el-card>

        <div class="pagination">
          <el-pagination
            v-model:current-page="queryParams.pageNum"
            v-model:page-size="queryParams.pageSize"
            :page-sizes="[5, 10, 15, 20]"
            layout="total, sizes, prev, pager, next"
            :total="total"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </div>

      <el-empty v-else description="草稿箱空空如也" :image-size="200">
        <el-button type="primary" @click="$router.push('/editor')">去写文章</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  Calendar,
  Folder,
  Edit,
  Delete,
  RefreshRight,
  Picture,
} from '@element-plus/icons-vue'
import { getDrafts, deleteDraft, clearAllDrafts } from '@/api/article'
import type { PostVO, DraftListParams } from '@/api/types/article'
import { debounce } from 'lodash-es'

const router = useRouter()
const loading = ref(false)
const drafts = ref<PostVO[]>([])
const total = ref(0)

// 查询参数
const queryParams = reactive<DraftListParams>({
  pageNum: 1,
  pageSize: 10,
  year: null,
  month: null,
  keyword: null,
})

// 根据状态返回标签颜色
const getStatusType = (status: string) => {
  switch (status) {
    case 'draft':
      return 'warning' // 黄色
    case 'published':
      return 'success' // 绿色
    case 'scheduled':
      return 'info' // 蓝色
    default:
      return 'default' // 灰色
  }
}

// 根据状态返回中文
const getStatusLabel = (status: string) => {
  switch (status) {
    case 'draft':
      return '草稿'
    case 'published':
      return '已发布'
    case 'scheduled':
      return '定时发布'
    default:
      return '未知状态'
  }
}

// 生成年份选项（近5年）
const currentYear = new Date().getFullYear()
const years = Array.from({ length: 5 }, (_, i) => currentYear - i)

// 获取草稿列表
const fetchDrafts = async () => {
  loading.value = true
  try {
    // 构造请求参数，将空字符串转换为 null
    const params = {
      ...queryParams,
      year: queryParams.year === '' ? null : queryParams.year,
      month: queryParams.month === '' ? null : queryParams.month,
      keyword: queryParams.keyword === '' ? null : queryParams.keyword,
    }
    const { data } = await getDrafts(params)
    drafts.value = data.list
    total.value = data.total
  } catch (error) {
    console.error('获取草稿列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索、切换年月、切换页码、切换每页条数，都自动重新请求接口
watch(
  () => [queryParams.year, queryParams.month],
  () => {
    queryParams.pageNum = 1
    fetchDrafts()
  },
)
// 监听关键词变化 → 走防抖
watch(
  () => queryParams.keyword,
  () => {
    handleSearch()
  },
)

const handleSearch = debounce(() => {
  queryParams.pageNum = 1
  fetchDrafts()
}, 800)

const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  fetchDrafts()
}

const handlePageChange = (val: number) => {
  queryParams.pageNum = val
  fetchDrafts()
}

const clearFilters = () => {
  queryParams.year = null
  queryParams.month = null
  queryParams.keyword = null
  queryParams.pageNum = 1
  fetchDrafts()
}

onMounted(() => {
  fetchDrafts()
})

const editDraft = (id: number) => {
  router.push(`/editor/${id}`)
}

const handleClearAll = () => {
  ElMessageBox.confirm('确定要清空草稿箱吗？此操作不可恢复！', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'error',
  }).then(async () => {
    try {
      await clearAllDrafts()
      ElMessage.success('草稿箱已清空')
      fetchDrafts()
    } catch (error) {
      console.error('清空草稿箱失败:', error)
    }
  })
}

const confirmDelete = (id: number) => {
  ElMessageBox.confirm('确定要删除这篇草稿吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await deleteDraft(id)
      ElMessage.success('草稿已删除')
      // 删除后自动刷新
      fetchDrafts()
    } catch (error) {
      console.error('删除草稿失败:', error)
    }
  })
}
</script>

<style scoped lang="scss">
@use '../../styles/variables.scss' as *;

.drafts-view {
  padding-bottom: $spacing-xl;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-xl;

  .page-title {
    font-size: 32px;
    font-weight: 800;
    margin: 0 0 $spacing-xs;
  }
  .page-subtitle {
    color: $text-secondary;
    font-size: 14px;
    margin: 0;
  }
}

.filter-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-xl;
  gap: $spacing-md;
  flex-wrap: wrap;

  .search-input {
    width: 300px;
  }

  .filter-group {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }
}

.draft-list-container {
  min-height: 400px;
}

.draft-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.draft-card {
  border-radius: $border-radius-large;
  border: 1px solid $border-color;
  transition: all 0.3s;

  &:hover {
    border-color: $primary-color;
    .draft-title {
      color: $primary-color;
    }
  }

  .draft-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .draft-info {
    display: flex;
    align-items: center;
    gap: $spacing-lg;
    flex: 1;

    .draft-cover {
      width: 120px;
      height: 80px;
      border-radius: $border-radius-base;
      overflow: hidden;
      cursor: pointer;
      flex-shrink: 0;

      .el-image {
        width: 100%;
        height: 100%;
        transition: transform 0.3s;
      }

      &:hover .el-image {
        transform: scale(1.1);
      }

      .image-placeholder {
        width: 100%;
        height: 100%;
        background-color: $bg-color;
        display: flex;
        justify-content: center;
        align-items: center;
        color: $text-placeholder;
        font-size: 24px;
      }
    }
  }

  .draft-main {
    .draft-title {
      font-size: 18px;
      font-weight: 600;
      margin: 0 0 $spacing-sm;
      cursor: pointer;
      transition: color 0.3s;
    }
    .draft-meta {
      font-size: 13px;
      color: $text-secondary;
      display: flex;
      align-items: center;
      gap: 4px;

      .meta-item {
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }
  }

  .draft-actions {
    display: flex;
    gap: $spacing-md;
  }
}

.pagination {
  margin-top: $spacing-xl;
  display: flex;
  justify-content: center;
}
</style>
