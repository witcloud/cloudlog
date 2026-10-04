<template>
  <section class="comments">
    <header class="comments__head">
      <h2 class="comments__title">评论</h2>
      <span class="comments__count tnum">{{ commentCount }}</span>
    </header>

    <div v-if="!isLoggedIn" class="comments__login">
      <p class="comments__login-hint">登录后即可参与评论。</p>
      <el-button type="primary" plain @click="goLogin">去登录</el-button>
    </div>

    <div v-else class="comments__input">
      <el-input
        v-model="newComment"
        type="textarea"
        :rows="3"
        :maxlength="wordLimit"
        show-word-limit
        placeholder="说点什么吧…"
      />
      <div class="comments__actions">
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!newComment.trim() || cooldownLeft > 0"
          @click="submitComment()"
        >
          {{ cooldownLeft > 0 ? `请 ${cooldownLeft} 秒后再发` : '提交评论' }}
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="comments__area">
      <!-- 本地就有 loadComments()，不该把人支去刷新整页 -->
      <p v-if="loadFailed" class="comments__empty">
        评论加载失败。
        <button type="button" class="comments__retry" @click="loadComments">重试</button>
      </p>

      <ul v-else-if="threads.length" class="comments__list">
        <li v-for="thread in threads" :key="thread.id" class="comments__thread">
          <div class="comments__item">
            <RouterLink
              v-if="thread.user"
              class="comments__avatar"
              :to="`/authors/${thread.user.id}`"
            >
              <el-avatar :size="36" :src="avatarOf(thread)" />
            </RouterLink>
            <el-avatar v-else :size="36" :src="avatarOf(thread)" />
            <div class="comments__body">
              <div class="comments__meta">
                <!-- 匿名的评论者没有主页可去，保持纯文本 -->
                <RouterLink v-if="thread.user" class="comments__user" :to="`/authors/${thread.user.id}`">{{
                  nameOf(thread)
                }}</RouterLink>
                <span v-else class="comments__user">{{ nameOf(thread) }}</span>
                <time class="comments__time tnum" :datetime="thread.createdAt">
                  {{ formatTime(thread.createdAt) }}
                </time>
              </div>
              <p class="comments__text">{{ thread.content }}</p>
              <div v-if="isLoggedIn" class="comments__tools">
                <button
                  class="comments__tool"
                  type="button"
                  :disabled="submitting"
                  @click="toggleReply(thread.id)"
                >
                  {{ replyingTo === thread.id ? '取消回复' : '回复' }}
                </button>
              </div>
            </div>
          </div>

          <ul v-if="thread.replies.length" class="comments__replies">
            <li v-for="reply in thread.replies" :key="reply.id" class="comments__item">
              <RouterLink
                v-if="reply.user"
                class="comments__avatar"
                :to="`/authors/${reply.user.id}`"
              >
                <el-avatar :size="28" :src="avatarOf(reply)" />
              </RouterLink>
              <el-avatar v-else :size="28" :src="avatarOf(reply)" />
              <div class="comments__body">
                <div class="comments__meta">
                  <RouterLink v-if="reply.user" class="comments__user" :to="`/authors/${reply.user.id}`">{{
                    nameOf(reply)
                  }}</RouterLink>
                  <span v-else class="comments__user">{{ nameOf(reply) }}</span>
                  <time class="comments__time tnum" :datetime="reply.createdAt">
                    {{ formatTime(reply.createdAt) }}
                  </time>
                </div>
                <p class="comments__text">{{ reply.content }}</p>
              </div>
            </li>
          </ul>

          <div v-if="replyingTo === thread.id" class="comments__reply">
            <el-input
              v-model="replyContent"
              type="textarea"
              :rows="2"
              :maxlength="wordLimit"
              show-word-limit
              placeholder="回复这条评论…"
            />
            <div class="comments__actions">
              <el-button size="small" :disabled="submitting" @click="replyingTo = null">
                取消
              </el-button>
              <el-button
                size="small"
                type="primary"
                :loading="submitting"
                :disabled="!replyContent.trim() || cooldownLeft > 0"
                @click="submitComment(thread.id)"
              >
                {{ cooldownLeft > 0 ? `请 ${cooldownLeft} 秒后再发` : '发布回复' }}
              </el-button>
            </div>
          </div>
        </li>
      </ul>

      <p v-else-if="!loading" class="comments__empty">还没有评论，来发表第一条吧。</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { addComment, getComments } from '@/api/comment'
import type { Comment } from '@/api/types/comment'
import { useUserStore } from '@/stores/user'
import { useSiteStore } from '@/stores/site'
import { formatMinioUrl, defaultAvatarUrl } from '@/config'

const props = defineProps<{
  postId: string
  commentCount: number
}>()

/** 已过审评论落库后通知父级：文章页的评论计数（标题旁与悬浮角标）要同步 +1 */
const emit = defineEmits<{ approved: [] }>()

/** 根评论 + 其直接回复；回复不带子级，保证展示只有两级 */
interface Thread extends Comment {
  replies: Comment[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const siteStore = useSiteStore()

/** 限长以后端配置为准：0 表示不限制，此时不传 maxlength，字数提示也随之不出现 */
const wordLimit = computed(() => siteStore.commentMaxLength || undefined)

/**
 * 发布间隔倒计时。服务端对同一用户有最小发布间隔（随公开设置下发，缺省 60 秒），
 * 不显示的话按钮发完立刻又能点，第二发只能撞「评论发得太快」那条红字——
 * 而还剩几秒这件事，系统一开始就知道。
 */
const cooldownLeft = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

const stopCooldown = () => {
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
}

const startCooldown = () => {
  const seconds = siteStore.commentIntervalSeconds
  if (!seconds) return
  cooldownLeft.value = seconds
  stopCooldown()
  cooldownTimer = setInterval(() => {
    cooldownLeft.value -= 1
    if (cooldownLeft.value <= 0) stopCooldown()
  }, 1000)
}

onUnmounted(stopCooldown)

const comments = ref<Comment[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const submitting = ref(false)
const newComment = ref('')
const replyingTo = ref<number | null>(null)
const replyContent = ref('')

const isLoggedIn = computed(() => !!userStore.token)

/** 服务端返回「根 + 其直接回复」，先摊平成一条线性集合再重新归组 */
const flattened = computed<Comment[]>(() =>
  comments.value.flatMap((comment) => [comment, ...(comment.replies ?? [])]),
)

// parent_id = 0 为根；其父评论未通过审核的回复独立可见，按根平铺展示
const threads = computed<Thread[]>(() => {
  const roots: Thread[] = []
  const index = new Map<number, Thread>()
  const orphans: Thread[] = []

  for (const comment of flattened.value) {
    const thread: Thread = { ...comment, replies: [] }
    if (comment.parentId === 0) {
      index.set(comment.id, thread)
      roots.push(thread)
    } else {
      orphans.push(thread)
    }
  }

  for (const orphan of orphans) {
    const parent = index.get(orphan.parentId)
    if (parent) parent.replies.push(orphan)
    else roots.push(orphan)
  }

  return roots
})

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm')
const nameOf = (comment: Comment) => comment.user?.username || '匿名'
const avatarOf = (comment: Comment) => formatMinioUrl(comment.user?.avatar) || defaultAvatarUrl

const goLogin = () => {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

const loadComments = async () => {
  if (!props.postId) return
  loading.value = true
  try {
    const res = await getComments(props.postId)
    comments.value = res.data ?? []
    loadFailed.value = false
  } catch {
    // 拦截器已统一提示，此处只置失败态，不再拼一条同类提示
    comments.value = []
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

const toggleReply = (id: number) => {
  replyingTo.value = replyingTo.value === id ? null : id
  replyContent.value = ''
}

/** parentId 一律为根评论 id：回复某条回复时仍挂到它的根下 */
const submitComment = async (parentId = 0) => {
  const content = (parentId === 0 ? newComment.value : replyContent.value).trim()
  if (!isLoggedIn.value || !props.postId || submitting.value || !content) return

  submitting.value = true
  try {
    // 最终 status 以服务端返回为准，不以「重新拉取后是否多出一条」反推（business-model.md §4.4 第 4 条）
    const res = await addComment({ postId: props.postId, content, parentId })
    // 过审与转待审都已在服务端落库、都占这一次间隔，所以两条路一起进倒计时
    startCooldown()
    if (parentId === 0) newComment.value = ''
    else {
      replyingTo.value = null
      replyContent.value = ''
    }
    if (res.data === 'approved') {
      ElMessage.success('评论已发布。')
      await loadComments()
      emit('approved')
    } else {
      ElMessage.info('评论已提交，审核通过后展示。')
    }
  } catch {
    // 拦截器已统一提示，失败时保留输入内容
  } finally {
    submitting.value = false
  }
}

onMounted(loadComments)

watch(
  () => props.postId,
  () => {
    newComment.value = ''
    replyingTo.value = null
    replyContent.value = ''
    loadComments()
  },
)
</script>

<style scoped>
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

.comments__input,
.comments__login {
  margin-top: var(--space-lg);
}

.comments__login {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  background-color: var(--color-paper-2);
}

.comments__login-hint {
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

.comments__actions {
  margin-top: var(--space-sm);
  display: flex;
  justify-content: flex-end;
  gap: var(--space-xs);
}

.comments__area {
  margin-top: var(--space-xl);
  min-height: var(--space-2xl);
}

.comments__list {
  display: flex;
  flex-direction: column;
}

.comments__thread + .comments__thread {
  border-top: 1px solid var(--color-rule);
}

.comments__item {
  display: flex;
  gap: var(--space-md);
  padding-block: var(--space-md);
}

.comments__replies {
  margin-left: var(--space-xl);
  padding-left: var(--space-md);
  border-left: 1px solid var(--color-rule);
}

.comments__reply {
  margin: 0 0 var(--space-md) var(--space-xl);
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
  transition: color var(--dur-micro) var(--ease-out);
}

.comments__user:hover {
  color: var(--color-accent);
}

/* 头像本身没有可变的墨色，链接落点只给它光标与几何不变的包裹 */
.comments__avatar {
  display: flex;
  align-self: flex-start;
}

.comments__time {
  font-size: var(--text-xs);
  color: var(--color-ink-4);
}

.comments__text {
  /* 评论是用户生成的正文级内容，13px 压不住场，升正文字号（掘金评论 ≥14px） */
  margin-top: var(--space-2xs);
  font-size: var(--text-base);
  line-height: 1.75;
  color: var(--color-ink-2);
  white-space: pre-wrap;
}

.comments__tools {
  margin-top: var(--space-2xs);
}

.comments__tool {
  /* 它是「回复」的唯一入口。评论行里放一颗 44px 的按钮会把整行撑散，
     所以按最小命中档（24×24）收，而不是照抄表单按钮那一档 */
  display: inline-flex;
  align-items: center;
  min-height: 1.5rem;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  font-size: var(--text-xs);
  color: var(--color-ink-3);
  transition: color var(--dur-micro) var(--ease-out);
}

.comments__tool:hover {
  color: var(--color-accent);
}

.comments__tool:disabled {
  cursor: progress;
  color: var(--color-ink-4);
}

.comments__empty {
  padding-block: var(--space-lg);
  text-align: center;
  font-size: var(--text-sm);
  color: var(--color-ink-4);
}

/* 失败态里那颗重试：跟这行字同档，不自带控件外观 */
.comments__retry {
  border: 0;
  padding: 0;
  background: none;
  font-size: inherit;
  color: var(--color-accent-ink);
  cursor: pointer;

  &:hover {
    text-decoration: underline;
    text-underline-offset: 0.25em;
  }
}
</style>
