<template>
  <div class="published">
    <section class="published__panel">
      <!-- 单色描边对勾，不做大面积装饰 -->
      <svg class="published__mark" viewBox="0 0 24 24" width="32" height="32" fill="none" aria-hidden="true">
        <circle cx="12" cy="12" r="9.25" stroke="currentColor" stroke-width="1.5" />
        <path
          d="m8.4 12.3 2.5 2.5 4.7-5.2"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>

      <h1 class="published__title">{{ isScheduled ? '已加入定时发布' : '文章发布成功' }}</h1>
      <p class="published__hint">
        {{
          isScheduled
            ? '文章将在设定的时间自动上线。'
            : '文章已上线，读者现在就可以阅读它了。'
        }}
      </p>

      <!-- 文章基本信息：发丝线三行 -->
      <dl v-if="article" class="published__meta">
        <div class="published__row">
          <dt>标题</dt>
          <dd>{{ article.title || '无标题' }}</dd>
        </div>
        <div class="published__row">
          <dt>分类</dt>
          <dd>{{ article.category?.name || '未分类' }}</dd>
        </div>
        <div class="published__row">
          <dt>{{ isScheduled ? '计划发布于' : '发布时间' }}</dt>
          <dd class="tnum">{{ formatTime(isScheduled ? article.publishTime : article.publishedAt) }}</dd>
        </div>
      </dl>

      <div class="published__actions">
        <el-button type="primary" @click="handleViewArticle">查看文章</el-button>
        <el-button class="published__write" @click="handleContinue">继续写作</el-button>
      </div>
      <button class="published__home" @click="handleGoHome">返回首页</button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticleDetail } from '@/api/article'
import type { Article } from '@/api/types/article'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()
const articleId = computed(() => route.query.id as string)
const article = ref<Article | null>(null)

// 详情接口对 status/publishTime 不保证返回，读取方按回退处理
const isScheduled = computed(() => article.value?.status === 'scheduled')

const formatTime = (value?: string | null) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'

onMounted(async () => {
  if (!articleId.value) return
  try {
    const { data } = await getArticleDetail(articleId.value)
    article.value = data
  } catch (error) {
    // 获取详情失败不影响成功态展示，仅省略文章信息区
    console.error('获取文章信息失败:', error)
  }
})

// 成功页是一次性收据，三个出口全部 replace，返回键不再落回这里
const handleViewArticle = () => {
  if (articleId.value) {
    router.replace(`/articles/${articleId.value}`)
  } else {
    router.replace('/')
  }
}

const handleContinue = () => {
  router.replace('/editor')
}

const handleGoHome = () => {
  router.replace('/')
}
</script>

<style scoped lang="scss">
.published {
  display: flex;
  justify-content: center;
  min-height: calc(100dvh - var(--header-h) - 10rem);
}

.published__panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 30rem;
  padding-block: var(--space-xl);
  text-align: center;
}

.published__mark {
  color: var(--color-success);
}

.published__title {
  margin: var(--space-md) 0 0;
  font-size: var(--text-lg);
  color: var(--color-ink);
}

.published__hint {
  margin: var(--space-xs) 0 0;
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

/* 文章信息：左右对齐的发丝线清单 */
.published__meta {
  width: 100%;
  margin: var(--space-xl) 0 0;
  padding-block: var(--space-xs);
  border-top: 1px solid var(--color-rule);
  border-bottom: 1px solid var(--color-rule);
  text-align: start;
}

.published__row {
  display: flex;
  gap: var(--space-md);
  padding-block: var(--space-xs);

  & + .published__row {
    border-top: 1px solid var(--color-rule);
  }

  dt {
    width: 5em;
    flex-shrink: 0;
    font-size: var(--text-sm);
    color: var(--color-ink-3);
  }

  dd {
    flex: 1;
    min-width: 0;
    margin: 0;
    font-size: var(--text-sm);
    line-height: 1.7;
    color: var(--color-ink);
    overflow-wrap: anywhere;
  }
}

.published__actions {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-xl);

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

.published__home {
  margin-top: var(--space-md);
  font-size: var(--text-sm);
  color: var(--color-ink-3);
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
  }
}

@media (max-width: 40rem) {
  .published {
    min-height: calc(100dvh - var(--header-h) - 8rem);
  }

  .published__actions {
    width: 100%;

    :deep(.el-button) {
      flex: 1;
      margin-left: 0;
    }
  }
}
</style>
