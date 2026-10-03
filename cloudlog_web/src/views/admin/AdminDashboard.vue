<template>
  <div v-loading="loading" class="dashboard admin-page">
    <!-- 页头：问候语 + 日期 -->
    <header class="admin-page__head">
      <div>
        <h1 class="admin-page__title">工作台</h1>
        <p class="admin-page__subtitle">{{ greeting }}，{{ username }}。今天是 {{ today }}。</p>
      </div>
    </header>

    <!-- 统计卡片 -->
    <div class="dashboard__stats">
      <div v-for="stat in statCards" :key="stat.label" class="stat-card">
        <p class="stat-card__label">{{ stat.label }}</p>
        <p class="stat-card__value tnum" :class="{ 'stat-card__value--warning': stat.warning }">
          {{ stat.value }}
        </p>
        <RouterLink v-if="stat.linkTo" :to="stat.linkTo" class="stat-card__link">
          {{ stat.linkText }} →
        </RouterLink>
        <p v-else class="stat-card__foot">{{ stat.foot }}</p>
      </div>
    </div>

    <!-- 概览两栏：最近文章 + 最近活动 -->
    <div class="dashboard__grid">
      <section class="admin-card dashboard__panel">
        <header class="admin-card__head">
          <h2 class="admin-card__title">最近文章</h2>
          <RouterLink to="/admin/articles" class="admin-card__extra">查看全部 →</RouterLink>
        </header>
        <div class="admin-card__body admin-card__body--flush">
          <ul v-if="data?.recentArticles.length" class="recent-list">
            <li v-for="article in data.recentArticles" :key="article.id" class="recent-row">
              <div class="recent-row__main">
                <a
                  :href="`/articles/${article.id}`"
                  target="_blank"
                  rel="noopener"
                  class="recent-row__title"
                  >{{ article.title }}</a
                >
                <p class="recent-row__meta">
                  <span
                    class="status-badge"
                    :class="`status-badge--${articleTone(article.status)}`"
                  >
                    <i class="status-badge__dot" aria-hidden="true"></i>{{ articleLabel(article.status) }}
                  </span>
                  <span aria-hidden="true">·</span>
                  <span>{{ article.author?.username || '未知作者' }}</span>
                  <span aria-hidden="true">·</span>
                  <time :datetime="article.createdAt" class="tnum">{{ formatTime(article.createdAt) }}</time>
                </p>
              </div>
            </li>
          </ul>
          <div v-else class="empty-state">
            <p class="empty-state__title">还没有任何文章。</p>
            <p class="empty-state__hint">去前台写第一篇文章吧。</p>
          </div>
        </div>
      </section>

      <section class="admin-card dashboard__panel">
        <header class="admin-card__head">
          <h2 class="admin-card__title">最近活动</h2>
          <RouterLink to="/admin/comments" class="admin-card__extra">评论管理 →</RouterLink>
        </header>
        <div class="admin-card__body admin-card__body--flush">
          <ul v-if="data?.recentComments.length" class="recent-list">
            <li v-for="comment in data.recentComments" :key="comment.id" class="recent-row">
              <div class="recent-row__main">
                <p class="recent-row__actor">
                  <strong>{{ comment.user?.username || '访客' }}</strong>
                  评论了
                  <RouterLink
                    v-if="comment.postTitle"
                    :to="{ name: 'ArticleDetail', params: { id: comment.postId } }"
                    class="recent-row__post"
                    >《{{ comment.postTitle }}》</RouterLink
                  >
                  <span v-else class="cell-muted">已删除的文章</span>
                </p>
                <p class="recent-row__excerpt">{{ comment.content }}</p>
                <time :datetime="comment.createdAt" class="recent-row__time tnum">
                  {{ formatTime(comment.createdAt) }}
                </time>
              </div>
            </li>
          </ul>
          <div v-else class="empty-state">
            <p class="empty-state__title">还没有评论动态。</p>
            <p class="empty-state__hint">读者发表评论后会显示在这里。</p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getDashboard } from '@/api/admin'
import type { ArticleStatus, DashboardData } from '@/api/types/admin'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'

const userStore = useUserStore()
const loading = ref(false)
const data = ref<DashboardData | null>(null)

const username = computed(() => userStore.userInfo?.username || '管理员')

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const today = dayjs().format('YYYY年MM月DD日')

const statCards = computed(() => [
  { label: '用户总数', value: Number(data.value?.userCount ?? 0), foot: '注册用户', warning: false, linkTo: '/admin/users', linkText: '用户管理' },
  { label: '文章总数', value: Number(data.value?.articleCount ?? 0), foot: '含草稿与定时发布', warning: false, linkTo: '/admin/articles', linkText: '文章管理' },
  {
    label: '待处理评论',
    value: Number(data.value?.pendingCommentCount ?? 0),
    foot: '待审核评论',
    warning: Number(data.value?.pendingCommentCount ?? 0) > 0,
    linkTo: '/admin/comments',
    linkText: '去处理',
  },
  { label: '累计阅读', value: Number(data.value?.totalViews ?? 0), foot: '全部文章阅读量', warning: false, linkTo: null, linkText: '' },
])

// 状态展示与前台草稿箱同一套语义：muted/warning/success，强调色不参与状态
const ARTICLE_META: Record<ArticleStatus, { label: string; tone: string }> = {
  draft: { label: '草稿', tone: 'muted' },
  published: { label: '已发布', tone: 'success' },
  scheduled: { label: '定时发布', tone: 'warning' },
  offline: { label: '已下线', tone: 'danger' },
}

const articleLabel = (status: ArticleStatus) => ARTICLE_META[status]?.label ?? '未知状态'
const articleTone = (status: ArticleStatus) => ARTICLE_META[status]?.tone ?? 'muted'

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm')

const fetchDashboard = async () => {
  loading.value = true
  try {
    const { data: payload } = await getDashboard()
    data.value = payload
  } catch (error) {
    console.error('获取工作台数据失败:', error) // 提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDashboard()
})
</script>

<style scoped lang="scss">
.dashboard__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-md);
}

.stat-card {
  padding: var(--space-lg);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
}

.stat-card__label {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

.stat-card__value {
  margin-top: var(--space-2xs);
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 600;
  line-height: 1.2;
  color: var(--color-ink);

  &--warning {
    color: var(--color-warning);
  }
}

.stat-card__foot {
  margin-top: var(--space-2xs);
  font-size: var(--text-xs);
  color: var(--color-ink-4);
}

.stat-card__link {
  display: inline-block;
  margin-top: var(--space-2xs);
  font-size: var(--text-xs);
  color: var(--color-accent);

  &:hover {
    text-decoration: underline;
    text-underline-offset: 0.25em;
  }
}

.dashboard__grid {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: var(--space-md);
  margin-top: var(--space-md);
}

.recent-list {
  display: flex;
  flex-direction: column;
}

.recent-row {
  padding: var(--space-md) var(--space-lg);

  & + .recent-row {
    border-top: 1px solid var(--color-rule);
  }
}

.recent-row__main {
  min-width: 0;
}

.recent-row__title {
  display: block;
  font-size: var(--text-base);
  font-weight: 500;
  line-height: 1.4;
  color: var(--color-ink);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
  }
}

.recent-row__meta {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-top: var(--space-2xs);
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.recent-row__actor {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-ink-2);

  strong {
    font-weight: 600;
    color: var(--color-ink);
  }
}

.recent-row__post {
  color: var(--color-ink-2);

  &:hover {
    color: var(--color-accent);
  }
}

.recent-row__excerpt {
  display: -webkit-box;
  margin-top: var(--space-2xs);
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-ink-3);
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.recent-row__time {
  display: block;
  margin-top: var(--space-2xs);
  font-size: var(--text-xs);
  color: var(--color-ink-4);
}

/* ---- 断点 ---- */
@media (max-width: 60rem) {
  .dashboard__stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .dashboard__grid {
    grid-template-columns: 1fr;
  }
}
</style>
