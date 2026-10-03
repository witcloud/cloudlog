<template>
  <div class="profile">
    <!-- 身份卡：抬升面 + 发丝线描边；上行身份与编辑入口，下行发丝线收住可点统计 -->
    <header class="profile__card">
      <div class="profile__row">
        <AvatarUpload
          :model-value="formatMinioUrl(user.avatar)"
          :size="76"
          @update:model-value="(val) => (user.avatar = val)"
          @success="handleAvatarSuccess"
        />
        <div class="profile__id">
          <h1 class="profile__name">{{ user.username }}</h1>
          <p v-if="user.occupation" class="profile__occupation">{{ user.occupation }}</p>
          <p v-if="user.bio" class="profile__bio">{{ user.bio }}</p>
        </div>
        <button class="profile__edit" @click="editProfile">编辑资料</button>
      </div>

      <div class="profile__stats">
        <button
          class="profile__stat"
          type="button"
          aria-label="查看我关注的用户"
          @click="tab = 'following'"
        >
          <span class="profile__stat-value tnum">{{ stats.followingCount }}</span>
          <span class="profile__stat-label">关注</span>
        </button>
        <button
          class="profile__stat"
          type="button"
          aria-label="查看关注我的用户"
          @click="tab = 'followers'"
        >
          <span class="profile__stat-value tnum">{{ stats.followersCount }}</span>
          <span class="profile__stat-label">粉丝</span>
        </button>
      </div>
    </header>

    <!-- 四个选项卡：我的文章 / 关注 / 粉丝 / 点赞 -->
    <div class="profile__tabs-host" :class="{ 'is-searching': headerHidden }">
      <!-- 搜索态：整条选项卡换成搜索条，返回即退出并把搜索条件清掉（留着条件就没法回到没筛的那张列表） -->
      <transition name="psearch" @after-leave="onBarLeft">
        <div v-if="searchMode" class="profile__search-bar">
        <button type="button" class="profile__search-back" @click="exitSearch">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true">
            <path
              d="M10 3 5 8l5 5"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          返回
        </button>
        <span class="profile__search-divider" aria-hidden="true"></span>
        <p class="profile__search-title">搜索文章</p>
        <form class="profile__search-field" role="search" @submit.prevent>
          <input
            ref="searchInputRef"
            v-model="postsKeyword"
            type="search"
            class="profile__search-input"
            placeholder="输入文章标题…"
            aria-label="按标题搜索文章"
            @keydown.esc="exitSearch"
          />
          <svg
            class="profile__search-icon"
            viewBox="0 0 16 16"
            width="14"
            height="14"
            fill="none"
            aria-hidden="true"
          >
            <path
              d="m10.5 10.5 3 3M7 11.5a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9Z"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
            />
          </svg>
        </form>
      </div>
      </transition>

      <!-- 入口把手：常驻只有这一枚图标，点了才换成上面那条 -->
      <button
        v-if="!searchMode && tab === 'posts'"
        type="button"
        class="profile__search-handle"
        aria-label="搜索文章"
        @click="openSearch"
      >
        <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
          <path
            d="m10.5 10.5 3 3M7 11.5a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9Z"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
          />
        </svg>
      </button>

      <el-tabs v-model="tab" class="profile__tabs">
      <el-tab-pane label="我的文章" name="posts">

        <div v-loading="postsLoading || postsStale" class="profile__pane">
          <ul v-if="posts.length > 0" class="post-list">
            <li v-for="post in posts" :key="post.id" class="post-row">
              <div class="post-row__main">
                <h3 class="post-row__title" @click="router.push(`/articles/${post.id}`)">
                  {{ post.title || '无标题' }}
                </h3>
                <p class="post-row__meta">
                  <!-- 已下线的文章只有本人看得到这一行：读者访问同一主页走公开列表，压根不含它 -->
                  <template v-if="post.status === 'offline'">
                    <span class="post-flag"><i class="post-flag__dot" aria-hidden="true"></i>已下线</span>
                    <span class="post-row__divider" aria-hidden="true"></span>
                  </template>
                  <span>{{ post.category?.name || '未分类' }}</span>
                  <span class="post-row__divider" aria-hidden="true"></span>
                  <PostCounts
                    :views="post.views"
                    :comments="post.comments"
                    :likes="post.likes"
                  />
                </p>
              </div>
              <div class="post-row__actions">
                <!-- 徽标在这一行，开关也该在这一行：撤可见性不必绕进编辑器。
                     写接口是管理员闸，非作者角色点了只会 403，所以不给这个按钮而不是给了再报错 -->
                <button
                  v-if="isAdmin && (post.status === 'published' || post.status === 'offline')"
                  class="post-action"
                  :disabled="togglingId === post.id"
                  @click="toggleVisibility(post)"
                >
                  {{
                    togglingId === post.id
                      ? '处理中…'
                      : post.status === 'offline'
                        ? '上线'
                        : '下线'
                  }}
                </button>
                <button class="post-action" @click="router.push(`/articles/${post.id}`)">
                  阅读
                </button>
              </div>
            </li>
          </ul>

          <div v-else-if="!postsLoading" class="empty-state">
            <template v-if="postsKeyword">
              <p class="empty-state__title">没搜到「{{ postsKeyword }}」。</p>
              <p class="empty-state__hint">换个词试试。</p>
              <button class="empty-state__action" @click="postsKeyword = ''">清除搜索</button>
            </template>
            <template v-else>
              <p class="empty-state__title">还没有文章。</p>
              <p class="empty-state__hint">第一篇文章正在等你落笔。</p>
              <button v-if="isAdmin" class="empty-state__action" @click="router.push('/editor')"
                >开始写作</button
              >
            </template>
          </div>

          <!-- 列表与搜索词不匹配的那段时间里不追加，否则旧条件的下一页会串进新列表 -->
          <div
            v-if="posts.length > 0 && !postsStale"
            ref="postsSentinel"
            class="load-sentinel"
            aria-live="polite"
          >
            <span v-if="postsLoadingMore">加载中…</span>
            <span v-else-if="postsTotal > POSTS_PAGE_SIZE && !postsHasMore">没有更多了</span>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="关注" name="following">
        <div class="profile__pane">
          <ProfileFollowList kind="following" @changed="fetchStats" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="粉丝" name="followers">
        <div class="profile__pane">
          <ProfileFollowList kind="followers" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="点赞" name="likes">
        <div v-loading="likesLoading" class="profile__pane">
          <ul v-if="liked.length > 0" class="post-list">
            <li v-for="post in liked" :key="post.id" class="post-row">
              <div class="post-row__main">
                <h3 class="post-row__title" @click="router.push(`/articles/${post.id}`)">
                  {{ post.title || '无标题' }}
                </h3>
                <p class="post-row__meta">
                  <span>{{ post.category?.name || '未分类' }}</span>
                  <span class="post-row__divider" aria-hidden="true"></span>
                  <PostCounts
                    :views="post.views"
                    :comments="post.comments"
                    :likes="post.likes"
                  />
                </p>
              </div>
              <div class="post-row__actions">
                <button class="post-action" @click="router.push(`/articles/${post.id}`)">
                  阅读
                </button>
              </div>
            </li>
          </ul>

          <div v-else-if="!likesLoading" class="empty-state">
            <p class="empty-state__title">还没有点赞过文章。</p>
            <p class="empty-state__hint">在文章页点「点赞」，它就会出现。</p>
          </div>
        </div>
      </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 编辑资料对话框：沿用原有保存逻辑 -->
    <el-dialog v-model="editVisible" title="编辑资料" width="500px">
      <div class="edit-profile-content">
        <div class="avatar-edit-section">
          <AvatarUpload
            :model-value="formatMinioUrl(editForm.avatar)"
            @update:model-value="(val) => (editForm.avatar = val)"
            :size="100"
            show-btn
            @success="handleAvatarSuccess"
          />
        </div>

        <el-form
          :model="editForm"
          :rules="rules"
          ref="formRef"
          label-position="top"
          class="edit-form"
          @submit.prevent="saveProfile"
        >
          <el-form-item label="用户名" prop="username">
            <el-input v-model="editForm.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="职业" prop="occupation">
            <el-select
              v-model="editForm.occupation"
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="选择职业，或直接输入自定义"
              style="width: 100%"
            >
              <el-option v-for="item in occupations" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="个人简介" prop="bio">
            <el-input
              v-model="editForm.bio"
              type="textarea"
              :rows="3"
              placeholder="介绍一下你自己吧"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveProfile">保存修改</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { formatMinioUrl } from '@/config'
import { ref, reactive, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import AvatarUpload from '@/components/AvatarUpload.vue'
import ProfileFollowList from './ProfileFollowList.vue'
import {
  getLikedPosts,
  getProfileStats,
  getUserProfile,
  updateUserProfile,
  type UserProfile,
  type EditUserProfile,
} from '@/api/user'
import { getMyPosts, updateArticle } from '@/api/article'
import type { PostVO } from '@/api/types/article'
import { useUserStore } from '@/stores/user'
import { debounce } from 'lodash-es'
import PostCounts from '@/components/PostCounts.vue'

const router = useRouter()
const userStore = useUserStore()

const user = reactive<UserProfile>({
  username: '',
  email: '',
  avatar: '',
  bio: '',
  occupation: '',
})

type ProfileTab = 'posts' | 'following' | 'followers' | 'likes'

const tab = ref<ProfileTab>('posts')
const stats = reactive({ followingCount: 0, followersCount: 0 })

const editVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

// 职业快捷入口，不是数据约束：控件带 allow-create，列表外的自定义值同样可提交
const occupations = [
  '前端工程师',
  '后端工程师',
  '全栈工程师',
  '客户端工程师',
  '测试工程师',
  '运维工程师',
  '算法工程师',
  '产品经理',
  '设计师',
  '运营',
  '学生',
  '自由职业',
]

// 表单校验规则
const rules = {
  // 用户名与后端 UserProfileUpdateDTO 同档，且与注册页同一套规则（后端两处共用 UsernameRules）
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 30, message: '用户名长度需在 2-30 位之间', trigger: 'blur' },
    {
      pattern: /^[\u4e00-\u9fa5a-zA-Z0-9_-]+$/,
      message: '用户名仅支持中文、字母、数字、下划线和中划线',
      trigger: 'blur',
    },
  ],
  occupation: [{ max: 30, message: '职业长度不能超过 30 个字符', trigger: 'change' }],
  bio: [{ max: 100, message: '长度不能超过 100 个字符', trigger: 'blur' }],
}

// 我的文章：已发布 + 已下线（后者按 status 打徽标，只有本人能看到这一页）；草稿与定时归草稿箱。
// 滚动追加，哨兵机制与首页同一套
const POSTS_PAGE_SIZE = 10
const posts = ref<PostVO[]>([])
const postsKeyword = ref('')
const postsPage = ref(1)
const postsTotal = ref(0)
const postsExhausted = ref(false)
const postsLoading = ref(false)
const postsLoadingMore = ref(false)
const postsSentinel = ref<HTMLElement>()
/** 搜索词已改、结果还没换过来：这段时间既不算「没有更多」，也不许追加 */
const postsStale = ref(false)

const postsHasMore = computed(
  () => !postsStale.value && !postsExhausted.value && posts.value.length < postsTotal.value,
)

/** 每次拉完都重新观察一次哨兵：observe() 必定异步回调一次当前相交状态，
 *  而「新进入视口」在首屏不必滚动时根本不会发生——不复查就会卡在第一条之后 */
let postsObserver: IntersectionObserver | undefined

const observePostsSentinel = () => {
  if (!postsObserver) return
  postsObserver.disconnect()
  if (postsSentinel.value) postsObserver.observe(postsSentinel.value)
}

const fetchPosts = async (append = false): Promise<boolean> => {
  const keyword = postsKeyword.value
  if (append) postsLoadingMore.value = true
  else postsLoading.value = true
  try {
    const { data } = await getMyPosts({
      page: postsPage.value,
      pageSize: POSTS_PAGE_SIZE,
      keyword: keyword || null,
    })
    // 请求期间搜索词已变：这一页属于旧条件，丢掉，否则会串进新列表
    if (keyword !== postsKeyword.value) return false
    if (!append) postsExhausted.value = false
    posts.value = append ? [...posts.value, ...data.list] : data.list
    postsTotal.value = Number(data.total)
    if (data.list.length === 0) postsExhausted.value = true
    postsStale.value = false
    return true
  } catch (error) {
    console.error('获取文章列表失败:', error)
    return false
  } finally {
    postsLoading.value = false
    postsLoadingMore.value = false
    observePostsSentinel()
  }
}

const loadMorePosts = async () => {
  if (postsLoading.value || postsLoadingMore.value || !postsHasMore.value) return
  const keyword = postsKeyword.value
  postsPage.value += 1
  const ok = await fetchPosts(true)
  // 仅在搜索词未变时回退页码；已变则页码被重置为 1，回退会退到 0
  if (!ok && keyword === postsKeyword.value) postsPage.value -= 1
}

const searchPosts = debounce(() => {
  postsPage.value = 1
  fetchPosts()
}, 800)

watch(postsKeyword, () => {
  postsStale.value = true
  searchPosts()
})

/** 写接口 /articles/{id} 是管理员闸；非管理员点了只会吃 403，所以干脆不给这个按钮 */
const isAdmin = computed(() => userStore.userInfo?.role === 'admin')

const togglingId = ref<string | null>(null)

/**
 * 就地上下线：只带 status 走 PUT 的部分更新——不动正文、不动首发时刻，也抹不掉下架留痕。
 * 成功后就地改本地状态：这一列同时收 published 与 offline，换状态不会让它离开列表。
 */
const toggleVisibility = async (post: PostVO) => {
  const next = post.status === 'offline' ? 'published' : 'offline'
  if (next === 'offline') {
    try {
      await ElMessageBox.confirm('下线后读者将无法访问此文章，可随时重新上线', '下线文章', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return // 取消
    }
  }

  togglingId.value = post.id
  try {
    await updateArticle(post.id, { status: next })
    post.status = next
    ElMessage.success(next === 'offline' ? '文章已下线' : '文章已重新上线')
  } catch (error) {
    console.error('更新文章可见性失败:', error) // 失败原因由响应拦截器统一提示
  } finally {
    togglingId.value = null
  }
}

/** 搜索态：整条选项卡换成搜索条，所以进入要抢焦点、退出必须连条件一起清掉 */
const searchMode = ref(false)
/** 头带什么时候回来：等搜索条走完退场再让位，否则两段动画会叠在同一行上 */
const headerHidden = ref(false)
const searchInputRef = ref<HTMLInputElement>()

const openSearch = async () => {
  headerHidden.value = true
  searchMode.value = true
  await nextTick()
  searchInputRef.value?.focus()
}

const exitSearch = () => {
  searchMode.value = false
  if (postsKeyword.value) {
    postsKeyword.value = ''
    // 词变了会排一次防抖；返回不等 800ms，排好就立刻执行掉
    nextTick(() => searchPosts.flush())
  } else {
    // 没输过词时 watcher 不会响，这一支就得自己拉；顺手撤掉「删字删到空」那一次还在排的防抖
    searchPosts.cancel()
    postsPage.value = 1
    fetchPosts()
  }
}

const onBarLeft = () => {
  // 退场途中又点开的话，别把头带塞回去
  if (!searchMode.value) headerHidden.value = false
}

const fetchStats = async () => {
  try {
    const { data } = await getProfileStats()
    stats.followingCount = data.followingCount
    stats.followersCount = data.followersCount
  } catch (error) {
    console.error('获取关注计数失败:', error)
  }
}

// 点赞列表在选项卡首次展开时才取；el-tab-pane 自身是懒渲染的，数据要跟着懒加载才不空转
const liked = ref<PostVO[]>([])
const likesLoading = ref(false)
let likesLoaded = false

const fetchLiked = async () => {
  likesLoading.value = true
  try {
    const { data } = await getLikedPosts({ page: 1, pageSize: 20 })
    liked.value = data.list
    likesLoaded = true
  } catch (error) {
    console.error('获取点赞列表失败:', error)
  } finally {
    likesLoading.value = false
  }
}

watch(tab, (value) => {
  if (value === 'likes' && !likesLoaded) fetchLiked()
})

const editForm = reactive<EditUserProfile>({
  username: '',
  avatar: '',
  bio: '',
  occupation: '',
})

// 初始化加载数据
const fetchProfile = async () => {
  try {
    const res = await getUserProfile()
    if (res.code === 200 && res.data) {
      Object.assign(user, res.data)
    }
  } catch (error) {
    console.error('Failed to fetch profile:', error)
  }
}

onMounted(() => {
  postsObserver = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) loadMorePosts()
    },
    { rootMargin: '300px' },
  )
  fetchProfile()
  fetchPosts()
  fetchStats()
})

onUnmounted(() => {
  postsObserver?.disconnect()
})

const editProfile = () => {
  Object.assign(editForm, {
    username: user.username,
    avatar: user.avatar,
    bio: user.bio,
    occupation: user.occupation || '',
  })
  editVisible.value = true
}

const saveProfile = async () => {
  if (!formRef.value || saving.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        // 清空职业要提交空串而不是 undefined：请求体里缺这个键时后端按「不改这一列」处理，旧值会留着
        const occupation = editForm.occupation || ''
        const res = await updateUserProfile({
          username: editForm.username,
          bio: editForm.bio,
          occupation,
        })
        if (res.code === 200) {
          ElMessage.success('个人资料已更新')
          user.username = editForm.username
          user.bio = editForm.bio
          user.occupation = occupation

          if (userStore.userInfo) {
            // 走 store 的 updateCachedUser：缓存必须与 token 落在同一个存储，
            // 外部直接 setItem(localStorage) 会在「未记住我」的会话里留下孤儿缓存
            userStore.updateCachedUser({
              ...userStore.userInfo,
              username: editForm.username,
              avatar: editForm.avatar,
            })
          }

          editVisible.value = false
        }
      } catch (error) {
        console.error('Failed to update profile:', error)
      } finally {
        saving.value = false
      }
    }
  })
}

const handleAvatarSuccess = (url: string) => {
  user.avatar = url
  editForm.avatar = url

  if (userStore.userInfo) {
    userStore.updateCachedUser({ ...userStore.userInfo, avatar: url })
  }
}
</script>

<style scoped lang="scss">
.profile {
  padding-bottom: var(--space-xl);
}

/* ---- 身份卡：抬升面 + 发丝线描边 + 一枚极轻阴影（卡片范式见 docs/ui-design.md:311） ---- */
.profile__card {
  padding: var(--space-lg);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
}

.profile__row {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: var(--space-lg);
}

.profile__id {
  flex: 1;
  min-width: 0;
}

.profile__name {
  margin: var(--space-2xs) 0 0;
  font-size: var(--text-lg);
  line-height: 1.35;
  color: var(--color-ink);
}

.profile__occupation {
  margin: var(--space-3xs) 0 0;
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.profile__bio {
  margin: var(--space-2xs) 0 0;
  max-width: 36rem;
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--color-ink-2);
}

.profile__edit {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  align-self: flex-start;
  height: 2rem;
  margin-top: var(--space-2xs);
  padding-inline: var(--space-sm);
  font-size: var(--text-sm);
  color: var(--color-ink);
  border: 1px solid var(--color-rule-strong);
  border-radius: var(--radius-sm);
  background-color: var(--color-paper-2);
  white-space: nowrap;
  transition:
    border-color var(--dur-micro) var(--ease-out),
    color var(--dur-micro) var(--ease-out);

  &:hover {
    border-color: var(--color-accent);
    color: var(--color-accent);
  }
}

/* 统计：数字在上（等宽）、标签在下，整块可点切到对应选项卡 */
.profile__stats {
  display: flex;
  gap: var(--space-xl);
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-rule);
}

.profile__stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3xs);
}

.profile__stat-value {
  font-size: var(--text-md);
  font-weight: 700;
  line-height: 1.2;
  color: var(--color-ink);
  transition: color var(--dur-micro) var(--ease-out);
}

.profile__stat-label {
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.profile__stat:hover .profile__stat-value {
  color: var(--color-accent);
}

/* ---- 选项卡 ---- */
/* 选项卡行是搜索把手的定位父级 */
.profile__tabs-host {
  position: relative;
  margin-top: var(--space-xl);
}

.profile__tabs {
  margin-top: 0;
}

/* 头带只是让位、不撤：display:none 会在退场结束前空出一帧，整张列表会上顶一下 */
.profile__tabs-host.is-searching :deep(.el-tabs__header) {
  visibility: hidden;
}

.profile__search-handle {
  position: absolute;
  inset-block-start: 0;
  inset-inline-end: 0;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 2.5rem;
  height: 2.5rem;
  color: var(--color-ink-3);
  border-radius: var(--radius-sm);
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
  }
}

/* 把手钉在右端，所以搜索条从把手那个位置向左展开、退场原路收回；时长与路由淡入同档 */
.psearch-enter-active,
.psearch-leave-active {
  transition:
    opacity 150ms var(--ease-out),
    transform 150ms var(--ease-out);
}

.psearch-enter-from,
.psearch-leave-to {
  opacity: 0;
  transform: translateX(0.75rem);
}

/* 盖在让位的头带上：同高同描边位置，所以切换时下面一行像素都不动 */
.profile__search-bar {
  position: absolute;
  inset-block-start: 0;
  inset-inline: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  height: 2.5rem;
  background-color: var(--color-paper);
  border-bottom: 1px solid var(--color-rule);
}

.profile__search-back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3xs);
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  white-space: nowrap;
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
  }
}

.profile__search-divider {
  width: 1px;
  height: 1em;
  background-color: var(--color-rule-strong);
}

.profile__search-title {
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--color-ink);
  white-space: nowrap;
}

.profile__search-field {
  position: relative;
  display: flex;
  align-items: center;
  width: 20rem;
  min-width: 0;
  margin-inline-start: auto;
}

.profile__search-input {
  width: 100%;
  height: 2rem;
  padding-inline: var(--space-sm) calc(var(--space-sm) + 1.5rem);
  font-size: var(--text-sm);
  font-family: var(--font-body);
  color: var(--color-ink);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  transition: border-color var(--dur-micro) var(--ease-out);

  &:focus {
    outline: none;
    border-color: var(--color-rule-strong);
  }

  &::placeholder {
    color: var(--color-ink-4);
  }

  &::-webkit-search-cancel-button {
    -webkit-appearance: none;
  }
}

.profile__search-icon {
  position: absolute;
  inset-inline-end: var(--space-sm);
  color: var(--color-ink-4);
  pointer-events: none;
}

/* 四个选项卡共用同一套抬升面板配方（卡片范式见 docs/ui-design.md:311） */
.profile__pane {
  padding: var(--space-md) var(--space-lg);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
}

/* 8rem 的地板是给 v-loading 留spinner 落点与空态留位用的，不是给已渲染列表的：
   有列表就让它贴着内容收，否则一行的 tab 下面挂着约 50px 空面 */
.profile__pane:not(:has(ul)) {
  min-height: 8rem;
}

.post-list {
  display: flex;
  flex-direction: column;
}

.post-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  padding-block: var(--space-md);

  & + .post-row {
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

.post-row__main {
  flex: 1;
  min-width: 0;
}

.post-row__title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 700;
  line-height: 1.5;
  color: var(--color-ink);
  cursor: pointer;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
  }
}

.post-row__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-xs);
  margin-top: var(--space-2xs);
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

/* 窄屏宁可整项换行，也不让日期与计数在词内断开 */
.post-row__meta > * {
  white-space: nowrap;
}

/* 计数：图标 + 数字，无点号；组间一档、图标与数字之间一小档（与首页卡片同一套） */
.post-row__divider {
  width: 1px;
  height: 1em;
  background-color: currentColor;
}

/* 已下线徽标：与草稿箱的状态徽标同一套（6px 圆点 + 文案），功能色仅作提示 */
.post-flag {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2xs);
  color: var(--color-danger);
  white-space: nowrap;
}

.post-flag__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: currentColor;
  flex-shrink: 0;
}

.post-row__actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  flex-shrink: 0;
}

.post-action {
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  white-space: nowrap;
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
    text-decoration: underline;
    text-underline-offset: 0.25em;
  }
}

/* 滚动追加的哨兵，与首页同一套文案与配方 */
.load-sentinel {
  padding-top: var(--space-md);
  font-size: var(--text-sm);
  color: var(--color-ink-4);
  text-align: center;

  /* 首屏与空态都不需要这行留白撑高 */
  &:empty {
    padding-top: 0;
  }
}

/* ---- 空状态：与首页同一套 ---- */
.empty-state {
  padding: var(--space-2xl) 0;
  text-align: center;

  &__title {
    font-family: var(--font-display);
    font-size: var(--text-md);
    color: var(--color-ink);
  }

  &__hint {
    margin-top: var(--space-xs);
    font-size: var(--text-sm);
    color: var(--color-ink-3);
  }

  &__action {
    margin-top: var(--space-lg);
    font-size: var(--text-sm);
    color: var(--color-accent);

    &:hover {
      text-decoration: underline;
      text-underline-offset: 0.25em;
    }
  }
}

/* ---- 编辑资料对话框 ---- */
.edit-profile-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);

  .avatar-edit-section {
    padding: var(--space-md) 0;
    border-bottom: 1px solid var(--color-rule);
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-md);
}

/* ---- 断点 ---- */
@media (max-width: 40rem) {
  /* 编辑入口收到自己一行通栏；头像与姓名仍并排（P6：只改位置与密度，不删内容） */
  .profile__edit {
    flex: 1 1 100%;
    justify-content: center;
  }

  .post-row {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-xs);
  }

  .post-row__actions {
    justify-content: flex-end;
  }
}
</style>
