<template>
  <div class="articles admin-page">
    <header class="admin-page__head">
      <div>
        <h1 class="admin-page__title">文章管理</h1>
        <p class="admin-page__subtitle">管理全站文章，共 {{ total }} 篇</p>
      </div>
    </header>

    <!-- 筛选区 -->
    <div class="admin-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索文章标题…"
        clearable
        class="admin-toolbar__search"
      >
        <template #prefix>
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true">
            <path
              d="m10.5 10.5 3 3M7 11.5a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9Z"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
            />
          </svg>
        </template>
      </el-input>

      <el-select
        v-model="query.status"
        placeholder="全部状态"
        clearable
        class="admin-toolbar__select"
      >
        <el-option label="已发布" value="published" />
        <el-option label="草稿" value="draft" />
        <el-option label="定时发布" value="scheduled" />
        <el-option label="已下线" value="offline" />
      </el-select>

      <el-select
        v-model="query.authorId"
        placeholder="全部作者"
        clearable
        filterable
        class="admin-toolbar__select"
      >
        <el-option v-for="author in authors" :key="author.id" :label="author.username" :value="author.id" />
      </el-select>

      <el-select
        v-model="query.categoryId"
        placeholder="全部分类"
        clearable
        class="admin-toolbar__select"
      >
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>

      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="–"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        class="admin-toolbar__date"
      />

      <button v-if="hasFilter" class="admin-toolbar__reset" @click="clearFilters">重置筛选</button>
    </div>

    <!-- 列表 -->
    <div class="admin-card">
      <div class="admin-card__body admin-card__body--flush">
        <el-table v-loading="loading" :data="articles" class="admin-table" row-key="id">
          <el-table-column label="标题" min-width="220">
            <template #default="{ row }">
              <span v-if="row.isTop" class="cell-pin">置顶</span>
              <a
                :href="`/articles/${row.id}`"
                target="_blank"
                rel="noopener"
                class="cell-title"
                :title="row.title"
                >{{ row.title }}</a
              >
            </template>
          </el-table-column>
          <el-table-column label="作者" min-width="120">
            <template #default="{ row }">
              <div class="cell-user">
                <el-avatar :size="20" :src="formatAvatar(row.author?.avatar)" />
                <span class="cell-user__name">{{ row.author?.username || '未知' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="分类" min-width="100">
            <template #default="{ row }">
              <span>{{ row.category?.name || '未分类' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="150">
            <template #default="{ row }">
              <span class="status-badge" :class="`status-badge--${statusTone(row.status)}`">
                <i class="status-badge__dot" aria-hidden="true"></i>{{ statusLabel(row.status) }}
              </span>
              <!-- 徽标只答得出「是什么状态」，答不出「是谁撤的」；留库不清空，但只在当前下线时显示 -->
              <p v-if="row.status === 'offline' && row.offlinedByName" class="cell-offline-by">
                由 {{ row.offlinedByName }} 于 {{ formatTime(row.offlinedAt) }} 下线
              </p>
            </template>
          </el-table-column>
          <el-table-column label="发布时间" width="160">
            <template #default="{ row }">
              <span v-if="row.publishedAt" class="tnum">{{ formatTime(row.publishedAt) }}</span>
              <span v-else class="cell-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="160">
            <template #default="{ row }">
              <span class="tnum">{{ formatTime(row.updatedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <a :href="`/articles/${row.id}`" target="_blank" rel="noopener" class="row-action">查看</a>
              <button class="row-action" @click="openSettings(row)">设置</button>
              <button class="row-action row-action--danger" @click="confirmDelete(row)">删除</button>
            </template>
          </el-table-column>

          <template #empty>
            <div class="empty-state">
              <p class="empty-state__title">{{ emptyTitle }}</p>
              <button v-if="hasFilter" class="empty-state__action" @click="clearFilters">重置筛选</button>
              <p v-else class="empty-state__hint">去前台写第一篇文章吧。</p>
            </div>
          </template>
        </el-table>
      </div>

      <div v-if="total > query.pageSize" class="admin-pagination">
        <el-pagination
          v-model:current-page="query.page"
          :page-size="query.pageSize"
          layout="total, prev, pager, next"
          :total="total"
          @current-change="fetchArticles"
        />
      </div>
    </div>

    <!-- 行内「设置」：置顶、排序权重、分类与上下线都在后台改完，不必进写作编辑器 -->
    <el-drawer
      v-model="settings.visible"
      :title="`文章设置 · ${settings.title}`"
      size="min(27.5rem, 88%)"
    >
      <el-form label-position="top" @submit.prevent="saveSettings">
        <el-form-item label="分类">
          <!-- 不给 clearable：PUT 的部分更新语义会跳过 null，清空分类在这台后端上根本存不下 -->
          <el-select v-model="settings.categoryId" placeholder="未分类" class="settings-field">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="置顶">
          <el-switch v-model="settings.isTop" />
        </el-form-item>

        <!-- 权重只在置顶时有意义：不置顶的行没有先后可言 -->
        <el-form-item v-if="settings.isTop" label="排序权重">
          <el-input-number
            v-model="settings.topOrder"
            :min="0"
            :max="999"
            :step="1"
            controls-position="right"
          />
          <p class="settings-hint">多篇置顶文章按权重从大到小排列，同权重按发布时间倒序</p>
        </el-form-item>

        <el-form-item v-if="canSetVisibility" label="可见性">
          <el-radio-group v-model="settings.visibility">
            <el-radio value="published">已发布</el-radio>
            <el-radio value="offline">已下线</el-radio>
          </el-radio-group>
          <p class="settings-hint">下线只收回可见性：正文、分类与首发时间都保留，随时可重新上线</p>
        </el-form-item>

        <!-- 草稿与定时没有「读者能看到的版本」可收回；定时要改时刻只能进编辑器 -->
        <p v-else class="settings-hint">
          当前为{{ statusLabel(settings.status) }}，上下线只在已发布与已下线之间切换；改正文或定时时刻请进编辑器。
        </p>

        <!-- 留痕在库里不清空，所以一篇被放回去的文章在这里仍然看得见是谁撤的 -->
        <p v-if="settings.offlinedAt" class="settings-hint">
          由 {{ settings.offlinedByName }} 于 {{ formatTime(settings.offlinedAt) }} 下线<template
            v-if="settings.status !== 'offline'">（此后已重新上线）</template>
        </p>
      </el-form>

      <template #footer>
        <el-button @click="settings.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSettings">确定</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteAdminArticle, getAdminArticles, getAdminAuthors, getAdminCategories } from '@/api/admin'
import { updateArticle } from '@/api/article'
import type {
  AdminArticleItem,
  AdminArticleQuery,
  AdminAuthor,
  AdminCategoryItem,
  ArticleStatus,
} from '@/api/types/admin'
import { formatMinioUrl, defaultAvatarUrl } from '@/config'
import { useUrlFilters } from '@/composables/useUrlFilters'
import dayjs from 'dayjs'

const loading = ref(false)
const articles = ref<AdminArticleItem[]>([])
const total = ref(0)
const authors = ref<AdminAuthor[]>([])
const categories = ref<AdminCategoryItem[]>([])

const query = reactive<AdminArticleQuery>({
  page: 1,
  pageSize: 15,
  keyword: null,
  status: null,
  authorId: null,
  categoryId: null,
  startDate: null,
  endDate: null,
})

/** 日期区间直接落在 query 那两个字段上（原先是另一个 ref，取数时才拼回去）：
 *  只有进得了 query 的条件才进得了地址栏，否则分享出去的链接少一半条件 */
const dateRange = computed({
  get: (): [string, string] | null =>
    query.startDate && query.endDate
      ? ([query.startDate, query.endDate] as [string, string])
      : null,
  set: (range: [string, string] | null) => {
    query.startDate = range?.[0] ?? null
    query.endDate = range?.[1] ?? null
  },
})

useUrlFilters(query)

const hasFilter = computed(
  () => !!(query.keyword || query.status || query.authorId || query.categoryId || dateRange.value),
)

const emptyTitle = computed(() => (hasFilter.value ? '没有符合条件的文章。' : '还没有任何文章。'))

const fetchArticles = async () => {
  loading.value = true
  try {
    const { data } = await getAdminArticles(query)
    articles.value = data.list
    total.value = Number(data.total) // PageResult.total 以字符串下发（Long 统一转字符串），分页器要 number
  } catch (error) {
    console.error('获取文章列表失败:', error) // 提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

// 关键词输入走轻量防抖；其余筛选变化立即查询并回到第一页
let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => query.keyword,
  () => {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      query.page = 1
      fetchArticles()
    }, 500)
  },
)

watch(
  () => [query.status, query.authorId, query.categoryId, dateRange.value],
  () => {
    query.page = 1
    fetchArticles()
  },
)

const clearFilters = () => {
  query.keyword = null
  query.status = null
  query.authorId = null
  query.categoryId = null
  dateRange.value = null
  query.page = 1
  fetchArticles()
}

const confirmDelete = (row: AdminArticleItem) => {
  ElMessageBox.confirm(
    // 连带范围按后端 cascadeDelete 的实删清单说：它还清点赞、并把封面与正文配图的对象回收掉，
    // 而回收不看这张图有没有被别的文章引用（ArticleService.java:271-281）
    `确定要删除文章「${row.title}」吗？其评论、标签关联与点赞记录一并清除；` +
      '本文用到的封面与正文配图也会从对象存储删除，若同样的地址出现在别的文章里，那些图会一起失效。此操作不可恢复。',
    '删除文章',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'error' },
  ).then(async () => {
    try {
      await deleteAdminArticle(row.id)
      ElMessage.success('文章已删除')
      fetchArticles()
    } catch (error) {
      console.error('删除文章失败:', error)
    }
  })
}

/**
 * 行内设置：分类 / 置顶 / 排序权重 / 上下线，全部走 PUT /articles/{id} 的部分更新，不新增接口。
 * status 只在真的动了可见性时才带——不带即后端不改状态，也不碰 published_at。
 */
const settings = reactive({
  visible: false,
  id: '',
  title: '',
  status: 'draft' as ArticleStatus,
  categoryId: null as number | null,
  isTop: false,
  topOrder: 0,
  visibility: 'published' as ArticleStatus,
  /** 只读留痕：抽屉里显示，永不进提交载荷 */
  offlinedAt: null as string | null,
  offlinedByName: null as string | null,
})

const saving = ref(false)

/** 只有「已发布 ↔ 已下线」这一对是可见性开关；草稿与定时没有可收回的对外版本 */
const canSetVisibility = computed(
  () => settings.status === 'published' || settings.status === 'offline',
)

const openSettings = (row: AdminArticleItem) => {
  settings.id = row.id
  settings.title = row.title
  settings.status = row.status
  settings.categoryId = row.category?.id ?? null
  settings.isTop = row.isTop === 1
  settings.topOrder = row.topOrder ?? 0
  settings.visibility = row.status === 'offline' ? 'offline' : 'published'
  settings.offlinedAt = row.offlinedAt ?? null
  settings.offlinedByName = row.offlinedByName ?? null
  settings.visible = true
}

const saveSettings = async () => {
  const payload: Record<string, unknown> = {
    categoryId: settings.categoryId,
    isTop: settings.isTop ? 1 : 0,
    // 权重与开关解耦：取消置顶也照原值提交，后端留着它，重新置顶时沿用
    topOrder: settings.topOrder,
  }
  if (canSetVisibility.value && settings.visibility !== settings.status) {
    payload.status = settings.visibility
  }

  // 下线是「读者立刻看不到」，按下确定后再加一道闸；上线是恢复可见，不再盘问
  if (payload.status === 'offline') {
    try {
      await ElMessageBox.confirm('下线后读者将无法访问此文章，可随时重新上线', '下线文章', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }
  }

  saving.value = true
  try {
    await updateArticle(settings.id, payload)
    ElMessage.success('已保存')
    settings.visible = false
    fetchArticles()
  } catch (error) {
    console.error('保存文章设置失败:', error) // 提示由响应拦截器统一处理
  } finally {
    saving.value = false
  }
}

// 状态展示与前台草稿箱同一套语义
const STATUS_META: Record<ArticleStatus, { label: string; tone: string }> = {
  draft: { label: '草稿', tone: 'muted' },
  published: { label: '已发布', tone: 'success' },
  scheduled: { label: '定时发布', tone: 'warning' },
  offline: { label: '已下线', tone: 'danger' },
}

const statusLabel = (status: ArticleStatus) => STATUS_META[status]?.label ?? '未知状态'
const statusTone = (status: ArticleStatus) => STATUS_META[status]?.tone ?? 'muted'

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm')
const formatAvatar = (avatar?: string | null) => formatMinioUrl(avatar) || defaultAvatarUrl

const fetchFilters = async () => {
  try {
    const [authorRes, categoryRes] = await Promise.all([getAdminAuthors(), getAdminCategories()])
    authors.value = authorRes.data
    categories.value = categoryRes.data
  } catch (error) {
    console.error('获取筛选数据失败:', error)
  }
}

onMounted(() => {
  fetchArticles()
  fetchFilters()
})
</script>

<style scoped lang="scss">
.admin-toolbar__date {
  max-width: 100%;

  :deep(.el-range-editor) {
    height: 2rem;
  }
}

.row-action + .row-action {
  margin-left: var(--space-md);
}

/* 设置抽屉：分类下拉撑满一行，提示语紧跟其控件下方 */
.settings-field {
  width: 100%;
}

.settings-hint {
  margin-top: var(--space-2xs);
  font-size: var(--text-xs);
  color: var(--color-ink-4);
}

/* 置顶标识：与前台文章卡片的置顶徽标同款，朱砂洗染仅作点睛 */
.cell-pin {
  display: inline-block;
  margin-right: var(--space-xs);
  padding-inline: var(--space-2xs);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  font-weight: 400;
  line-height: 1.4;
  color: var(--color-accent);
  background-color: var(--color-accent-wash);
  border-radius: var(--radius-xs);
}
</style>
