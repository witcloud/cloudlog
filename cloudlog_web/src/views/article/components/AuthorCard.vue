<template>
  <section class="author-card">
    <RouterLink class="author-card__head" :to="`/authors/${author.id}`">
      <el-avatar :size="40" :src="formatMinioUrl(author.avatar) || defaultAvatarUrl" />
      <div class="author-card__intro">
        <h3 class="author-card__name">{{ author.username }}</h3>
        <p v-if="author.occupation" class="author-card__role">{{ author.occupation }}</p>
      </div>
    </RouterLink>
    <dl class="author-card__stats">
      <div class="author-card__stat">
        <dt>文章</dt>
        <dd class="tnum">{{ stats.articles ?? '—' }}</dd>
      </div>
      <div class="author-card__stat">
        <dt>粉丝</dt>
        <dd class="tnum">{{ author.followers ?? '—' }}</dd>
      </div>
      <div class="author-card__stat">
        <dt>阅读</dt>
        <dd class="tnum">{{ stats.views ?? '—' }}</dd>
      </div>
    </dl>
    <FollowButton
      v-if="!isSelf"
      class="author-card__follow"
      :followed="followed"
      :pending="followPending"
      @toggle="emit('toggle-follow')"
    />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Author } from '@/api/types/article'
import { formatMinioUrl, defaultAvatarUrl } from '@/config'
import { useUserStore } from '@/stores/user'
import FollowButton from './FollowButton.vue'

const props = defineProps<{
  author: Author
  // 全站口径：作者全部已发布文章的聚合。本篇计数在文章头与悬浮条，两者不得互相取数
  // 粉丝格不属此口径，直接读 author.followers
  stats: {
    articles?: number
    views?: number
  }
  followPending: boolean
}>()

const emit = defineEmits<{ 'toggle-follow': [] }>()

const userStore = useUserStore()

// 作者本人不出现关注入口；服务端另有兜底（自关注返回 400）
const isSelf = computed(() => userStore.userInfo?.id === props.author.id)
const followed = computed(() => props.author.followed ?? false)
</script>

<style scoped>
.author-card {
  padding: var(--space-md);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  background-color: var(--color-paper-2);
}

.author-card__head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.author-card__name {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-ink);
  transition: color var(--dur-micro) var(--ease-out);
}

.author-card__head:hover .author-card__name {
  color: var(--color-accent);
}

.author-card__role {
  margin-top: var(--space-3xs);
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.author-card__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-rule);
  text-align: center;
}

.author-card__stat dt {
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.author-card__stat dd {
  margin-top: var(--space-3xs);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-ink);
}

.author-card__follow {
  width: 100%;
  margin-top: var(--space-md);
}
</style>
