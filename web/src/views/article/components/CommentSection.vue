<template>
  <el-card class="comments-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>评论 ({{ commentCount }})</span>
      </div>
    </template>
    <div class="comment-input">
      <el-input v-model="newComment" type="textarea" :rows="3" placeholder="说点什么吧..." />
      <div class="input-actions">
        <el-button type="primary" @click="handleSubmit">提交评论</el-button>
      </div>
    </div>
    <div class="comment-list">
      <div v-for="comment in comments" :key="comment.id" class="comment-item">
        <el-avatar :size="40" :src="formatMinioUrl(comment.avatar)" />
        <div class="comment-body">
          <div class="comment-header">
            <span class="comment-user">{{ comment.user }}</span>
            <span class="comment-time">{{ comment.time }}</span>
          </div>
          <div class="comment-text">{{ comment.text }}</div>
        </div>
      </div>
      <el-empty v-if="comments.length === 0" description="暂无评论" />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { formatMinioUrl } from '@/config'

const props = defineProps<{
  commentCount: number
}>()

const emit = defineEmits(['submit'])

const newComment = ref('')

// Mock comments for now as they are not in the main article API
const comments = ref([
  {
    id: 1,
    user: '张三',
    avatar: 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',
    time: '2小时前',
    text: '写得太棒了，非常有参考价值！',
  },
  {
    id: 2,
    user: '李四',
    avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png',
    time: '5小时前',
    text: '想请教一下关于 Vite 插件开发的问题。',
  },
])

const handleSubmit = () => {
  if (newComment.value.trim()) {
    const comment = {
      id: Date.now(),
      user: '当前用户',
      avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png',
      time: '刚刚',
      text: newComment.value,
    }
    comments.value.unshift(comment)
    newComment.value = ''
    emit('submit', comment)
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.comments-card {
  border-radius: $border-radius-large;
  border: 1px solid $border-color;

  .comment-input {
    margin-bottom: $spacing-xl;
    .input-actions {
      margin-top: $spacing-sm;
      display: flex;
      justify-content: flex-end;
    }
  }

  .comment-list {
    display: flex;
    flex-direction: column;
    gap: $spacing-lg;

    .comment-item {
      display: flex;
      gap: $spacing-md;

      .comment-body {
        flex: 1;
        .comment-header {
          display: flex;
          justify-content: space-between;
          margin-bottom: 4px;
          .comment-user {
            font-weight: 700;
            font-size: 14px;
          }
          .comment-time {
            font-size: 12px;
            color: $text-secondary;
          }
        }
        .comment-text {
          font-size: 14px;
          line-height: 1.6;
          color: $text-regular;
        }
      }
    }
  }
}
</style>
