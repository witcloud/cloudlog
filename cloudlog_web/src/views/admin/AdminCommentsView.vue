<template>
  <div class="comments admin-page">
    <header class="admin-page__head">
      <div>
        <h1 class="admin-page__title">评论管理</h1>
        <p class="admin-page__subtitle">审核全站评论，维护机审用的违禁词库</p>
      </div>
    </header>

    <!-- 一级页签切管理对象（评论 / 词库），二级 .seg 切筛选（状态）；两层分工见 ui-design.md §3.11 -->
    <el-tabs v-model="pane" class="comments__panes">
      <el-tab-pane label="评论审核" name="comments">
        <div class="comments__toolbar">
          <div class="seg" role="tablist" aria-label="评论状态筛选">
            <button
              v-for="tab in tabs"
              :key="tab.status"
              class="seg__item"
              :class="{ 'is-active': query.status === tab.status }"
              role="tab"
              :aria-selected="query.status === tab.status"
              @click="switchTab(tab.status)"
            >
              {{ tab.label }}
              <span class="seg__count tnum">{{ counts[tab.status] ?? 0 }}</span>
            </button>
          </div>

          <el-input
            v-model="query.keyword"
            placeholder="搜索评论内容 / 评论者 / 文章标题…"
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
        </div>

        <!-- 评论列表 -->
        <div class="admin-card">
          <div class="admin-card__body admin-card__body--flush">
            <el-table v-loading="loading" :data="comments" row-key="id">
              <el-table-column label="评论内容" min-width="280">
                <template #default="{ row }">
                  <p class="comments__content" @click="openDetail(row)">{{ row.content }}</p>
                </template>
              </el-table-column>
              <el-table-column label="文章" min-width="160">
                <template #default="{ row }">
                  <a
                    v-if="row.postTitle"
                    :href="`/articles/${row.postId}`"
                    target="_blank"
                    rel="noopener"
                    class="cell-title"
                    :title="row.postTitle"
                    >{{ row.postTitle }}</a
                  >
                  <span v-else class="cell-muted">文章已删除</span>
                </template>
              </el-table-column>
              <el-table-column label="评论人" min-width="120">
                <template #default="{ row }">
                  <div v-if="row.user" class="cell-user">
                    <el-avatar :size="20" :src="formatAvatar(row.user.avatar)" />
                    <span class="cell-user__name">{{ row.user.username }}</span>
                  </div>
                  <span v-else class="cell-muted">访客</span>
                </template>
              </el-table-column>
              <!-- 命中词解释「当初为什么被拦下」；为空含关审核、未命中与存量评论三种情况，不必区分 -->
              <el-table-column label="命中词" width="110">
                <template #default="{ row }">
                  <span v-if="row.hitWord">{{ row.hitWord }}</span>
                  <span v-else class="cell-muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="96">
                <template #default="{ row }">
                  <span class="status-badge" :class="`status-badge--${statusTone(row.status)}`">
                    <i class="status-badge__dot" aria-hidden="true"></i
                    >{{ statusLabel(row.status) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="时间" width="170">
                <template #default="{ row }">
                  <span class="tnum">{{ formatTime(row.createdAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <button
                    v-if="row.status !== 'approved'"
                    class="row-action row-action--success"
                    @click="moderate(row, 'approved')"
                  >
                    通过
                  </button>
                  <button
                    v-if="row.status !== 'rejected'"
                    class="row-action row-action--danger"
                    @click="moderate(row, 'rejected')"
                  >
                    拒绝
                  </button>
                  <button
                    v-if="row.status !== 'pending'"
                    class="row-action"
                    @click="moderate(row, 'pending')"
                  >
                    退回待审
                  </button>
                  <button class="row-action row-action--danger" @click="confirmDelete(row)">
                    删除
                  </button>
                </template>
              </el-table-column>

              <template #empty>
                <div class="empty-state">
                  <p class="empty-state__title">
                    {{ query.keyword ? '没有匹配的评论。' : '这里还没有评论。' }}
                  </p>
                  <p class="empty-state__hint">{{ emptyHint }}</p>
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
              @current-change="fetchComments"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="违禁词库" name="words">
        <div class="comments__toolbar">
          <!-- 单条添加：词面 + 处置动作，回车即提交 -->
          <div class="words__add">
            <el-input
              v-model="newWord"
              placeholder="添加违禁词，最长 50 字"
              maxlength="50"
              clearable
              class="words__add-input"
              @keyup.enter="submitWord"
            />
            <el-select v-model="newAction" class="admin-toolbar__select" aria-label="处置动作">
              <el-option
                v-for="option in actionOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-button type="primary" :loading="adding" @click="submitWord">添加</el-button>
          </div>

          <div class="words__tools">
            <el-input
              v-model="wordQuery.keyword"
              placeholder="搜索违禁词…"
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
            <el-button @click="openImport">批量导入</el-button>
          </div>
        </div>

        <div class="admin-card">
          <header class="admin-card__head">
            <h2 class="admin-card__title">词库</h2>
            <!-- 词库本身没有开关，机审的总闸在系统设置；不写出来，博主会以为加了词就该拦 -->
            <span class="admin-card__extra">机审只在「系统设置 → 评论审核」开启时生效</span>
          </header>
          <div class="admin-card__body admin-card__body--flush">
            <el-table v-loading="wordsLoading" :data="words" row-key="id">
              <el-table-column label="词" min-width="200">
                <template #default="{ row }">
                  <span class="words__word">{{ row.word }}</span>
                </template>
              </el-table-column>
              <el-table-column label="命中后" width="110">
                <template #default="{ row }">
                  <span
                    class="status-badge"
                    :class="
                      row.action === 'reject' ? 'status-badge--danger' : 'status-badge--warning'
                    "
                  >
                    <i class="status-badge__dot" aria-hidden="true"></i
                    >{{ actionLabel(row.action) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="96">
                <template #default="{ row }">
                  <span
                    class="status-badge"
                    :class="row.isEnabled === 1 ? 'status-badge--success' : 'status-badge--muted'"
                  >
                    <i class="status-badge__dot" aria-hidden="true"></i
                    >{{ row.isEnabled === 1 ? '启用' : '停用' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="添加时间" width="170">
                <template #default="{ row }">
                  <span class="tnum">{{ formatTime(row.createdAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <button class="row-action" @click="toggleWord(row)">
                    {{ row.isEnabled === 1 ? '停用' : '启用' }}
                  </button>
                  <button class="row-action row-action--danger" @click="confirmDeleteWord(row)">
                    删除
                  </button>
                </template>
              </el-table-column>

              <template #empty>
                <div class="empty-state">
                  <p class="empty-state__title">
                    {{ wordQuery.keyword ? '没有匹配的违禁词。' : '词库还是空的。' }}
                  </p>
                  <p class="empty-state__hint">
                    {{
                      wordQuery.keyword
                        ? '换个关键词试试。'
                        : '词库为空时机审不拦任何评论，新评论按「评论审核」开关直接发布或全部转待审。'
                    }}
                  </p>
                </div>
              </template>
            </el-table>
          </div>

          <div v-if="wordTotal > wordQuery.pageSize" class="admin-pagination">
            <el-pagination
              v-model:current-page="wordQuery.page"
              :page-size="wordQuery.pageSize"
              layout="total, prev, pager, next"
              :total="wordTotal"
              @current-change="fetchWords"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 审核工作台：全文 + 回复上下文 + 元信息，状态流转动作集中在 footer -->
    <el-dialog v-model="detailVisible" title="评论详情" width="560px">
      <template v-if="detail">
        <p class="comments__detail-content">{{ detail.content }}</p>

        <div v-if="detail.parentId" class="comments__detail-reply">
          <p class="comments__detail-reply-label">
            {{ detail.parentContent ? `↩ 回复 @${detail.parentUsername || '访客'}` : '↩ 回复' }}
          </p>
          <div class="comments__detail-reply-quote">
            <p class="comments__detail-reply-text">{{ detail.parentContent || '原评论已删除' }}</p>
          </div>
        </div>

        <dl class="comments__detail-meta">
          <div>
            <dt>评论人</dt>
            <dd>{{ detail.user?.username || '访客' }}</dd>
          </div>
          <div>
            <dt>文章</dt>
            <dd>
              <a
                v-if="detail.postTitle"
                :href="`/articles/${detail.postId}`"
                target="_blank"
                rel="noopener"
                class="comments__detail-link"
                >{{ detail.postTitle }}</a
              >
              <span v-else class="cell-muted">文章已删除</span>
            </dd>
          </div>
          <div>
            <dt>命中词</dt>
            <dd>
              <span v-if="detail.hitWord">{{ detail.hitWord }}</span>
              <span v-else class="cell-muted">—</span>
            </dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>
              <span class="status-badge" :class="`status-badge--${statusTone(detail.status)}`">
                <i class="status-badge__dot" aria-hidden="true"></i>{{ statusLabel(detail.status) }}
              </span>
            </dd>
          </div>
          <div>
            <dt>时间</dt>
            <dd class="tnum">{{ formatTime(detail.createdAt) }}</dd>
          </div>
        </dl>
      </template>
      <template #footer>
        <el-button
          v-if="detail && detail.status !== 'approved'"
          type="primary"
          @click="moderate(detail, 'approved')"
          >通过</el-button
        >
        <el-button
          v-if="detail && detail.status !== 'rejected'"
          @click="moderate(detail, 'rejected')"
          >拒绝</el-button
        >
        <el-button v-if="detail && detail.status !== 'pending'" @click="moderate(detail, 'pending')"
          >退回待审</el-button
        >
        <el-button v-if="detail" type="danger" plain @click="confirmDelete(detail)">删除</el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入：拆分规则写在弹窗里，提交后原地回显 added/skipped，不关掉窗口 -->
    <el-dialog v-model="importVisible" title="批量导入违禁词" width="560px">
      <p class="words__import-hint">
        一行一个词，中英文逗号也可作分隔。导入时自动去除首尾空白、丢弃空行并去重；已存在的词与超过
        50 字的条目会被跳过，都计入「跳过」数。
      </p>
      <el-input
        v-model="importText"
        type="textarea"
        :rows="8"
        placeholder="广告&#10;代购，刷单&#10;代开发票"
      />
      <div class="words__import-action">
        <span class="words__import-label">本批词命中后</span>
        <el-select v-model="importAction" class="admin-toolbar__select" aria-label="处置动作">
          <el-option
            v-for="option in actionOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
      <p v-if="importResult" class="words__import-result tnum">
        已导入 {{ importResult.added }} 个，跳过 {{ importResult.skipped }} 个。
      </p>
      <template #footer>
        <el-button type="primary" :loading="importing" @click="submitImport">导入</el-button>
        <el-button @click="importVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createBannedWord,
  deleteAdminComment,
  deleteBannedWord,
  getAdminComments,
  getBannedWords,
  getCommentStatusCounts,
  importBannedWords,
  toggleBannedWord,
  updateCommentStatus,
} from '@/api/admin'
import type {
  AdminCommentItem,
  AdminCommentQuery,
  BannedWordAction,
  BannedWordImportResult,
  BannedWordItem,
  BannedWordQuery,
  CommentStatus,
  CommentStatusCounts,
} from '@/api/types/admin'
import { formatMinioUrl, defaultAvatarUrl } from '@/config'
import dayjs from 'dayjs'

const loading = ref(false)
const comments = ref<AdminCommentItem[]>([])
const total = ref(0)
const counts = ref<CommentStatusCounts>({ pending: 0, approved: 0, rejected: 0 })
const detail = ref<AdminCommentItem | null>(null)
const detailVisible = ref(false)

const query = reactive<AdminCommentQuery>({
  page: 1,
  pageSize: 15,
  status: 'pending',
  keyword: null,
})

const tabs: { status: CommentStatus; label: string }[] = [
  { status: 'pending', label: '待审核' },
  { status: 'approved', label: '已通过' },
  { status: 'rejected', label: '已拒绝' },
]

const statusLabel = (status: CommentStatus) =>
  tabs.find((tab) => tab.status === status)?.label ?? '未知状态'

const statusTone = (status: CommentStatus) =>
  ({ pending: 'warning', approved: 'success', rejected: 'danger' })[status]

const emptyHint = computed(() => {
  if (query.keyword) return '换个关键词试试。'
  if (query.status === 'rejected') return '被拒绝的评论会显示在这里。'
  if (query.status === 'approved') return '审核通过的评论会显示在这里。'
  return '读者发表评论后会显示在这里。'
})

const fetchComments = async () => {
  loading.value = true
  try {
    const { data } = await getAdminComments(query)
    comments.value = data.list
    total.value = Number(data.total) // PageResult.total 以字符串下发（Long 统一转字符串），分页器要 number
  } catch (error) {
    console.error('获取评论列表失败:', error) // 提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

const fetchCounts = async () => {
  try {
    const { data } = await getCommentStatusCounts()
    counts.value = data
  } catch (error) {
    console.error('获取评论计数失败:', error)
  }
}

const switchTab = (status: CommentStatus) => {
  query.status = status
  query.page = 1
  fetchComments()
}

let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => query.keyword,
  () => {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      query.page = 1
      fetchComments()
    }, 500)
  },
)

const moderateTips: Record<CommentStatus, string> = {
  approved: '评论已通过',
  rejected: '评论已拒绝',
  pending: '已退回待审',
}

const moderate = async (row: AdminCommentItem, status: CommentStatus) => {
  try {
    await updateCommentStatus(row.id, status)
    ElMessage.success(moderateTips[status])
    detailVisible.value = false
    fetchComments()
    fetchCounts()
  } catch (error) {
    console.error('审核评论失败:', error)
  }
}

const confirmDelete = (row: AdminCommentItem) => {
  ElMessageBox.confirm('确定要删除这条评论吗？其回复将一并删除，此操作不可恢复。', '删除评论', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'error',
  }).then(async () => {
    try {
      await deleteAdminComment(row.id)
      ElMessage.success('评论已删除')
      detailVisible.value = false
      fetchComments()
      fetchCounts()
    } catch (error) {
      console.error('删除评论失败:', error)
    }
  })
}

const openDetail = (row: AdminCommentItem) => {
  detail.value = row
  detailVisible.value = true
}

/* ---- 违禁词库 ---- */

const pane = ref<'comments' | 'words'>('comments')

const words = ref<BannedWordItem[]>([])
const wordsLoading = ref(false)
const wordTotal = ref(0)
const wordsLoaded = ref(false)

const wordQuery = reactive<BannedWordQuery>({
  page: 1,
  pageSize: 15,
  keyword: null,
})

const actionOptions: { value: BannedWordAction; label: string }[] = [
  { value: 'pending', label: '转待审' },
  { value: 'reject', label: '直接拒绝' },
]

const actionLabel = (action: BannedWordAction) =>
  actionOptions.find((option) => option.value === action)?.label ?? action

const newWord = ref('')
const newAction = ref<BannedWordAction>('pending')
const adding = ref(false)

const importVisible = ref(false)
const importText = ref('')
const importAction = ref<BannedWordAction>('pending')
const importResult = ref<BannedWordImportResult | null>(null)
const importing = ref(false)

const fetchWords = async () => {
  wordsLoading.value = true
  try {
    const { data } = await getBannedWords(wordQuery)
    words.value = data.list
    wordTotal.value = Number(data.total)
  } catch (error) {
    console.error('获取违禁词库失败:', error) // 提示由拦截器统一处理
  } finally {
    wordsLoading.value = false
    wordsLoaded.value = true
  }
}

// 页签首次展开才取词库：不进这个页签的博主不该为它付一次请求
watch(pane, (value) => {
  if (value === 'words' && !wordsLoaded.value) fetchWords()
})

let wordDebounceTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => wordQuery.keyword,
  () => {
    if (wordDebounceTimer) clearTimeout(wordDebounceTimer)
    wordDebounceTimer = setTimeout(() => {
      wordQuery.page = 1
      fetchWords()
    }, 500)
  },
)

const submitWord = async () => {
  const word = newWord.value.trim()
  if (!word) {
    ElMessage.warning('请先输入要添加的词')
    return
  }
  adding.value = true
  try {
    await createBannedWord({ word, action: newAction.value })
    ElMessage.success('违禁词已添加')
    newWord.value = ''
    wordQuery.page = 1
    fetchWords()
  } catch (error) {
    console.error('添加违禁词失败:', error) // 重名等原因由拦截器提示
  } finally {
    adding.value = false
  }
}

const openImport = () => {
  importResult.value = null
  importVisible.value = true
}

const submitImport = async () => {
  if (!importText.value.trim()) {
    ElMessage.warning('请先粘贴要导入的词')
    return
  }
  importing.value = true
  try {
    const { data } = await importBannedWords({ text: importText.value, action: importAction.value })
    importResult.value = data
    ElMessage.success(`已导入 ${data.added} 个词，跳过 ${data.skipped} 个`)
    fetchWords()
  } catch (error) {
    console.error('批量导入违禁词失败:', error)
  } finally {
    importing.value = false
  }
}

const applyToggle = async (row: BannedWordItem, disabling: boolean) => {
  try {
    await toggleBannedWord(row.id)
    ElMessage.success(disabling ? '已停用，机审将跳过这个词' : '已启用')
    fetchWords()
  } catch (error) {
    console.error('切换违禁词状态失败:', error)
  }
}

const toggleWord = (row: BannedWordItem) => {
  const disabling = row.isEnabled === 1
  if (!disabling) {
    applyToggle(row, false) // 启用是恢复，不再多问一道
    return
  }
  // 禁用类操作要二次确认（ui-design.md §3.11）；文案写明词条会留着，避免被当成删除
  ElMessageBox.confirm(`停用后「${row.word}」不再参与机审，词条本身会保留。`, '停用违禁词', {
    confirmButtonText: '停用',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => applyToggle(row, true))
}

const confirmDeleteWord = (row: BannedWordItem) => {
  ElMessageBox.confirm(
    `确定要删除「${row.word}」吗？删除后机审不再拦它，此操作不可恢复；已记录的命中词不受影响。`,
    '删除违禁词',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'error' },
  ).then(async () => {
    try {
      await deleteBannedWord(row.id)
      ElMessage.success('违禁词已删除')
      fetchWords()
    } catch (error) {
      console.error('删除违禁词失败:', error)
    }
  })
}

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm')
const formatAvatar = (avatar?: string | null) => formatMinioUrl(avatar) || defaultAvatarUrl

onMounted(() => {
  fetchComments()
  fetchCounts()
})
</script>

<style scoped lang="scss">
.comments__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  flex-wrap: wrap;
  margin-bottom: var(--space-md);
}

/* ---- 分段页签：中性反色选中态 ---- */
.seg {
  display: inline-flex;
  padding: 2px;
  background-color: var(--color-paper-3);
  border-radius: var(--radius-sm);
}

.seg__item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2xs);
  padding: var(--space-2xs) var(--space-sm);
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  border-radius: var(--radius-xs);
  transition:
    color var(--dur-micro) var(--ease-out),
    background-color var(--dur-micro) var(--ease-out);

  &:hover:not(.is-active) {
    color: var(--color-ink);
  }

  &.is-active {
    font-weight: 500;
    color: var(--color-paper-2);
    background-color: var(--color-ink);
  }
}

.seg__count {
  font-size: var(--text-xs);
  opacity: 0.72;
}

/* ---- 列表 ---- */
.comments__content {
  display: -webkit-box;
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-ink-2);
  cursor: pointer;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
  }
}

.row-action + .row-action {
  margin-left: var(--space-md);
}

/* ---- 详情弹窗 ---- */
.comments__detail-content {
  margin: 0;
  padding: var(--space-md);
  font-size: var(--text-sm);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-ink);
  background-color: var(--color-paper-3);
  border-radius: var(--radius-sm);
}

/* 父评论只做判断用的摘录，超 3 行截断 */
.comments__detail-reply {
  margin-top: var(--space-md);
}

.comments__detail-reply-label {
  margin: 0 0 var(--space-2xs);
  font-size: var(--text-xs);
  color: var(--color-ink-4);
}

.comments__detail-reply-quote {
  padding: var(--space-sm) var(--space-md);
  background-color: var(--color-paper-3);
  border-radius: var(--radius-sm);
}

/* 截断元素自己不带 padding，第 4 行会漏进下内衬里露出半行字 */
.comments__detail-reply-text {
  display: -webkit-box;
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--color-ink-3);
  overflow: hidden;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.comments__detail-meta {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  margin: var(--space-lg) 0 0;

  div {
    display: flex;
    gap: var(--space-md);
  }

  dt {
    flex-shrink: 0;
    width: 3.5em;
    font-size: var(--text-sm);
    color: var(--color-ink-4);
  }

  dd {
    margin: 0;
    font-size: var(--text-sm);
    color: var(--color-ink-2);
  }
}

.comments__detail-link {
  color: var(--color-ink-2);

  &:hover {
    color: var(--color-accent);
  }
}

/* ---- 违禁词库 ---- */
.words__add,
.words__tools {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-wrap: wrap;
}

/* 与 .admin-toolbar__search 同宽，两个框在工具条两端才对得齐 */
.words__add-input {
  width: 14rem;
  max-width: 100%;
}

.words__word {
  font-size: var(--text-sm);
  color: var(--color-ink);
  word-break: break-all;
}

.words__import-hint {
  margin: 0 0 var(--space-md);
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--color-ink-3);
}

.words__import-action {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}

.words__import-label {
  font-size: var(--text-sm);
  color: var(--color-ink-2);
}

.words__import-result {
  margin: var(--space-md) 0 0;
  font-size: var(--text-sm);
  color: var(--color-success);
}
</style>
