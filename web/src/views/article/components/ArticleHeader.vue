<template>
  <header class="article-header">
    <h1 class="article-title">{{ article.title }}</h1>
    <div class="article-meta">
      <span class="meta-item">
        <el-icon><Calendar /></el-icon> 发布于 {{ formatDate(article.publishedAt) }}
      </span>
      <span class="meta-item">
        <el-icon><User /></el-icon> {{ article.author.username }}
      </span>
      <span class="meta-item">
        <el-icon><View /></el-icon> {{ article.views }} 次阅读
      </span>
      <el-button v-if="canEdit" type="primary" link @click="$emit('edit')">
        <el-icon><Edit /></el-icon> 编辑
      </el-button>
    </div>
    <div class="article-tags">
      <el-tag v-for="tag in article.tags" :key="tag.id" size="small" class="tag">
        {{ tag.name }}
      </el-tag>
    </div>
  </header>
</template>

<script setup lang="ts">
import { Calendar, User, View, Edit } from '@element-plus/icons-vue'
import type { Article } from '@/api/types/article'
import dayjs from 'dayjs'

defineProps<{
  article: Article
  canEdit: boolean
}>()

defineEmits(['edit'])

const formatDate = (date: string) => {
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.article-header {
  text-align: center;
  padding-bottom: $spacing-xl;
  border-bottom: 1px solid rgba($border-color, 0.5);
  margin-bottom: $spacing-xl;

  .article-title {
    font-size: 32px;
    font-weight: 800;
    margin-bottom: $spacing-lg;
    color: $text-primary;
  }

  .article-meta {
    display: flex;
    justify-content: center;
    gap: $spacing-lg;
    color: $text-secondary;
    font-size: 14px;
    margin-bottom: $spacing-md;

    .meta-item {
      display: flex;
      align-items: center;
      gap: 4px;
    }
  }

  .article-tags {
    display: flex;
    justify-content: center;
    gap: $spacing-sm;
  }
}

@media (max-width: 768px) {
  .article-header .article-title {
    font-size: 24px;
  }
}
</style>
