<template>
  <el-card class="author-card" shadow="never">
    <div class="author-header">
      <el-avatar :size="64" :src="formatMinioUrl(author.avatar || defaultAvatar)" />
      <div class="author-info-text">
        <h3 class="author-name">{{ author.username }}</h3>
        <p class="author-title">前端开发工程师 / 博主</p>
      </div>
    </div>
    <p class="author-bio">专注于 Vue.js 和 TypeScript 领域，热衷于分享技术干货和开发心得。</p>
    <div class="author-stats">
      <div class="stat-item">
        <span class="label">文章</span>
        <span class="value">{{ stats.articles || 0 }}</span>
      </div>
      <div class="stat-item">
        <span class="label">获赞</span>
        <span class="value">{{ stats.likes || 0 }}</span>
      </div>
    </div>
    <el-button type="primary" class="follow-btn" plain>关注作者</el-button>
  </el-card>
</template>

<script setup lang="ts">
import type { Author } from '@/api/types/article'
import { formatMinioUrl } from '@/config'

const props = defineProps<{
  author: Author
  stats: {
    articles?: number
    likes?: number
  }
}>()
console.log('作者信息：', props.author)
console.log('统计信息：', props.stats)

const defaultAvatar = '/default-avatar.png'
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.author-card {
  border-radius: $border-radius-large;
  border: 1px solid $border-color;

  .author-header {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    margin-bottom: $spacing-md;

    .author-info-text {
      .author-name {
        margin: 0;
        font-size: 18px;
        font-weight: 700;
      }
      .author-title {
        margin: 4px 0 0;
        font-size: 13px;
        color: $text-secondary;
      }
    }
  }

  .author-bio {
    font-size: 14px;
    color: $text-regular;
    line-height: 1.6;
    margin: 0 0 $spacing-md;
  }

  .author-stats {
    display: flex;
    gap: $spacing-xl;
    padding: $spacing-md 0;
    border-top: 1px solid rgba($border-color, 0.5);
    margin-bottom: $spacing-md;

    .stat-item {
      display: flex;
      flex-direction: column;
      .label {
        font-size: 12px;
        color: $text-secondary;
      }
      .value {
        font-weight: 700;
        font-size: 16px;
      }
    }
  }

  .follow-btn {
    width: 100%;
  }
}
</style>
