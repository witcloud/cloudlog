<template>
  <div class="author-profile">
    <template v-if="profile">
      <!-- 身份卡：与个人主页同一结构，但只读——编辑入口只在 /profile -->
      <header class="author-profile__card">
        <div class="author-profile__row">
          <el-avatar :size="76" :src="formatMinioUrl(profile.avatar) || defaultAvatarUrl" />
          <div class="author-profile__id">
            <h1 class="author-profile__name">{{ profile.username }}</h1>
            <p v-if="profile.bio" class="author-profile__bio">{{ profile.bio }}</p>
          </div>
          <el-button
            v-if="!isSelf"
            class="author-profile__follow"
            :class="{ 'is-followed': followed }"
            type="primary"
            plain
            :loading="followPending"
            @click="toggleFollow"
            >{{ followed ? '取消关注' : '关注' }}</el-button
          >
        </div>

        <!-- 三格与文章页作者卡同一套口径与顺序：都是「该作者全部已发布文章」的聚合 -->
        <dl class="author-profile__stats">
          <div class="author-profile__stat">
            <dt>文章</dt>
            <dd class="tnum">{{ profile.articleCount ?? '—' }}</dd>
          </div>
          <div class="author-profile__stat">
            <dt>粉丝</dt>
            <dd class="tnum">{{ profile.followers ?? '—' }}</dd>
          </div>
          <div class="author-profile__stat">
            <dt>阅读</dt>
            <dd class="tnum">{{ profile.viewCount ?? '—' }}</dd>
          </div>
        </dl>
      </header>

      <!-- 四栏：TA 的文章 / 关注中 / 粉丝 / 点赞。行式与个人页一致，两栏行内带关注按钮 -->
      <!-- 选项卡行是搜索把手的定位父级；结构与个人页 .profile__tabs-host 同一份 -->
      <div class="author-profile__tabs-host" :class="{ 'is-searching': headerHidden }">
        <!-- 搜索态：整条选项卡换成搜索条，返回即退出并把搜索条件清掉 -->
        <transition name="psearch" @after-leave="onBarLeft">
          <div v-if="searchMode" class="author-profile__search-bar">
            <button type="button" class="author-profile__search-back" @click="exitSearch">
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
            <span class="author-profile__search-divider" aria-hidden="true"></span>
            <p class="author-profile__search-title">搜索 TA 的文章</p>
            <form class="author-profile__search-field" role="search" @submit.prevent>
              <input
                ref="searchInputRef"
                v-model="keyword"
                type="search"
                class="author-profile__search-input"
                placeholder="输入标题或正文关键词…"
                aria-label="按关键词搜索 TA 的文章"
                @keydown.esc="exitSearch"
              />
              <svg
                class="author-profile__search-icon"
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
          class="author-profile__search-handle"
          aria-label="搜索 TA 的文章"
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
        <el-tabs v-model="tab" class="author-profile__tabs">
          <el-tab-pane label="TA 的文章" name="posts">
            <div v-loading="(loading && posts.length === 0) || postsStale" class="author-profile__pane">
              <ul v-if="posts.length > 0" class="post-list">
                <li
                  v-for="post in posts"
                  :key="post.id"
                  class="post-row"
                  @click="goToArticle(post.id)"
                >
                  <div class="post-row__main">
                    <h3 class="post-row__title">{{ post.title || '无标题' }}</h3>
                    <p class="post-row__meta">
                      <span>{{ post.category?.name || '未分类' }}</span>
                      <span class="post-row__divider" aria-hidden="true"></span>
                      <PostCounts :views="post.views" :comments="post.comments" :likes="post.likes" />
                    </p>
                  </div>
                </li>
              </ul>

              <div v-else-if="!loading && !loadingMore && !postsStale" class="empty-state">
                <template v-if="keyword">
                  <p class="empty-state__title">没搜到「{{ keyword }}」。</p>
                  <p class="empty-state__hint">换个词试试。</p>
                  <button class="empty-state__action" @click="keyword = ''">清除搜索</button>
                </template>
                <template v-else>
                  <p class="empty-state__title">{{ profile.username }} 还没有发布文章。</p>
                  <p class="empty-state__hint">等 TA 写下第一篇。</p>
                </template>
              </div>

              <!-- 列表与搜索词不匹配的那段时间里不追加，否则旧条件的下一页会串进新列表 -->
              <div
                v-if="posts.length > 0 && !postsStale"
                ref="sentinel"
                class="load-sentinel"
                aria-live="polite"
              >
                <span v-if="loadingMore">加载中…</span>
                <!-- 只有一屏时不必宣告「没有更多」：与个人页同一条件 -->
                <span v-else-if="total > PAGE_SIZE && !hasMore">没有更多了</span>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="关注中" name="following">
            <div v-loading="listLoading.following" class="author-profile__pane">
              <ul v-if="following.length > 0" class="follow-list">
                <li v-for="row in following" :key="row.id" class="follow-row">
                  <RouterLink class="follow-row__avatar" :to="`/authors/${row.id}`">
                    <el-avatar :size="40" :src="formatMinioUrl(row.avatar) || defaultAvatarUrl" />
                  </RouterLink>
                  <div class="follow-row__id">
                    <p class="follow-row__name">
                      <RouterLink :to="`/authors/${row.id}`">{{ row.username }}</RouterLink>
                    </p>
                    <p class="follow-row__bio">{{ row.bio || '还没有简介' }}</p>
                  </div>
                  <button
                    v-if="row.id !== viewerId"
                    class="follow-row__action"
                    :disabled="pendingRow === row.id"
                    @click="toggleRowFollow(row)"
                  >
                    {{ rowFollowLabel(row) }}
                  </button>
                </li>
              </ul>

              <div v-else-if="!listLoading.following" class="empty-state">
                <p class="empty-state__title">{{ profile.username }} 还没有关注任何人。</p>
                <p class="empty-state__hint">TA 在这里关注的人会出现这一栏。</p>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="粉丝" name="followers">
            <div v-loading="listLoading.followers" class="author-profile__pane">
              <ul v-if="fans.length > 0" class="follow-list">
                <li v-for="row in fans" :key="row.id" class="follow-row">
                  <RouterLink class="follow-row__avatar" :to="`/authors/${row.id}`">
                    <el-avatar :size="40" :src="formatMinioUrl(row.avatar) || defaultAvatarUrl" />
                  </RouterLink>
                  <div class="follow-row__id">
                    <p class="follow-row__name">
                      <RouterLink :to="`/authors/${row.id}`">{{ row.username }}</RouterLink>
                    </p>
                    <p class="follow-row__bio">{{ row.bio || '还没有简介' }}</p>
                  </div>
                  <button
                    v-if="row.id !== viewerId"
                    class="follow-row__action"
                    :disabled="pendingRow === row.id"
                    @click="toggleRowFollow(row)"
                  >
                    {{ rowFollowLabel(row) }}
                  </button>
                </li>
              </ul>

              <div v-else-if="!listLoading.followers" class="empty-state">
                <p class="empty-state__title">{{ profile.username }} 还没有粉丝。</p>
                <p class="empty-state__hint">关注 TA 的人会出现在这一栏。</p>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="点赞" name="liked">
            <div v-loading="listLoading.liked" class="author-profile__pane">
              <ul v-if="liked.length > 0" class="post-list">
                <li
                  v-for="post in liked"
                  :key="post.id"
                  class="post-row"
                  @click="goToArticle(post.id)"
                >
                  <div class="post-row__main">
                    <h3 class="post-row__title">{{ post.title || '无标题' }}</h3>
                    <p class="post-row__meta">
                      <span>{{ post.category?.name || '未分类' }}</span>
                      <span class="post-row__divider" aria-hidden="true"></span>
                      <PostCounts :views="post.views" :comments="post.comments" :likes="post.likes" />
                    </p>
                  </div>
                </li>
              </ul>

              <div v-else-if="!listLoading.liked" class="empty-state">
                <p class="empty-state__title">{{ profile.username }} 还没有点赞过文章。</p>
                <p class="empty-state__hint">TA 赞过的文章会出现这一栏。</p>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>

    <!-- 404 与「服务没答上话」分两报：后者不能把还在世的作者报成不存在 -->
    <div v-else-if="missing" class="empty-state">
      <p class="empty-state__title">这位作者不存在。</p>
      <p class="empty-state__hint">TA 可能已注销，或链接有误。</p>
      <RouterLink to="/" class="empty-state__action">返回首页</RouterLink>
    </div>
    <div v-else-if="failed" class="empty-state">
      <p class="empty-state__title">加载失败。</p>
      <p class="empty-state__hint">服务没有正常响应，这位作者可能还在。</p>
      <button class="empty-state__action" type="button" @click="loadAll">重试</button>
    </div>
    <div v-else class="author-profile__loading" v-loading="true" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getArticles } from '@/api/article'
import {
  getAuthorFollowers,
  getAuthorFollowing,
  getAuthorLikedPosts,
  getAuthorProfile,
  type AuthorProfile,
  type UserBrief,
} from '@/api/user'
import { followAuthor, unfollowAuthor } from '@/api/follow'
import type { Article, PostVO } from '@/api/types/article'
import { defaultAvatarUrl, formatMinioUrl } from '@/config'
import { setSeoMeta, resolveTitle } from '@/utils/seo'
import { useUserStore } from '@/stores/user'
import { debounce } from 'lodash-es'
import PostCounts from '@/components/PostCounts.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const authorId = computed(() => route.params.id as string)

const profile = ref<AuthorProfile | null>(null)
const loading = ref(false)
const missing = ref(false)
const failed = ref(false)
const followPending = ref(false)

// 作者本人不出现关注入口；服务端另有兜底（自关注返回 400）
const isSelf = computed(() => userStore.userInfo?.id === profile.value?.id)
const followed = computed(() => profile.value?.followed ?? false)

// 文章列表：只列该作者已发布，公开列表走 /articles?authorId=，草稿与定时发布不会出现在这里
const PAGE_SIZE = 10
const posts = ref<Article[]>([])
const page = ref(1)
const total = ref(0)
const exhausted = ref(false)
const loadingMore = ref(false)
const sentinel = ref<HTMLElement>()
/** 公开列表的谓词是标题 + 正文（≥2 字走全文索引，单字回退 LIKE），所以占位文案不写「标题」了事 */
const keyword = ref('')
/** 搜索词已改、结果还没换过来：这段时间既不算「没有更多」，也不许追加 */
const postsStale = ref(false)

const hasMore = computed(
  () => !postsStale.value && !exhausted.value && posts.value.length < total.value,
)

type TabKey = 'posts' | 'following' | 'followers' | 'liked'

const tab = ref<TabKey>('posts')
/** 除「TA 的文章」外三栏都 20 行封顶、不翻页，与个人页 ProfileFollowList 同一口径 */
const LIST_PAGE_SIZE = 20
const following = ref<UserBrief[]>([])
const fans = ref<UserBrief[]>([])
const liked = ref<PostVO[]>([])
const listLoading = ref({ following: false, followers: false, liked: false })
/** 首次点开才拉；换作者作废 */
let loadedTabs = new Set<TabKey>()

const fetchTab = async (key: Exclude<TabKey, 'posts'>) => {
  loadedTabs.add(key)
  listLoading.value[key] = true
  const params = { page: 1, pageSize: LIST_PAGE_SIZE }
  try {
    if (key === 'following')
      following.value = (await getAuthorFollowing(authorId.value, params)).data.list
    else if (key === 'followers')
      fans.value = (await getAuthorFollowers(authorId.value, params)).data.list
    else liked.value = (await getAuthorLikedPosts(authorId.value, params)).data.list
  } catch (error) {
    // 拉失败就作废，切回来还能重试
    loadedTabs.delete(key)
    console.error('获取作者主页列表失败:', error)
  } finally {
    listLoading.value[key] = false
  }
}

watch(tab, (next) => {
  if (next !== 'posts' && !loadedTabs.has(next)) fetchTab(next)
})

/** 每次拉完都重新观察一次哨兵：observe() 必定异步回调一次当前相交状态，
 *  而「新进入视口」在首屏不必滚动时根本不会发生——不复查就会卡在第一条之后。
 *  先等一次 DOM 刷新：首页那一屏之前哨兵还不存在，直接读 ref 会 observe 到 undefined，
 *  于是列表停在第一页，滚到底也不会追加 */
let observer: IntersectionObserver | undefined

const observeSentinel = async () => {
  if (!observer) return
  observer.disconnect()
  await nextTick()
  if (sentinel.value) observer.observe(sentinel.value)
}

const fetchPosts = async (append = false): Promise<boolean> => {
  const word = keyword.value
  if (append) loadingMore.value = true
  try {
    const { data } = await getArticles({
      page: page.value,
      pageSize: PAGE_SIZE,
      authorId: authorId.value,
      keyword: word || undefined,
    })
    // 请求期间搜索词已变：这一页属于旧条件，丢掉，否则会串进新列表
    if (word !== keyword.value) return false
    if (!append) exhausted.value = false
    posts.value = append ? [...posts.value, ...data.list] : data.list
    total.value = Number(data.total)
    if (data.list.length === 0) exhausted.value = true
    postsStale.value = false
    return true
  } catch (error) {
    console.error('获取作者文章列表失败:', error)
    return false
  } finally {
    loadingMore.value = false
    observeSentinel()
  }
}

const loadMore = async () => {
  if (loading.value || loadingMore.value || !hasMore.value) return
  const word = keyword.value
  page.value += 1
  const ok = await fetchPosts(true)
  // 仅在搜索词未变时回退页码；已变则页码被重置为 1，回退会退到 0
  if (!ok && word === keyword.value) page.value -= 1
}

const searchPosts = debounce(() => {
  page.value = 1
  fetchPosts()
}, 800)

/** flush: 'sync' 让换作者时的程序化清词能在同一次调用里被 cancel 撤掉，
 *  否则那一次防抖会在 800ms 后把已追加的页重新拍回第一页 */
watch(
  keyword,
  () => {
    postsStale.value = true
    searchPosts()
  },
  { flush: 'sync' },
)

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
  if (keyword.value) {
    // 清词同步排好一次防抖，返回不等 800ms，立刻执行掉
    keyword.value = ''
    searchPosts.flush()
  } else {
    // 没输过词时 watcher 不会响，这一支就得自己拉；顺手撤掉「删字删到空」那一次还在排的防抖
    searchPosts.cancel()
    page.value = 1
    fetchPosts()
  }
}

const onBarLeft = () => {
  // 退场途中又点开的话，别把头带塞回去
  if (!searchMode.value) headerHidden.value = false
}

// 关注状态与关注数以接口返回为唯一来源，不做乐观更新（business-model.md §4.7）
const toggleFollow = async () => {
  if (!profile.value || followPending.value) return
  if (!userStore.token) {
    ElMessage.info('登录后才能关注作者')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  followPending.value = true
  try {
    const res = profile.value.followed
      ? await unfollowAuthor(profile.value.id)
      : await followAuthor(profile.value.id)
    profile.value.followed = res.data.followed
    profile.value.followers = res.data.followers
  } catch {
    // 拦截器已弹提示；接口未确认前不改状态，因此无需回滚
  } finally {
    followPending.value = false
  }
}

const goToArticle = (id: string) => {
  router.push(`/articles/${id}`)
}

/** 行内关注：「粉丝」栏是站内唯一的就地回关入口，不必先点进对方主页 */
const viewerId = computed(() => userStore.userInfo?.id)
const pendingRow = ref<string | null>(null)

/** 单向时说动作（关注 / 取消关注），双向时说关系——同一个槽位不换控件 */
const rowFollowLabel = (row: UserBrief) => {
  if (row.followed && row.followsMe) return '互相关注'
  return row.followed ? '取消关注' : '关注'
}

/** 与个人页行内取关同一口径：可撤销，直接执行 + 一次性提示，不弹确认。
 *  只回填 followed 不重拉整页——对方有没有关注我由对方的操作决定，不随我这一下变化 */
const toggleRowFollow = async (row: UserBrief) => {
  if (pendingRow.value) return
  if (!userStore.token) {
    ElMessage.info('登录后才能关注')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  pendingRow.value = row.id
  try {
    const { data } = row.followed ? await unfollowAuthor(row.id) : await followAuthor(row.id)
    row.followed = data.followed
    ElMessage.success(data.followed ? '已关注' : '已取消关注')
  } catch {
    // 拦截器已弹提示；接口未确认前不改状态，因此无需回滚
  } finally {
    pendingRow.value = null
  }
}

const reset = () => {
  // 换作者：搜索态与词一起作废。清词会同步排一次防抖，紧跟着撤掉——第一页由 loadAll 自己拉
  searchMode.value = false
  headerHidden.value = false
  keyword.value = ''
  searchPosts.cancel()
  postsStale.value = false
  profile.value = null
  posts.value = []
  page.value = 1
  total.value = 0
  exhausted.value = false
  missing.value = false
  failed.value = false
  tab.value = 'posts'
  following.value = []
  fans.value = []
  liked.value = []
  listLoading.value = { following: false, followers: false, liked: false }
  loadedTabs = new Set()
}

const loadAll = async () => {
  reset()
  loading.value = true
  try {
    const { data } = await getAuthorProfile(authorId.value)
    profile.value = data
    // 作者主页没有 SEO 表位，标题仍走站点名后缀那一条拼法；简介为空时给一句可读的兜底描述
    setSeoMeta({
      title: resolveTitle(data.username),
      description: data.bio || `${data.username} 在 CloudLog 的文章`,
    })
  } catch (error) {
    const status = (error as { response?: { status?: number } }).response?.status
    missing.value = status === 404
    failed.value = status !== 404
    return
  } finally {
    loading.value = false
  }
  await nextTick()
  await fetchPosts()
}

onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) loadMore()
    },
    { rootMargin: '300px' },
  )
  loadAll()
})

onUnmounted(() => {
  observer?.disconnect()
})

// 换作者时组件实例会被路由复用，必须自己清干净重拉，否则上一位的主页留在屏上。
// 哨兵不用在这里管：fetchPosts 的 finally 每次都会 disconnect 后重新 observe
watch(
  () => route.params.id,
  () => {
    if (route.name !== 'AuthorProfile') return
    loadAll()
  },
)
</script>

<style scoped lang="scss">
.author-profile__card {
  padding: var(--space-lg);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
}

.author-profile__row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.author-profile__id {
  flex: 1;
  min-width: 0;
}

.author-profile__name {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 700;
  line-height: 1.3;
  color: var(--color-ink);
}

.author-profile__bio {
  margin: var(--space-2xs) 0 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-ink-2);
}

/* 与作者卡同一套已关注表现：文案说动作，状态只由洗染底与描边区分 */
.author-profile__follow.el-button.is-followed {
  color: var(--color-accent);
  border-color: var(--color-accent);
  background-color: var(--color-accent-wash);
}

.author-profile__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: var(--space-lg);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-rule);
  text-align: center;

  /* 统计是纯展示项，不给 hover 玩法（ui-design.md §5.2） */
  dt {
    font-size: var(--text-xs);
    color: var(--color-ink-3);
  }

  dd {
    margin-top: var(--space-3xs);
    font-size: var(--text-base);
    font-weight: 600;
    color: var(--color-ink);
  }
}

/* ---- 选项卡行：搜索把手的定位父级，配方与个人页 .profile__tabs-host 同一份 ---- */
.author-profile__tabs-host {
  position: relative;
  margin-top: var(--space-xl);
}

.author-profile__tabs {
  margin-top: 0;
}

/* 头带只是让位、不撤：display:none 会在退场结束前空出一帧，整张列表会上顶一下 */
.author-profile__tabs-host.is-searching :deep(.el-tabs__header) {
  visibility: hidden;
}

.author-profile__search-handle {
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

/* 把手钉在右端，所以搜索条从把手那个位置向左展开、退场原路收回；时长与个人页 .psearch 同一档 */
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
.author-profile__search-bar {
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

.author-profile__search-back {
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

.author-profile__search-divider {
  width: 1px;
  height: 1em;
  background-color: var(--color-rule-strong);
}

.author-profile__search-title {
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--color-ink);
  white-space: nowrap;
}

.author-profile__search-field {
  position: relative;
  display: flex;
  align-items: center;
  width: 20rem;
  min-width: 0;
  margin-inline-start: auto;
}

.author-profile__search-input {
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

.author-profile__search-icon {
  position: absolute;
  inset-inline-end: var(--space-sm);
  color: var(--color-ink-4);
  pointer-events: none;
}

/* 面板配方与个人页 .profile__pane 同一份 */
.author-profile__pane {
  padding: var(--space-md) var(--space-lg);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
}

/* 地板只服务 spinner 与空态，列表一出来就交还给内容高度 */
.author-profile__pane:not(:has(ul)) {
  min-height: 8rem;
}

.post-list,
.follow-list {
  list-style: none;
}

.post-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  padding-block: var(--space-md);
  cursor: pointer;

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

  > * {
    white-space: nowrap;
  }
}

.post-row__divider {
  width: 1px;
  height: 1em;
  background-color: currentColor;
}

/* 两栏的行：与个人页 ProfileFollowList 同一结构、同一行内控件语汇 */
.follow-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding-block: var(--space-md);

  & + .follow-row {
    border-top: 1px solid var(--color-rule);
  }

  &:first-child {
    padding-top: 0;
  }

  &:last-child {
    padding-bottom: 0;
  }
}

.follow-row__avatar {
  display: flex;
  align-self: flex-start;
}

.follow-row__id {
  flex: 1;
  min-width: 0;
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

/* 与个人页行内取关同一枚文本按钮：不铺底色、不加描边，状态只由这两个字承担 */
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

.load-sentinel {
  padding-top: var(--space-md);
  font-size: var(--text-sm);
  color: var(--color-ink-4);
  text-align: center;

  &:empty {
    padding-top: 0;
  }
}

.empty-state {
  padding: var(--space-2xl) 0;
  text-align: center;
}

.empty-state__title {
  font-family: var(--font-display);
  font-size: var(--text-md);
  color: var(--color-ink);
}

.empty-state__hint {
  margin-top: var(--space-xs);
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

.empty-state__action {
  display: inline-block;
  margin-top: var(--space-md);
  font-size: var(--text-sm);
  color: var(--color-accent);
  background: none;
  border: none;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
    text-underline-offset: 0.25em;
  }
}

/* 首屏拉取期的地板：给 spinner 留落点，列表一出来就交还给内容高度 */
.author-profile__loading {
  min-height: 12rem;
}
</style>
