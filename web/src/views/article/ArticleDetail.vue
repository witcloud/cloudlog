<template>
  <div v-if="loading" class="loading-container">
    <el-skeleton :rows="10" animated />
  </div>
  <div v-else-if="article" class="article-detail">
    <div class="article-detail__main">
      <ArticleHeader :article="article" :can-edit="canEdit" @edit="handleEdit" />

      <div class="article-detail__content">
        <md-preview
                :model-value="article.content"
                :theme="isDark ? 'dark' : 'light'"
                code-theme="one-dark"
              />
      </div>

      <ArticleFooter :author-name="article.author.username" :article-title="article.title" />

      <CommentSection :comment-count="article.comments" />
    </div>

    <aside class="article-detail__aside">
      <div class="article-detail__aside-sticky">
        <AuthorCard :author="article.author" :stats="{ likes: article.likes }" />
        <TocSidebar :toc-items="tocItems" />
      </div>
    </aside>
  </div>
  <div v-else class="article-missing">
    <p class="article-missing__title">文章不存在。</p>
    <p class="article-missing__hint">它可能已被删除，或链接有误。</p>
    <RouterLink to="/" class="article-missing__action">返回首页</RouterLink>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useSEO } from '@/utils/seo'
import { useArticleStore } from '@/stores/article'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'
import { storeToRefs } from 'pinia'

// Components
import ArticleHeader from './components/ArticleHeader.vue'
import ArticleFooter from './components/ArticleFooter.vue'
import AuthorCard from './components/AuthorCard.vue'
import CommentSection from './components/CommentSection.vue'
import TocSidebar from './components/TocSidebar.vue'

const route = useRoute()
const router = useRouter()
const { updateMeta } = useSEO()
const articleStore = useArticleStore()
const userStore = useUserStore()
const { isDark } = useTheme()
const { currentArticle: article, loading } = storeToRefs(articleStore)

// 权限控制：管理员可编辑所有，普通用户只能编辑自己的文章
const isAdmin = computed(() => userStore.userInfo?.role === 'admin')
const canEdit = computed(() => {
  if (!userStore.userInfo || !article.value) return false
  if (isAdmin.value) return true
  return article.value.author.id === userStore.userInfo.id
})

// Extract TOC items from markdown content
const tocItems = computed(() => {
  if (!article.value?.content) return []

  const headings: { id: string; text: string; level: number }[] = []
  const reg = /^(#+)\s+(.+)$/gm
  let match

  while ((match = reg.exec(article.value.content)) !== null) {
    const level = (match[1] ?? '').length
    const text = match[2] ?? ''
    // Simple ID generation from text
    const id = text.toLowerCase().replace(/[^\w\u4e00-\u9fa5]+/g, '-')
    headings.push({ id, text, level })
  }

  return headings
})

onMounted(async () => {
  const id = Number(route.params.id)
  if (id) {
    await articleStore.fetchArticleDetail(id)
    if (article.value) {
      updateMeta(
        article.value.title,
        article.value.excerpt,
        article.value.tags.map((t) => t.name),
      )
    }
  }
})

// Watch for route changes to refetch data
watch(
  () => route.params.id,
  async (newId) => {
    if (newId) {
      await articleStore.fetchArticleDetail(Number(newId))
    }
  },
)

const handleEdit = () => {
  router.push(`/editor/${article.value?.id}`)
}
</script>

<style scoped lang="scss">
.article-detail {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-2xl);
  align-items: start;
}

.loading-container {
  padding: var(--space-xl);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
}

.article-detail__main {
  min-width: 0;
}

.article-detail__content {
  line-height: 1.85;
  color: var(--color-ink-2);
}

.article-detail__aside {
  display: none;
}

.article-missing {
  padding: var(--space-2xl) 0;
  text-align: center;
}

.article-missing__title {
  font-family: var(--font-display);
  font-size: var(--text-md);
  color: var(--color-ink);
}

.article-missing__hint {
  margin-top: var(--space-xs);
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

.article-missing__action {
  display: inline-block;
  margin-top: var(--space-lg);
  font-size: var(--text-sm);
  color: var(--color-accent);
}

.article-missing__action:hover {
  text-decoration: underline;
  text-underline-offset: 0.25em;
}

.article-detail__aside-sticky {
  position: sticky;
  top: calc(var(--header-h) + var(--space-lg));
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

@media (min-width: 60rem) {
  .article-detail {
    grid-template-columns: minmax(0, 1fr) 15rem;
  }

  .article-detail__aside {
    display: block;
  }
}
</style>
