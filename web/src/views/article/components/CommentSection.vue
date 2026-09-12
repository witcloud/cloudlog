<template>
  <section class="comments">
    <header class="comments__head">
      <h2 class="comments__title">评论</h2>
      <span class="comments__count tnum">{{ commentCount }}</span>
    </header>

    <div class="comments__input">
      <el-input v-model="newComment" type="textarea" :rows="3" placeholder="说点什么吧…" />
      <div class="comments__actions">
        <el-button type="primary" @click="handleSubmit">提交评论</el-button>
      </div>
    </div>

    <ul class="comments__list">
      <li v-for="comment in comments" :key="comment.id" class="comments__item">
        <el-avatar :size="36" :src="formatMinioUrl(comment.avatar)" />
        <div class="comments__body">
          <div class="comments__meta">
            <span class="comments__user">{{ comment.user }}</span>
            <span class="comments__time tnum">{{ comment.time }}</span>
          </div>
          <p class="comments__text">{{ comment.text }}</p>
        </div>
      </li>
    </ul>
    <p v-if="comments.length === 0" class="comments__empty">还没有评论，来发表第一条吧。</p>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { formatMinioUrl } from '@/config'

defineProps<{
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

<style scoped>
.comments {
  margin-top: var(--space-2xl);
}

.comments__head {
  display: flex;
  align-items: baseline;
  gap: var(--space-xs);
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--color-rule);
}

.comments__title {
  font-size: var(--text-md);
}

.comments__count {
  font-size: var(--text-xs);
  color: var(--color-ink-4);
}

.comments__input {
  margin-top: var(--space-lg);
}

.comments__actions {
  margin-top: var(--space-sm);
  display: flex;
  justify-content: flex-end;
}

.comments__list {
  margin-top: var(--space-xl);
}

.comments__item {
  display: flex;
  gap: var(--space-md);
  padding-block: var(--space-md);
}

.comments__item + .comments__item {
  border-top: 1px solid var(--color-rule);
}

.comments__body {
  flex: 1;
  min-width: 0;
}

.comments__meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
}

.comments__user {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-ink);
}

.comments__time {
  font-size: var(--text-xs);
  color: var(--color-ink-4);
}

.comments__text {
  margin-top: var(--space-3xs);
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--color-ink-2);
}

.comments__empty {
  padding-block: var(--space-lg);
  text-align: center;
  font-size: var(--text-sm);
  color: var(--color-ink-4);
}
</style>
