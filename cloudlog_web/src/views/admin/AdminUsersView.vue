<template>
  <div class="users admin-page">
    <header class="admin-page__head">
      <div>
        <h1 class="admin-page__title">用户管理</h1>
        <p class="admin-page__subtitle">管理注册用户，共 {{ total }} 人</p>
      </div>
    </header>

    <!-- 筛选区 -->
    <div class="admin-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索用户名或邮箱…"
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

      <el-select v-model="query.status" placeholder="全部状态" clearable class="admin-toolbar__select">
        <el-option label="正常" :value="1" />
        <el-option label="已禁用" :value="0" />
      </el-select>

      <button v-if="hasFilter" class="admin-toolbar__reset" @click="clearFilters">重置筛选</button>
    </div>

    <div class="admin-card">
      <div class="admin-card__body admin-card__body--flush">
        <el-table v-loading="loading" :data="users" row-key="id">
          <el-table-column label="用户" min-width="200">
            <template #default="{ row }">
              <div class="cell-user">
                <el-avatar :size="30" :src="formatAvatar(row.avatar)" />
                <div class="cell-user__meta">
                  <p class="cell-user__name">{{ row.username }}</p>
                  <p class="cell-user__sub">{{ row.email }}</p>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="身份" width="100">
            <template #default="{ row }">
              <span
                class="users__role"
                :class="{ 'users__role--admin': row.role === 'admin' }"
              >
                {{ row.role === 'admin' ? '管理员' : '普通用户' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <span class="status-badge" :class="row.status === 1 ? 'status-badge--success' : 'status-badge--danger'">
                <i class="status-badge__dot" aria-hidden="true"></i>{{ row.status === 1 ? '正常' : '已禁用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="文章数量" width="100">
            <template #default="{ row }">
              <span class="tnum">{{ row.articleCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="注册时间" width="170">
            <template #default="{ row }">
              <span class="tnum">{{ formatTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="上次登录" width="170">
            <template #default="{ row }">
              <span class="tnum">{{ row.lastLoginAt ? formatTime(row.lastLoginAt) : '从未' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <template v-if="row.role !== 'admin'">
                <button class="row-action" @click="openDetail(row)">查看</button>
                <button
                  v-if="row.status === 1"
                  class="row-action row-action--danger"
                  @click="confirmToggle(row, 0)"
                >
                  禁用
                </button>
                <button v-else class="row-action row-action--success" @click="confirmToggle(row, 1)">
                  启用
                </button>
              </template>
              <span v-else class="cell-muted">—</span>
            </template>
          </el-table-column>

          <template #empty>
            <div class="empty-state">
              <p class="empty-state__title">{{ hasFilter ? '没有符合条件的用户。' : '还没有注册用户。' }}</p>
              <button v-if="hasFilter" class="empty-state__action" @click="clearFilters">重置筛选</button>
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
          @current-change="fetchUsers"
        />
      </div>
    </div>

    <!-- 查看弹窗 -->
    <el-dialog v-model="detailVisible" title="用户详情" width="440px">
      <template v-if="detail">
        <div class="users__detail-head">
          <el-avatar :size="52" :src="formatAvatar(detail.avatar)" />
          <div>
            <p class="users__detail-name">{{ detail.username }}</p>
            <p class="users__detail-email">{{ detail.email }}</p>
          </div>
        </div>
        <p v-if="detail.bio" class="users__detail-bio">{{ detail.bio }}</p>
        <dl class="users__detail-meta">
          <div>
            <dt>身份</dt>
            <dd>{{ detail.role === 'admin' ? '管理员' : '普通用户' }}</dd>
          </div>
          <div v-if="detail.occupation">
            <dt>职业</dt>
            <dd>{{ detail.occupation }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>{{ detail.status === 1 ? '正常' : '已禁用' }}</dd>
          </div>
          <div>
            <dt>文章数量</dt>
            <dd class="tnum">{{ detail.articleCount }}</dd>
          </div>
          <div>
            <dt>注册时间</dt>
            <dd class="tnum">{{ formatTime(detail.createdAt) }}</dd>
          </div>
          <div>
            <dt>上次登录</dt>
            <dd class="tnum">{{ detail.lastLoginAt ? formatTime(detail.lastLoginAt) : '从未登录' }}</dd>
          </div>
          <div>
            <dt>登录 IP</dt>
            <dd class="tnum">{{ detail.lastLoginIp || '从未' }}</dd>
          </div>
        </dl>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUsers, updateUserStatus } from '@/api/admin'
import type { AdminUserItem, UserStatus } from '@/api/types/admin'
import { formatMinioUrl, defaultAvatarUrl } from '@/config'
import dayjs from 'dayjs'

const loading = ref(false)
const users = ref<AdminUserItem[]>([])
const total = ref(0)
const detail = ref<AdminUserItem | null>(null)
const detailVisible = ref(false)

const query = reactive({
  page: 1,
  pageSize: 15,
  keyword: null as string | null,
  status: null as number | null,
})

const hasFilter = computed(() => !!(query.keyword || query.status !== null))

const fetchUsers = async () => {
  loading.value = true
  try {
    const { data } = await getAdminUsers(query)
    users.value = data.list
    total.value = Number(data.total) // PageResult.total 以字符串下发（Long 统一转字符串），分页器要 number
  } catch (error) {
    console.error('获取用户列表失败:', error) // 提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => query.keyword,
  () => {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      query.page = 1
      fetchUsers()
    }, 500)
  },
)

watch(
  () => query.status,
  () => {
    query.page = 1
    fetchUsers()
  },
)

const clearFilters = () => {
  query.keyword = null
  query.status = null
  query.page = 1
  fetchUsers()
}

const confirmToggle = (row: AdminUserItem, status: UserStatus) => {
  const disabling = status === 0
  ElMessageBox.confirm(
    disabling
      ? `确定要禁用「${row.username}」吗？禁用后该用户将无法登录，已发布内容不受影响。`
      : `确定要启用「${row.username}」吗？`,
    disabling ? '禁用用户' : '启用用户',
    {
      confirmButtonText: disabling ? '禁用' : '启用',
      cancelButtonText: '取消',
      type: disabling ? 'warning' : 'info',
    },
  ).then(async () => {
    try {
      await updateUserStatus(row.id, status)
      ElMessage.success(disabling ? '用户已禁用' : '用户已启用')
      fetchUsers()
    } catch (error) {
      console.error('更新用户状态失败:', error)
    }
  })
}

const openDetail = (row: AdminUserItem) => {
  detail.value = row
  detailVisible.value = true
}

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm')
const formatAvatar = (avatar?: string | null) => formatMinioUrl(avatar) || defaultAvatarUrl

onMounted(() => {
  fetchUsers()
})
</script>

<style scoped lang="scss">
.users__role {
  font-size: var(--text-xs);
  color: var(--color-ink-3);

  &--admin {
    padding: 1px var(--space-2xs);
    color: var(--color-accent-ink);
    background-color: var(--color-accent-wash);
    border-radius: var(--radius-xs);
  }
}

.cell-user {
  padding-block: var(--space-2xs);
}

.users__detail-head {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.users__detail-name {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-md);
  color: var(--color-ink);
}

.users__detail-email {
  margin-top: var(--space-3xs);
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

.users__detail-bio {
  margin: var(--space-md) 0 0;
  padding: var(--space-sm) var(--space-md);
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-ink-2);
  background-color: var(--color-paper-3);
  border-radius: var(--radius-sm);
}

.users__detail-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-md);
  margin-top: var(--space-lg);

  dt {
    font-size: var(--text-xs);
    color: var(--color-ink-4);
  }

  dd {
    margin-top: var(--space-3xs);
    font-size: var(--text-sm);
    color: var(--color-ink);
  }
}

.row-action + .row-action {
  margin-left: var(--space-md);
}
</style>
