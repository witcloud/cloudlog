<template>
  <div class="drafts">
    <!-- 页头：标题 + 计数，发丝线收底 -->
    <header class="drafts__head">
      <div class="drafts__heading">
        <h1 class="drafts__title">草稿箱</h1>
        <!-- 计数即筛选：没有定时发布时不摆三个点了没反应的把手，有才拆成视图 -->
        <p v-if="hasScheduled" class="drafts__views tnum">
          <button
            v-for="view in statusViews"
            :key="view.value ?? 'all'"
            type="button"
            class="drafts__view"
            :class="{ 'is-active': view.value === queryParams.status }"
            @click="queryParams.status = view.value"
          >
            {{ view.label }} {{ view.count }}
          </button>
        </p>
        <p v-else class="drafts__count tnum">共 {{ draftCount }} 篇草稿</p>
      </div>
      <!-- 清空的对象是整个抽屉，所以它的显隐跟未筛的计数走，不跟当前视图走 -->
      <button v-if="boxTotal > 0" class="drafts__clear" @click="handleClearAll">
        清空草稿箱
      </button>
    </header>

    <!-- 筛选：标题搜索 + 年月。工具条留在面板之外（页头 → 工具条 → 主体） -->
    <div class="drafts__toolbar">
      <el-input
        v-model="queryParams.keyword"
        placeholder="搜索草稿标题…"
        clearable
        class="drafts__search"
        @keyup.enter="handleSearch"
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

      <el-select v-model="queryParams.year" placeholder="年份" class="drafts__select">
        <el-option label="不限年份" :value="null" />
        <el-option v-for="y in years" :key="y" :label="`${y} 年`" :value="y" />
      </el-select>

      <el-select
        v-model="queryParams.month"
        placeholder="月份"
        class="drafts__select drafts__select--month"
      >
        <el-option label="不限月份" :value="null" />
        <el-option v-for="m in 12" :key="m" :label="`${m} 月`" :value="m" />
      </el-select>

      <button v-if="hasFilter" class="drafts__reset" @click="clearFilters">重置筛选</button>
    </div>

    <!-- 抬升面板：只包列表主体（卡片范式 docs/ui-design.md §6.2） -->
    <div class="drafts__panel">
      <div v-loading="loading || keywordStale" class="drafts__area">
        <!-- 草稿列表：与首页文章列表同一套发丝线行 -->
        <ul v-if="drafts.length > 0" class="draft-list">
          <li v-for="draft in drafts" :key="draft.id" class="draft-row">
            <div class="draft-row__main">
              <h2 class="draft-row__title" @click="editDraft(draft.id)">
                {{ draft.title || '无标题草稿' }}
              </h2>
              <p class="draft-row__meta">
                <span class="draft-status" :class="`draft-status--${statusTone(draft.status)}`">
                  <i class="draft-status__dot" aria-hidden="true"></i>{{ statusLabel(draft.status) }}
                </span>
                <span aria-hidden="true">·</span>
                <span>{{ draft.category?.name || '未分类' }}</span>
                <span aria-hidden="true">·</span>
                <!-- 排期的文章要看的是它几点上线，上次动笔的时间在这里没有意义 -->
                <time
                  v-if="draft.status === 'scheduled' && draft.publishedAt"
                  :datetime="draft.publishedAt"
                  class="tnum"
                >
                  计划 {{ formatTime(draft.publishedAt) }} 发布
                </time>
                <time v-else :datetime="draft.updatedAt" class="tnum">
                  最后修改 {{ formatTime(draft.updatedAt) }}
                </time>
              </p>
            </div>
            <div class="draft-row__actions">
              <button class="draft-action" @click="editDraft(draft.id)">继续编辑</button>
              <button class="draft-action draft-action--danger" @click="confirmDelete(draft)">
                删除
              </button>
            </div>
          </li>
        </ul>

        <div v-else-if="!loading" class="empty-state">
          <p class="empty-state__title">草稿箱还是空的。</p>
          <p class="empty-state__hint">写下第一篇文章，未完成的内容会保存在这里。</p>
          <button class="empty-state__action" @click="router.push('/editor')">去写文章</button>
        </div>

        <div v-if="total > queryParams.pageSize" class="pagination">
          <el-pagination
            v-model:current-page="queryParams.pageNum"
            :page-size="queryParams.pageSize"
            layout="prev, pager, next"
            :total="total"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDrafts, deleteDraft, clearAllDrafts } from '@/api/article'
import type { PostVO, DraftListParams } from '@/api/types/article'
import { debounce } from 'lodash-es'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
/** 打字到请求发出之间隔着 800ms 防抖，这段时间列表已经不代表当前条件了，先按加载中处理 */
const keywordStale = ref(false)
const drafts = ref<PostVO[]>([])
const total = ref(0)
// 分状态计数：后端按「不叠加状态」的那组谓词算，所以它们不随视图切换而变
const draftCount = ref(0)
const scheduledCount = ref(0)

/**
 * 整箱计数：不受 keyword/年月影响的那一组。
 * 上面两个是「计数即视图」的，跟着筛选走，服务视图把手；
 * 而清空删的是整箱——DELETE /drafts 不收筛选参数（后端 findDraftIdsByAuthor 只认作者与状态），
 * 拿筛出来的数去确认整箱清空，说的是删 2 篇、按下去删光全箱。
 * 取法：一次 pageSize=1 的无条件请求，只为要回那两个计数。
 */
const boxCounts = reactive({ draft: 0, scheduled: 0 })
const boxTotal = computed(() => boxCounts.draft + boxCounts.scheduled)

const fetchBoxCounts = async () => {
  try {
    const { data } = await getDrafts({ pageNum: 1, pageSize: 1 })
    boxCounts.draft = data.draftCount
    boxCounts.scheduled = data.scheduledCount
  } catch (error) {
    console.error('获取整箱计数失败:', error)
  }
}

// 查询参数
const queryParams = reactive<DraftListParams>({
  pageNum: 1,
  pageSize: 10,
  year: null,
  month: null,
  keyword: null,
  status: null,
})

type StatusView = { label: string; value: 'draft' | 'scheduled' | null; count: number }

const unpublishedTotal = computed(() => draftCount.value + scheduledCount.value)
const hasScheduled = computed(() => scheduledCount.value > 0)

// 「全部」取两个计数之和而不是 total，否则点中某个视图后全部会跟着缩成该视图的条数
const statusViews = computed<StatusView[]>(() => [
  { label: '全部', value: null, count: unpublishedTotal.value },
  { label: '草稿', value: 'draft', count: draftCount.value },
  { label: '定时', value: 'scheduled', count: scheduledCount.value },
])

// 状态 → 展示文案与色阶（muted/warning/success，强调色不参与状态）
const STATUS_META: Record<string, { label: string; tone: string }> = {
  draft: { label: '草稿', tone: 'muted' },
  published: { label: '已发布', tone: 'success' },
  scheduled: { label: '定时发布', tone: 'warning' },
}

const statusLabel = (status: string) => STATUS_META[status]?.label ?? '未知状态'
const statusTone = (status: string) => STATUS_META[status]?.tone ?? 'muted'

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm')

// 是否有任何筛选生效（决定「重置筛选」是否出现）
const hasFilter = computed(
  () => !!(queryParams.year || queryParams.month || queryParams.keyword || queryParams.status),
)

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
    total.value = Number(data.total) // PageResult.total 以字符串下发（Long 统一转字符串），分页器要 number
    draftCount.value = data.draftCount
    scheduledCount.value = data.scheduledCount
  } catch (error) {
    console.error('获取草稿列表失败:', error)
  } finally {
    loading.value = false
    keywordStale.value = false
  }
}

/** 重置一次改四个字段，下面三个 watcher 会各自去拉；这一段里让它们都闭嘴，只发一个请求 */
let resetting = false

// 搜索、切年月、切状态视图、切换页码，都自动重新请求接口
watch(
  () => [queryParams.year, queryParams.month, queryParams.status],
  () => {
    if (resetting) return
    queryParams.pageNum = 1
    fetchDrafts()
  },
)
// 监听关键词变化 → 走防抖
watch(
  () => queryParams.keyword,
  () => {
    if (resetting) return
    keywordStale.value = true
    handleSearch()
  },
)

const handleSearch = debounce(() => {
  queryParams.pageNum = 1
  fetchDrafts()
}, 800)

const handlePageChange = (val: number) => {
  queryParams.pageNum = val
  fetchDrafts()
}

const clearFilters = () => {
  resetting = true
  queryParams.year = null
  queryParams.month = null
  queryParams.keyword = null
  queryParams.status = null
  queryParams.pageNum = 1
  fetchDrafts()
  nextTick(() => {
    resetting = false
  })
}

onMounted(() => {
  fetchDrafts()
  fetchBoxCounts()
})

const editDraft = (id: string) => {
  router.push(`/editor/${id}`)
}

const handleClearAll = () => {
  // 数字取整箱计数，不取视图计数：列表筛过之后屏幕上只有 2 条，
  // 但这一枪删的是全箱，文案说 2 篇就是骗人
  const scope = hasFilter.value ? '（不按当前筛选，整箱全部删除）' : ''
  ElMessageBox.confirm(
    `确定要清空草稿箱吗？共 ${boxTotal.value} 篇（草稿 ${boxCounts.draft} · 定时 ${boxCounts.scheduled}）${scope}将被删除，此操作不可恢复！`,
    '警告',
    {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'error',
    },
  ).then(async () => {
    try {
      await clearAllDrafts()
      ElMessage.success('草稿箱已清空')
      fetchDrafts()
      fetchBoxCounts()
    } catch (error) {
      console.error('清空草稿箱失败:', error)
    }
  })
}

const confirmDelete = (draft: PostVO) => {
  ElMessageBox.confirm(`确定要删除「${draft.title || '无标题'}」吗？`, '删除草稿', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await deleteDraft(draft.id)
      ElMessage.success('草稿已删除')
      // 删除后自动刷新
      fetchDrafts()
      fetchBoxCounts()
    } catch (error) {
      console.error('删除草稿失败:', error)
    }
  })
}
</script>

<style scoped lang="scss">
.drafts {
  padding-bottom: var(--space-xl);
}

/* ---- 页头 ---- */
.drafts__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-md);
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--color-rule);
}

/* 计数并入标题行：小字落在标题基线上，不再单独占一行 */
.drafts__heading {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.drafts__title {
  margin: 0;
  font-size: var(--text-lg);
  color: var(--color-ink);
}

.drafts__count {
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

/* 状态视图：计数即筛选，当前项压一条 accent 下划线（与选项卡的指示同一套语汇） */
.drafts__views {
  display: flex;
  align-items: baseline;
  gap: var(--space-md);
  font-size: var(--text-sm);
}

.drafts__view {
  color: var(--color-ink-3);
  white-space: nowrap;
  border-bottom: 1px solid transparent;
  transition:
    color var(--dur-micro) var(--ease-out),
    border-color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-ink);
  }

  &.is-active {
    color: var(--color-ink);
    border-bottom-color: var(--color-accent);
  }
}

.drafts__clear {
  flex-shrink: 0;
  font-size: var(--text-sm);
  color: var(--color-ink-3);
  white-space: nowrap;
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-danger);
  }
}

/* ---- 抬升面板：只包列表主体，配方同首页 .article-area（§6.2 卡片范式） ---- */
.drafts__panel {
  margin-top: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
}

/* ---- 筛选条：在面板之外，与页头之间留一档，与面板之间由面板外边距承担 ---- */
.drafts__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-wrap: wrap;
  margin-top: var(--space-lg);
}

.drafts__search {
  width: 16rem;
  max-width: 100%;

  :deep(.el-input__prefix) {
    color: var(--color-ink-4);
  }
}

.drafts__select {
  width: 8.5rem;

  &--month {
    width: 7.5rem;
  }
}

.drafts__reset {
  font-size: var(--text-sm);
  color: var(--color-ink-3);
  white-space: nowrap;

  &:hover {
    color: var(--color-accent);
  }
}

/* ---- 列表：发丝线分行。行自带 24 上内衬，与筛选条之间不再另加外边距 ---- */
.drafts__area {
  min-height: 12rem;
}

.draft-list {
  display: flex;
  flex-direction: column;
}

.draft-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  padding-block: var(--space-lg);

  & + .draft-row {
    border-top: 1px solid var(--color-rule);
  }
}

.draft-row__main {
  flex: 1;
  min-width: 0;
}

.draft-row__title {
  margin: 0;
  font-size: var(--text-md);
  font-weight: 700;
  line-height: 1.4;
  color: var(--color-ink);
  cursor: pointer;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
  }
}

.draft-row__meta {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-top: var(--space-2xs);
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

/* 草稿状态：6px 圆点 + 文案，功能色仅作提示 */
.draft-status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2xs);
  white-space: nowrap;

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background-color: currentColor;
  }

  &--muted {
    color: var(--color-ink-3);
  }

  &--warning {
    color: var(--color-warning);
  }

  &--success {
    color: var(--color-success);
  }
}

.draft-row__actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  flex-shrink: 0;
}

.draft-action {
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  white-space: nowrap;
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
    text-decoration: underline;
    text-underline-offset: 0.25em;
  }

  &--danger:hover {
    color: var(--color-danger);
  }
}

/* ---- 空状态：与首页同一套 ---- */
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

/* ---- 断点 ---- */
@media (max-width: 40rem) {
  .draft-row {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-xs);
  }

  .draft-row__actions {
    justify-content: flex-end;
  }
}
</style>
