<template>
  <div v-if="loading" class="loading-container">
    <el-skeleton :rows="10" animated />
  </div>
  <div v-else-if="article" class="article-detail">
    <el-row :gutter="24">
      <el-col :xs="24" :sm="24" :md="18" :offset="isAdmin ? 0 : 3" class="main-col">
        <el-card class="detail-card" shadow="never">
          <ArticleHeader :article="article" :can-edit="canEdit" @edit="handleEdit" />

          <div class="article-content">
            <md-preview :model-value="article.content" />
          </div>

          <ArticleFooter :author-name="article.author.username" :article-title="article.title" />
        </el-card>

        <CommentSection :comment-count="article.comments" />
      </el-col>

      <el-col :xs="0" :sm="0" :md="6">
        <div class="sidebar-wrapper">
          <AuthorCard :author="article.author" :stats="{ articles: 42, likes: article.likes }" />
          <TocSidebar :toc-items="tocItems" />
        </div>
      </el-col>
    </el-row>
  </div>
  <el-empty v-else description="文章不存在" />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useSEO } from '@/utils/seo'
import { useArticleStore } from '@/stores/article'
import { useUserStore } from '@/stores/user'
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

  const headings: any[] = []
  const reg = /^(#+)\s+(.+)$/gm
  let match

  while ((match = reg.exec(article.value.content)) !== null) {
    const level = match[1].length
    const text = match[2]
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
@use '@/styles/variables.scss' as *;

.article-detail {
  padding-bottom: $spacing-xl;
  max-width: 1200px;
  margin: 0 auto;
}

.loading-container {
  padding: $spacing-xl;
  background: #fff;
  border-radius: $border-radius-large;
}

.detail-card {
  border-radius: $border-radius-large;
  border: 1px solid $border-color;
  margin-bottom: $spacing-lg;
  transition: box-shadow 0.3s ease;

  &:hover {
    box-shadow: $box-shadow-light;
  }

  .article-content {
    padding: 0 $spacing-sm;
    line-height: 1.8;
    color: $text-regular;

    :deep(.md-editor-preview) {
      font-size: 16px;
    }
  }
}

.sidebar-wrapper {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

@media (max-width: 768px) {
  .main-col {
    offset: 0 !important;
  }
}
</style>
