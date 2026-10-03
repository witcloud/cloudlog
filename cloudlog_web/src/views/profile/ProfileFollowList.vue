<template>
  <div v-loading="loading" class="follow-list">
    <ul v-if="rows.length > 0" class="follow-list__ul">
      <li v-for="row in rows" :key="row.id" class="follow-row">
        <RouterLink class="follow-row__avatar" :to="`/authors/${row.id}`">
          <el-avatar :size="40" :src="formatMinioUrl(row.avatar) || defaultAvatarUrl" />
        </RouterLink>
        <div class="follow-row__id">
          <p class="follow-row__name">
            <RouterLink :to="`/authors/${row.id}`">{{ row.username }}</RouterLink>
          </p>
          <p class="follow-row__bio">{{ row.bio || '还没有简介' }}</p>
        </div>
        <!-- 取关可撤销，按主流做法直接执行 + 一次性提示，不弹确认 -->
        <button
          v-if="kind === 'following'"
          class="follow-row__action"
          :disabled="pendingId === row.id"
          @click="unfollow(row.id)"
        >
          取消关注
        </button>
      </li>
    </ul>

    <div v-else-if="!loading" class="follow-list__empty">
      <p class="follow-list__empty-title">{{ emptyTitle }}</p>
      <p class="follow-list__empty-hint">{{ emptyHint }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { defaultAvatarUrl, formatMinioUrl } from '@/config'
import { getFollowers, getFollowing, type UserBrief } from '@/api/user'
import { unfollowAuthor } from '@/api/follow'

const props = defineProps<{ kind: 'following' | 'followers' }>()
const emit = defineEmits<{ changed: [] }>()

const rows = ref<UserBrief[]>([])
const loading = ref(false)
const pendingId = ref<string | null>(null)

const emptyTitle = computed(() =>
  props.kind === 'following' ? '还没有关注任何人。' : '还没有人关注你。',
)
const emptyHint = computed(() =>
  props.kind === 'following'
    ? '在文章页的作者卡片上点「关注」，对方就会出现在这里。'
    : '关注数会显示在文章页的作者卡片上。',
)

const fetchRows = async () => {
  loading.value = true
  try {
    const list = props.kind === 'following' ? getFollowing : getFollowers
    const { data } = await list({ page: 1, pageSize: 20 })
    rows.value = data.list
  } catch (error) {
    console.error('获取关注列表失败:', error)
  } finally {
    loading.value = false
  }
}

const unfollow = async (id: string) => {
  pendingId.value = id
  try {
    await unfollowAuthor(id)
    ElMessage.success('已取消关注')
    await fetchRows()
    emit('changed')
  } catch (error) {
    console.error('取消关注失败:', error)
  } finally {
    pendingId.value = null
  }
}

onMounted(fetchRows)
</script>

<style scoped lang="scss">
/* 地板是给 spinner 与空态留落点的，不是给已渲染列表的：有列表就让它贴着内容收 */
.follow-list:not(:has(ul)) {
  min-height: 6rem;
}

.follow-list__ul {
  display: flex;
  flex-direction: column;
}

.follow-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding-block: var(--space-md);

  & + .follow-row {
    border-top: 1px solid var(--color-rule);
  }

  /* 首末条的纵向留白交给面板承担，不与面板内衬叠成双层 */
  &:first-child {
    padding-top: 0;
  }

  &:last-child {
    padding-bottom: 0;
  }
}

.follow-row__id {
  flex: 1;
  min-width: 0;
}

.follow-row__avatar {
  display: flex;
  align-self: flex-start;
}

.follow-row__name {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--color-ink);

  a {
    transition: color var(--dur-micro) var(--ease-out);

    &:hover {
      color: var(--color-accent);
    }
  }
}

.follow-row__bio {
  margin: var(--space-3xs) 0 0;
  font-size: var(--text-xs);
  color: var(--color-ink-3);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.follow-row__action {
  flex-shrink: 0;
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  white-space: nowrap;
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
  }

  &:disabled {
    color: var(--color-ink-3);
  }
}

.follow-list__empty {
  padding: var(--space-2xl) 0;
  text-align: center;
}

.follow-list__empty-title {
  font-family: var(--font-display);
  font-size: var(--text-md);
  color: var(--color-ink);
}

.follow-list__empty-hint {
  margin-top: var(--space-xs);
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}
</style>
