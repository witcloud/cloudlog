<template>
  <div v-if="loading" class="loading-container">
    <el-skeleton :rows="10" animated />
  </div>
  <div v-else-if="article" class="article-detail">
    <!-- 阅读进度：2px 细线钉在视口顶缘（压过吸顶顶栏一层），滚动进度走 scaleX 不触发重排 -->
    <div
      class="article-progress"
      :style="{ transform: `scaleX(${readingProgress})` }"
      aria-hidden="true"
    ></div>
    <div class="article-detail__main">
      <div class="article-detail__post">
        <ArticleHeader
          :article="article"
          :can-edit="canEdit"
          :follow-pending="followPending"
          @edit="handleEdit"
          @toggle-follow="toggleFollow"
        />

        <!-- 窄屏右栏不渲染：长文导航收进正文卡，随用随开；≥60rem 交给右栏吸顶目录 -->
        <details v-if="tocItems.length" ref="mobileTocEl" class="article-toc-mobile">
          <summary>
            目录
            <span class="article-toc-mobile__count tnum">{{ tocItems.length }}</span>
          </summary>
          <nav class="article-toc-mobile__list" aria-label="文章目录">
            <button
              v-for="item in tocItems"
              :key="item.key"
              :class="['article-toc-mobile__item', { 'is-sub': item.level > tocMinLevel }]"
              type="button"
              @click="jumpFromMobileToc(item.index)"
            >
              {{ item.text }}
            </button>
          </nav>
        </details>

        <div
          ref="contentEl"
          class="article-detail__content"
          @click="onProseClick"
          @keydown="onProseKeydown"
        >
          <!-- no-img-zoom-in：库默认给正文图绑 medium-zoom，点击只能放大到原图尺寸、
               不响应 ESC、也没有滚轮缩放。看图走下面的 ImageLightbox，两层不能并存。
               show-code-row-number 关掉：库默认开着行号槽，用 !important 把左侧撑到 3.5em，
               但这里实测一个行号都没渲染出来（[rn-wrapper] 数量 0），只剩一条空栏位 -->
          <md-preview
            :model-value="article.content ?? ''"
            :theme="isDark ? 'dark' : 'light'"
            :no-img-zoom-in="true"
            :show-code-row-number="false"
            code-theme="one-dark"
            :sanitize="sanitizeHtml"
            :on-html-changed="onPreviewRendered"
          />
        </div>

        <!-- 正文页脚：标签在左、互动按钮在右，共用上方那条发丝线。
             互动按钮是同一组的第二副面孔——≥84rem 它钉在容器左侧成竖栏，不足时顺流落在这里 -->
        <div class="article-detail__foot">
        <nav v-if="article.tags?.length" class="article-detail__tags" aria-label="文章标签">
          <RouterLink
            v-for="tag in article.tags"
            :key="tag.id"
            class="article-detail__tag"
            :to="{ path: '/', query: { tagId: tag.id } }"
          >
            #{{ tag.name }}
          </RouterLink>
        </nav>
      <nav class="article-floatbar" aria-label="文章快捷操作">
        <button
          class="article-floatbar__btn article-floatbar__back"
          type="button"
          aria-label="返回"
          @click="handleBack"
        >
          <el-icon :size="18"><ArrowLeft /></el-icon>
        </button>

        <div
          class="article-floatbar__author"
          :class="{ 'is-hidden': !showAvatar }"
          :aria-hidden="!showAvatar"
        >
          <button
            class="article-floatbar__avatar"
            type="button"
            aria-label="回到文章开头查看作者"
            :tabindex="showAvatar ? 0 : -1"
            @click="scrollToTop"
          >
            <el-avatar :size="44" :src="formatMinioUrl(article.author.avatar) || defaultAvatarUrl" />
          </button>

          <!-- 作者卡的浓缩替身：卡片滚出视口才出现的那一格，把卡片唯一的动作一起搬过来。
               关注做成骑在头像下缘的药丸；竖栏只有 2.75rem 宽，放不下「取消关注」四字，
               所以这一枚的已关注态写状态词「已关注」（与卡片「取消关注」刻意不同，也避开 aria 名与可见文字打架） -->
          <button
            v-if="canFollow"
            class="article-floatbar__follow"
            :class="{ 'is-followed': followed }"
            type="button"
            :tabindex="showAvatar ? 0 : -1"
            :disabled="followPending"
            :aria-busy="followPending"
            @click="toggleFollow"
          >
            {{ followed ? '已关注' : '关注' }}
          </button>
        </div>

        <button
          class="article-floatbar__btn"
          :class="{ 'is-active': liked }"
          type="button"
          :aria-label="liked ? '取消点赞' : '点赞'"
          :disabled="likePending"
          :aria-busy="likePending"
          @click="toggleLike"
        >
          <svg
            class="article-floatbar__icon"
            viewBox="0 0 24 24"
            width="18"
            height="18"
            fill="currentColor"
            aria-hidden="true"
          >
            <path
              d="M1 21h4V9H1v12zM23 10c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.58 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.72v-2.01z"
            />
          </svg>
          <span class="article-floatbar__badge tnum">{{ likeCount }}</span>
        </button>
        <button
          class="article-floatbar__btn article-floatbar__comments"
          type="button"
          aria-label="查看评论"
          @click="scrollToComments"
        >
          <el-icon :size="18"><ChatDotRound /></el-icon>
          <span class="article-floatbar__badge tnum">{{ article.comments }}</span>
        </button>
        <el-popover
          ref="sharePopoverRef"
          :placement="railWide ? 'right' : 'top'"
          :popper-options="sharePopperOptions"
          :width="96"
          trigger="click"
        >
          <template #reference>
            <button class="article-floatbar__btn" type="button" aria-label="分享文章">
              <el-icon :size="18"><Share /></el-icon>
            </button>
          </template>
          <div class="article-floatbar__share">
            <button type="button" @click="shareTo('weibo')">
              <svg class="article-floatbar__share-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="weiboMark" />
              </svg>
              微博
            </button>
            <button type="button" @click="openShareQr">
              <svg class="article-floatbar__share-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="wechatMark" />
              </svg>
              微信
            </button>
            <button type="button" @click="shareTo('copy')">
              <el-icon :size="16"><Link /></el-icon>
              复制链接
            </button>
          </div>
        </el-popover>
      </nav>
      </div>
      </div>

      <div ref="commentSectionRef" class="article-detail__comments">
        <CommentSection
          :post-id="article.id"
          :comment-count="article.comments"
          @approved="onCommentApproved"
        />
      </div>
    </div>

    <aside class="article-detail__aside">
      <div class="article-detail__aside-inner">
        <div ref="authorCardRef">
          <AuthorCard
            :author="article.author"
            :stats="{
              articles: article.author.articleCount,
              views: article.author.viewCount,
            }"
            :follow-pending="followPending"
            @toggle-follow="toggleFollow"
          />
        </div>
        <TocSidebar :toc-items="tocItems" :active-index="activeIndex" @navigate="scrollToToc" />
      </div>
    </aside>

    <el-dialog v-model="shareQrVisible" title="微信扫码分享" width="240px">
      <svg
        class="article-share-qr"
        :viewBox="`0 0 ${shareQr.size} ${shareQr.size}`"
        role="img"
        aria-label="本文地址的二维码"
      >
        <rect class="article-share-qr__surface" :width="shareQr.size" :height="shareQr.size" />
        <path
          class="article-share-qr__ink"
          :d="shareQr.path"
          :transform="`translate(${shareQr.quiet} ${shareQr.quiet})`"
        />
      </svg>
    </el-dialog>

    <ImageLightbox v-model="lightboxVisible" :src="lightboxSrc" />
  </div>
  <div v-else-if="detailFailed" class="article-missing">
    <p class="article-missing__title">加载失败。</p>
    <p class="article-missing__hint">服务没有正常响应，这篇文章可能还在。</p>
    <button class="article-missing__action" type="button" @click="retryLoad">重试</button>
  </div>
  <div v-else class="article-missing">
    <p class="article-missing__title">文章不存在。</p>
    <p class="article-missing__hint">它可能已被删除，或链接有误。</p>
    <RouterLink to="/" class="article-missing__action">返回首页</RouterLink>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MdPreview } from 'md-editor-v3'
import { sanitizeHtml } from '@/utils/sanitize'
import 'md-editor-v3/lib/preview.css'
import qrcode from 'qrcode-generator'
import { setSeoMeta, canonicalUrl, toIso8601 } from '@/utils/seo'
import { useArticleStore } from '@/stores/article'
import { useSiteStore } from '@/stores/site'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'
import { useMarkdownToc } from '@/composables/useMarkdownToc'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { formatMinioUrl, defaultAvatarUrl } from '@/config'
import { weiboMark, wechatMark } from '@/assets/icons/share-marks'
import { likeArticle, unlikeArticle } from '@/api/article'
import { ArrowLeft, ChatDotRound, Link, Share } from '@element-plus/icons-vue'
import { followAuthor, unfollowAuthor } from '@/api/follow'

// Components
import ArticleHeader from './components/ArticleHeader.vue'
import AuthorCard from './components/AuthorCard.vue'
import CommentSection from './components/CommentSection.vue'
import TocSidebar from './components/TocSidebar.vue'
import ImageLightbox from '@/components/ImageLightbox.vue'

const route = useRoute()
const router = useRouter()
const articleStore = useArticleStore()
const userStore = useUserStore()
const { isDark } = useTheme()
const { currentArticle: article, loading, detailFailed } = storeToRefs(articleStore)

// 权限控制：管理员可编辑所有，普通用户只能编辑自己的文章
const isAdmin = computed(() => userStore.userInfo?.role === 'admin')
const canEdit = computed(() => {
  if (!userStore.userInfo || !article.value) return false
  if (isAdmin.value) return true
  return article.value.author.id === userStore.userInfo.id
})

// 正文插图：内联那一档收在 --figure-max-h，看完整画面走灯箱。
// src 直接取渲染后的 img，地址规整在渲染前已经做完，这里不再拼一次
const lightboxVisible = ref(false)
const lightboxSrc = ref('')

const openLightbox = (image: HTMLImageElement) => {
  lightboxSrc.value = image.currentSrc || image.src
  lightboxVisible.value = true
}

const onProseClick = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  if (target.tagName !== 'IMG') return
  openLightbox(target as HTMLImageElement)
}

/** 键盘走同一条路：插图原先只有鼠标能放大，Tab 根本停不下来 */
const onProseKeydown = (event: KeyboardEvent) => {
  const target = event.target as HTMLElement
  if (target.tagName !== 'IMG') return
  if (event.key !== 'Enter' && event.key !== ' ') return
  event.preventDefault()
  openLightbox(target as HTMLImageElement)
}

/**
 * 渲染后给正文每张图补两件事：可聚焦（配上面的 keydown）与一句最低限度的 alt。
 * 作者写 `![](url)` 时不会想到读屏，落成 alt="" 等于这张图对读屏不存在。
 * 不改 role：img 换成 button 会把「这是一张图」这层语义整个丢掉。
 */
const makeImagesOperable = (root: HTMLElement) => {
  for (const node of Array.from(root.querySelectorAll('img'))) {
    const image = node as HTMLImageElement
    if (!image.getAttribute('alt')) image.setAttribute('alt', image.title || '正文插图')
    if (image.dataset.lightboxReady) continue
    image.dataset.lightboxReady = '1'
    image.setAttribute('tabindex', '0')
  }
}

// ---- 目录：逻辑抽在 useMarkdownToc（编辑器大纲共用），DOM 优先、源码推导兜底 ----
/* 页面大标题已经是唯一的 h1，正文里 `#` 渲染出的 h1 与它同级是语义错误。
   作者该能随便用 `#`：所以渲染后整体下移一级，级别归位、字号由 global.css 的档位跟着移，观感不变。
   逐个元素打标记而不是按容器打：md-editor 换文章会重建子节点，标记随节点消失，
   而本函数会在每次渲染后的收集前各调一次，没标记就会二次下移 */
const demoteBodyHeadings = (root: HTMLElement) => {
  for (let level = 1; level <= 5; level++) {
    root.querySelectorAll(`h${level}:not([data-demoted])`).forEach((node) => {
      const el = node as HTMLElement
      const shifted = document.createElement(`h${level + 1}`)
      for (const attr of Array.from(el.attributes)) shifted.setAttribute(attr.name, attr.value)
      shifted.append(...el.childNodes)
      shifted.dataset.demoted = '1'
      el.replaceWith(shifted)
    })
  }
}

const contentEl = ref<HTMLElement>()

// levelOffset 1 对齐上面那条「整体下移一级」的渲染链；降级挂在 beforeCollect 上，
// 任何一次收集（含 composable 落一拍的兜底）拿到的都是归位后的级别
const { tocItems, activeIndex, containerRef, refresh, scrollTo: scrollToToc, startFollow } =
  useMarkdownToc({
    source: computed(() => article.value?.content ?? ''),
    levelOffset: 1,
    beforeCollect: (root) => {
      demoteBodyHeadings(root)
      // 插图的可聚焦与 alt 挂在同一处：这是本组件唯一保证「渲染之后」跑到的时机
      makeImagesOperable(root)
    },
  })

// ---- 窄屏内嵌目录：与右栏吸顶目录共用同一份 tocItems，点击跳转后收起 ----
const mobileTocEl = ref<HTMLDetailsElement>()
const tocMinLevel = computed(() => Math.min(...tocItems.value.map((item) => item.level), 6))

const jumpFromMobileToc = (index: number) => {
  mobileTocEl.value?.removeAttribute('open')
  scrollToToc(index)
}

const bindTocContainer = () => {
  containerRef.value = contentEl.value ?? null
  refresh()
}

// md-editor-v3 渲染完成（modelValue 之后的宏任务）后再绑定收一次，兜住首次渲染晚于首次收集；
// 重复绑定幂等
const onPreviewRendered = () => {
  nextTick(bindTocContainer)
}

/**
 * 详情接口的 seoTitle / seoDescription / seoKeywords 已是服务端合成好的最终值（business-model.md §2.10），
 * 前端不判空、不拼站点名。这一轮把封面、发布时刻与标签也交下去：og:image 没有图就没有社交卡片，
 * Google 的文章富结果要 datePublished 与 author
 */
const applySeoMeta = () => {
  const current = article.value
  if (!current) return
  // 与列表卡片同一件换算：库里存的就是上传时拼好的绝对地址，这里不改写主机（og:image 用封面原值）
  const cover = formatMinioUrl(current.cover)
  setSeoMeta({
    title: current.seoTitle,
    description: current.seoDescription,
    keywords: current.seoKeywords,
    image: cover || null,
    publishedTime: current.publishedAt || null,
    tags: current.tags.map((tag) => tag.name),
    structuredData: {
      '@context': 'https://schema.org',
      '@type': 'Article',
      headline: current.title,
      author: { '@type': 'Person', name: useSiteStore().siteName },
      mainEntityOfPage: { '@type': 'WebPage', '@id': canonicalUrl() },
      ...(cover ? { image: cover } : {}),
      ...(current.publishedAt ? { datePublished: toIso8601(current.publishedAt) } : {}),
      ...(current.seoDescription ? { description: current.seoDescription } : {}),
    },
  })
}

const loadArticle = async (id?: string) => {
  if (!id) return
  await articleStore.fetchArticleDetail(id)
  applySeoMeta()
}

// ---- 阅读进度：已滚过 / 可滚总高。监听常驻，切文回顶由 loading 后的一次重算校正 ----
const readingProgress = ref(0)

const updateReadingProgress = () => {
  const doc = document.documentElement
  const total = doc.scrollHeight - doc.clientHeight
  readingProgress.value = total > 0 ? Math.min(1, Math.max(0, doc.scrollTop / total)) : 0
}

onMounted(async () => {
  // 滚动跟随的监听挂载/卸载由 composable 托管
  startFollow()
  railQuery.addEventListener('change', onRailChange)
  window.addEventListener('scroll', updateReadingProgress, { passive: true })
  await loadArticle(route.params.id as string)
})

// 站内换一篇（前进/后退改 route.params.id）时同样要重写 meta，否则标题与描述还留着上一篇的
watch(
  () => route.params.id,
  (newId) => void loadArticle(newId as string),
)

const retryLoad = () => {
  const id = route.params.id as string
  if (id) void articleStore.fetchArticleDetail(id)
}

const handleEdit = () => {
  router.push(`/editor/${article.value?.id}`)
}

const handleBack = () => {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push('/')
  }
}

// ---- 左侧悬浮操作栏：常驻；作者头像仅在 author-card 完全离开可视区域时显示 ----
/** ≥84rem 容器外才有留白放竖栏；不足时同一组按钮换成正文末尾的一行，分享气泡的开口方向跟着换 */
const railQuery = window.matchMedia('(min-width: 84rem)')
const railWide = ref(railQuery.matches)
const onRailChange = (event: MediaQueryListEvent) => {
  railWide.value = event.matches
}

/** ≥84rem 竖栏是 position: fixed，浮层得同它一起锚视口才不会「漂一下再复位」；
   窄屏时这组按钮落回正文流里，absolute 才与触发点同步，fixed 反倒会脱开 */
const sharePopperOptions = computed(() => ({
  strategy: railWide.value ? ('fixed' as const) : ('absolute' as const),
}))

const authorCardRef = ref<HTMLElement>()
const commentSectionRef = ref<HTMLElement>()
const authorCardVisible = ref(true)
let authorCardObserver: IntersectionObserver | null = null

const showAvatar = computed(() => !authorCardVisible.value)

const observeAuthorCard = async () => {
  await nextTick()
  authorCardObserver?.disconnect()
  if (!authorCardRef.value) return
  // 视口顶部扣除固定顶栏高度：被顶栏完全遮挡即视为"完全离开可视区域"（threshold 0）
  const headerOffset = document.querySelector('header')?.offsetHeight ?? 0
  authorCardObserver = new IntersectionObserver(
    (entries) => {
      authorCardVisible.value = entries[0]?.isIntersecting ?? true
    },
    { rootMargin: `-${headerOffset}px 0px 0px 0px` },
  )
  authorCardObserver.observe(authorCardRef.value)
}

watch(
  () => [loading.value, article.value?.id],
  ([isLoading]) => {
    if (isLoading) {
      // 切文章时断开容器并重收一次：丢掉上一篇的标题元素引用，滚动高亮才不会拿旧节点算
      containerRef.value = null
      refresh()
      return
    }
    observeAuthorCard()
    // 新文渲染后重算一次阅读进度（换文章时高度变了，进度条不回算会停在旧刻度）
    nextTick(updateReadingProgress)
    // md-editor 渲染完成后绑定正文容器收集真实标题锚点（on-html-changed 会再各收一次）
    nextTick(bindTocContainer)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  railQuery.removeEventListener('change', onRailChange)
  window.removeEventListener('scroll', updateReadingProgress)
  authorCardObserver?.disconnect()
  authorCardObserver = null
})

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 点赞状态与计数以接口返回为唯一来源，不做乐观更新（ui-design.md §3.2 / business-model.md §4.5）
const liked = computed(() => article.value?.liked ?? false)
const likeCount = computed(() => article.value?.likes ?? 0)
const likePending = ref(false)

const toggleLike = async () => {
  if (!article.value || likePending.value) return
  if (!userStore.token) {
    ElMessage.info('登录后才能点赞')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  likePending.value = true
  try {
    const res = liked.value
      ? await unlikeArticle(article.value.id)
      : await likeArticle(article.value.id)
    article.value.liked = res.data.liked
    article.value.likes = res.data.likeCount
  } catch {
    // 拦截器已弹提示；接口未确认前不改状态，因此无需回滚
  } finally {
    likePending.value = false
  }
}

// 关注状态与关注数以接口返回为唯一来源，不做乐观更新（business-model.md §4.7）
const followPending = ref(false)

// 与作者卡、文章头同一判据：作者本人不出现关注入口，服务端另有 400 兜底
const followed = computed(() => article.value?.author.followed ?? false)
const canFollow = computed(
  () => !!article.value && userStore.userInfo?.id !== article.value.author.id,
)

const toggleFollow = async () => {
  if (!article.value || followPending.value) return
  if (!userStore.token) {
    ElMessage.info('登录后才能关注作者')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  followPending.value = true
  try {
    const authorId = article.value.author.id
    const res = article.value.author.followed
      ? await unfollowAuthor(authorId)
      : await followAuthor(authorId)
    article.value.author.followed = res.data.followed
    article.value.author.followers = res.data.followers
  } catch {
    // 拦截器已弹提示；接口未确认前不改状态，因此无需回滚
  } finally {
    followPending.value = false
  }
}

const scrollToComments = () => {
  commentSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const articleLink = computed(() => window.location.href)

/** 评论区确认一条已过审评论后同步计数：详情接口的 comments 是库里的 comment_count，
    发评成功后本地 +1，免得「评论列表多了一条、角标数字不动」直到重进页面 */
const onCommentApproved = () => {
  if (article.value) {
    article.value.comments = (article.value.comments ?? 0) + 1
  }
}

const sharePopoverRef = ref<{ hide: () => void } | null>(null)
const shareQrVisible = ref(false)

/** 静区 4 格是 QR 规范对扫码器的要求，不是版面留白 */
const QR_QUIET_ZONE = 4

const shareQr = computed(() => {
  const qr = qrcode(0, 'M')
  qr.addData(articleLink.value)
  qr.make()
  const modules = qr.getModuleCount()
  let path = ''
  for (let row = 0; row < modules; row++) {
    for (let col = 0; col < modules; col++) {
      if (qr.isDark(row, col)) path += `M${col} ${row}h1v1h-1z`
    }
  }
  return { path, size: modules + QR_QUIET_ZONE * 2, quiet: QR_QUIET_ZONE }
})

const openShareQr = () => {
  sharePopoverRef.value?.hide()
  shareQrVisible.value = true
}

const shareTo = (platform: 'weibo' | 'copy') => {
  if (!article.value) return
  sharePopoverRef.value?.hide()
  const url = encodeURIComponent(articleLink.value)
  const title = encodeURIComponent(article.value.title)

  switch (platform) {
    case 'weibo':
      window.open(`http://service.weibo.com/share/share.php?url=${url}&title=${title}`, '_blank')
      break
    case 'copy':
      // 复制链接不走外部服务；剪贴板不可用时给一次性提示
      navigator.clipboard
        ?.writeText(articleLink.value)
        .then(() => ElMessage.success('链接已复制'))
        .catch(() => ElMessage.error('复制失败，请手动复制地址栏链接'))
      break
  }
}
</script>

<style scoped lang="scss">
.article-detail {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-lg);
}

.loading-container {
  padding: var(--space-xl);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
}

/* 正文与评论各自独立成面（卡片范式 docs/ui-design.md §6.2）：
   paper 与 paper-2 只差 1.5% L，分层靠发丝线描边 + 一枚极轻阴影 */
/* 正文卡收在 --measure-prose、与右栏整组居中：文字列 688px ≈ 43 字/行，与编辑器写作面同宽，
   两端换行位置一致，「所见即所得」成立。容器余量归还两侧——≥84rem 那一侧由悬浮栏占用，
   不再让正文撑满主栏摊到 53 字/行；评论卡与正文卡同轨同内衬，文字左缘上下对齐 */
.article-detail__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
}

.article-detail__comments {
  padding: var(--space-lg);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-whisper);
}

/* 与下方评论卡同轨同内衬（--space-lg）：两张面文字左缘必须上下对齐；
   内衬从 16 升到 24，文字不再贴着卡缘（掘金/知乎同一档） */
.article-detail__post {
  padding: var(--space-lg);
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-whisper);
}

.article-detail__content {
  /* 标题落点：让开 3.5rem 的 sticky 顶栏再留一段呼吸。
     目录滚动联动的高亮判定线读的就是这个值，二者不会各算各的 */
  :deep(:is(h1, h2, h3, h4, h5, h6)) {
    scroll-margin-top: calc(var(--header-h) + var(--space-lg));
  }
}

/* 阅读进度条：2px 与发丝线同一量级，色用唯一强调色 */
.article-progress {
  position: fixed;
  inset-block-start: 0;
  inset-inline: 0;
  height: 2px;
  z-index: calc(var(--z-sticky) + 1);
  background-color: var(--color-accent);
  transform-origin: 0 50%;
  pointer-events: none;
}

/* ---- 窄屏内嵌目录：右栏 60rem 才渲染，手机上的长文导航由它承担 ---- */
.article-toc-mobile {
  display: none;
  margin-bottom: var(--space-md);
  padding: var(--space-xs) var(--space-md);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  background-color: var(--color-paper-3);
}

@media (max-width: 59.9375rem) {
  .article-toc-mobile {
    display: block;
  }
}

.article-toc-mobile summary {
  display: flex;
  align-items: center;
  gap: var(--space-2xs);
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-ink);
  cursor: pointer;
  list-style: none;
}

.article-toc-mobile summary::-webkit-details-marker {
  display: none;
}

/* chevron 用两条 1.5px 边拼成，开合旋转，与全站图标同一语言 */
.article-toc-mobile summary::after {
  content: '';
  width: 0.4rem;
  height: 0.4rem;
  margin-inline-start: auto;
  border-inline-end: 1.5px solid var(--color-ink-3);
  border-block-end: 1.5px solid var(--color-ink-3);
  transform: rotate(45deg) translateY(-0.1rem);
  transition: transform var(--dur-micro) var(--ease-out);
}

.article-toc-mobile[open] summary::after {
  transform: rotate(-135deg) translateY(-0.1rem);
}

.article-toc-mobile__list {
  display: flex;
  flex-direction: column;
  margin-top: var(--space-xs);
  padding-top: var(--space-2xs);
  border-top: 1px solid var(--color-rule);
}

.article-toc-mobile__item {
  padding: var(--space-2xs) 0;
  border: none;
  background: none;
  font-family: inherit;
  text-align: start;
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  cursor: pointer;
}

.article-toc-mobile__item.is-sub {
  padding-inline-start: var(--space-md);
  color: var(--color-ink-3);
}

.article-toc-mobile__item:hover {
  color: var(--color-accent);
}

.article-toc-mobile__count {
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-ink-3);
}

/* 正文页脚：上方一条发丝线与正文分隔，线上留白与文章头那条线同一套（两侧各 --space-md）。
   标签在左、互动按钮在右；无标签时按钮独占该行，发丝线仍在 */
.article-detail__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-rule);
}

/* 标签 chip：凹陷面小药丸（主流平台的 tag 形态），hover 洗染 + 强调色 */
.article-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2xs);
}

.article-detail__tag {
  padding: var(--space-3xs) var(--space-xs);
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  background-color: var(--color-paper-3);
  border-radius: var(--radius-sm);
  transition:
    color var(--dur-micro) var(--ease-out),
    background-color var(--dur-micro) var(--ease-out);
}

.article-detail__tag:hover {
  color: var(--color-accent);
  background-color: var(--color-accent-wash);
}

.article-detail__aside {
  display: none;
}

@media (min-width: 60rem) {
  .article-detail__aside {
    display: flex;
    flex-direction: column;
  }
}

.article-detail__comments {
  scroll-margin-top: calc(var(--header-h) + var(--space-md));
}

/* ---- 左侧悬浮操作栏：≥84rem 钉在容器外留白里成竖栏；不足时顺流在正文之后成一行 ---- */
.article-floatbar {
  display: none;
}

/* 窄屏换的那副面孔：竖栏专属的「返回」与「回顶看作者」收掉，只留互动三件 */
@media (max-width: 83.9375rem) {
  .article-floatbar {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
  }

  /* 双类名：.article-floatbar__btn 的 display 声明在本块之后，单类名压不过它。
     「查看评论」在窄屏收掉——评论区就紧接在正文面板下面，跳转没有意义，数量在「评论 N」标题上已经写了 */
  .article-floatbar .article-floatbar__back,
  .article-floatbar .article-floatbar__author,
  .article-floatbar .article-floatbar__comments {
    display: none;
  }
}

@media (min-width: 84rem) {
  .article-floatbar {
    /* 右缘距容器左缘 2rem：容器外留白 = (100vw - 72rem) / 2，84rem 视口起恰好容纳 */
    --rail-left: calc((100vw - var(--container-max)) / 2 - var(--space-2xl) - var(--space-md));

    position: fixed;
    left: var(--rail-left);
    /* 下限 = 返回键下缘 + 一档间距：返回键钉在固定值，20% 在矮视口会升进它那条带里 */
    top: max(20%, calc(var(--header-h) + var(--space-xl) + 2.5rem + var(--space-sm)));
    z-index: var(--z-sticky);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--space-sm);
  }

  /* 返回是导航而非互动，脱离互动组单独钉在左栏顶端，上缘与正文首行齐平。
     双类名选择器：压过 .article-floatbar__btn 的 position: relative */
  .article-floatbar .article-floatbar__back {
    position: fixed;
    left: var(--rail-left);
    top: calc(var(--header-h) + var(--space-xl));
  }
}

/* 作者格：头像 + 骑在下缘的关注药丸，常驻占位，显隐时平滑收起/展开，避免栏内按钮跳动 */
.article-floatbar__author {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  /* 下缘让出 --space-xs 给探出头像的药丸（绝对定位不计入本盒高度，不让出来就被 overflow 裁掉）。
     这一档同时决定咬进头像多少：药丸 1.25rem 高，让 8px 就压住 12px，中心落在圆下缘上方 2px，
     读起来是"骑在边上"而不是"挂在下面"。
     左右各让出 --space-xs 给「已关注」加切出环的宽度，再用等值负 margin 抵回去，
     竖栏仍只占头像的 2.75rem，--rail-left 那套留白计算不动 */
  padding: 0 var(--space-xs) var(--space-xs);
  margin-inline: calc(-1 * var(--space-xs));
  max-height: 4rem;
  margin-bottom: var(--space-xs);
  overflow: hidden;
  transition:
    max-height var(--dur-short) var(--ease-out),
    margin-bottom var(--dur-short) var(--ease-out),
    padding-bottom var(--dur-short) var(--ease-out),
    opacity var(--dur-short) var(--ease-out),
    transform var(--dur-short) var(--ease-out);
}

.article-floatbar__author.is-hidden {
  max-height: 0;
  padding-bottom: 0;
  margin-bottom: 0;
  opacity: 0;
  transform: scale(0.8);
  transform-origin: top center;
  pointer-events: none;
}

.article-floatbar__avatar {
  /* flex 掉行盒：inline-flex 的 el-avatar 会让按钮比头像圆高出 7px，药丸的落点就跟着偏 */
  display: flex;
  justify-content: center;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  border-radius: 50%;

  .el-avatar {
    box-shadow: var(--shadow-whisper);
  }
}

/* 关注药丸：骑在头像圆的下缘，靠一圈页面底色把自己从头像上切下来 */
.article-floatbar__follow {
  position: absolute;
  bottom: 0;
  left: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translateX(-50%);
  height: 1.25rem;
  padding: 0 var(--space-2xs);
  border: 1px solid transparent;
  border-radius: 999px;
  background-color: var(--color-accent);
  /* 切出环只描 1px：头像才 2.75rem，2px 的纸色槽会在药丸与头像之间切出一道缝，读起来就散开了 */
  box-shadow: 0 0 0 1px var(--color-paper);
  color: var(--color-paper);
  font-family: var(--font-body);
  font-size: var(--text-xs);
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color var(--dur-micro) var(--ease-out),
    border-color var(--dur-micro) var(--ease-out),
    color var(--dur-micro) var(--ease-out);
}

.article-floatbar__follow:hover {
  background-color: var(--color-accent-ink);
}

/* 已关注转描边 + 抬升面，与卡片那套「洗染底 + 强调色描边」同一取向；
   但洗染底是 8% 半透明，压在头像照片上会透出来，所以这里换成不透明的抬升面 */
.article-floatbar__follow.is-followed {
  background-color: var(--color-paper-2);
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.article-floatbar__follow.is-followed:hover {
  background-color: var(--color-paper-3);
}

/* 双类名 + 写在最后：提交中的失效态要盖过上面两态（ui-design.md §4.1 / §5.3） */
.article-floatbar .article-floatbar__follow:disabled {
  background-color: var(--color-paper-3);
  border-color: var(--color-rule);
  color: var(--color-ink-4);
  cursor: progress;
}

/* 圆形操作按钮 */
.article-floatbar__btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  padding: 0;
  border: 1px solid var(--color-rule);
  border-radius: 50%;
  background-color: var(--color-paper-2);
  box-shadow: var(--shadow-whisper);
  cursor: pointer;
  color: var(--color-ink-3);
  transition:
    color var(--dur-micro) var(--ease-out),
    border-color var(--dur-micro) var(--ease-out);
}

.article-floatbar__btn:hover,
.article-floatbar__btn.is-active {
  color: var(--color-accent);
  border-color: var(--color-accent);
}

/* 提交中态：禁用重复点击，用失效态色表达「等接口返回」（ui-design.md §4.1 / §5.3） */
.article-floatbar__btn:disabled {
  cursor: progress;
  color: var(--color-ink-4);
  border-color: var(--color-rule);
}

/* 计数角标（点赞数/评论数） */
.article-floatbar__badge {
  position: absolute;
  top: calc(-1 * var(--space-3xs));
  left: 100%;
  transform: translate(-30%, 0);
  min-width: 1.125rem;
  padding: 0 var(--space-3xs);
  background-color: var(--color-paper-3);
  border: 1px solid var(--color-rule);
  border-radius: 999px;
  font-size: var(--text-xs);
  line-height: 1.25;
  text-align: center;
  color: var(--color-ink-3);
}

.article-missing {
  padding: var(--space-2xl) 0;
  text-align: center;
}

.article-missing__title {
  font-family: var(--font-display);
  font-size: var(--text-md);
  color: var(--color-ink);
}

.article-missing__hint {
  margin-top: var(--space-xs);
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

.article-missing__action {
  display: inline-block;
  margin-top: var(--space-lg);
  font-size: var(--text-sm);
  color: var(--color-accent);
}

.article-missing__action:hover {
  text-decoration: underline;
  text-underline-offset: 0.25em;
}

button.article-missing__action {
  padding: 0;
  border: 0;
  font-family: inherit;
  background: none;
  cursor: pointer;
}

/* 右栏拉伸至整行高度，作者卡随页面滚走，仅目录内部吸顶 */
.article-detail__aside-inner {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

@media (min-width: 60rem) {
  .article-detail {
    /* 正文轨道封顶 --measure-prose、整组居中：文字列与编辑器写作面同宽（换行一致），
       窄容器（60–65rem 视口）先让正文轨道收缩，不会溢出 */
    grid-template-columns: minmax(0, var(--measure-prose)) 15rem;
    justify-content: center;
  }
}
</style>

<!-- 分享气泡与微信弹窗内容被 teleport 到 body，需非 scoped 样式 -->
<style lang="scss">
.article-floatbar__share {
  display: flex;
  flex-direction: column;
}

.article-floatbar__share button {
  display: flex;
  align-items: center;
  gap: var(--space-2xs);
  padding: var(--space-2xs) var(--space-xs);
  border: none;
  background: none;
  cursor: pointer;
  text-align: left;
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  transition: color var(--dur-micro) var(--ease-out);
}

.article-floatbar__share button:hover {
  color: var(--color-accent);
}

/* 1rem = §4.6 的 16px 图标基准；fill 继承按钮文字色，hover 一起变朱砂 */
.article-floatbar__share-icon {
  width: 1rem;
  height: 1rem;
  flex: none;
  fill: currentColor;
}

/* 深模块 + 浅底，两态恒不反色：反色二维码扫码器读不出 */
.article-share-qr {
  display: block;
  width: 100%;
  height: auto;
}

.article-share-qr__surface {
  fill: var(--color-qr-surface);
}

.article-share-qr__ink {
  fill: var(--color-qr-ink);
}
</style>
