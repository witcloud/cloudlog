<template>
  <div class="layout" :class="{ 'layout--bare': isBare }">
    <header v-if="!isBare" class="site-header" :class="{ 'site-header--hidden': headerHidden }">
      <div class="site-header__inner">
        <div class="site-header__left">
          <button class="icon-btn burger" aria-label="打开导航" @click="navOpen = true">
            <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
              <path
                d="M2.5 4h11M2.5 8h11M2.5 12h11"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
              />
            </svg>
          </button>

          <RouterLink to="/" class="wordmark"><SiteLogo /></RouterLink>

          <nav class="site-nav" aria-label="主导航">
            <RouterLink
              to="/"
              class="site-nav__link"
              :class="{ 'is-active': route.name === 'Home' }"
              >首页</RouterLink
            >
            <RouterLink
              to="/about"
              class="site-nav__link"
              :class="{ 'is-active': route.name === 'About' }"
              >关于</RouterLink
            >
          </nav>
        </div>

        <div class="site-header__right">
          <form class="search-box" role="search" @submit.prevent="goSearch">
            <svg
              class="search-box__icon"
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
            <input
              ref="searchInputRef"
              v-model="searchQuery"
              type="search"
              class="search-box__input"
              placeholder="搜索文章…"
              aria-label="搜索文章"
            />
            <button
              v-if="searchQuery"
              type="button"
              class="search-box__clear"
              aria-label="清空搜索"
              @click="clearSearch"
            >
              <svg viewBox="0 0 16 16" width="12" height="12" fill="none" aria-hidden="true">
                <path
                  d="m4 4 8 8M12 4l-8 8"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                />
              </svg>
            </button>
          </form>

          <button
            class="icon-btn"
            :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
            @click="toggle"
          >
            <svg
              v-if="isDark"
              viewBox="0 0 16 16"
              width="16"
              height="16"
              fill="none"
              aria-hidden="true"
            >
              <circle cx="8" cy="8" r="3.25" stroke="currentColor" stroke-width="1.5" />
              <path
                d="M8 1.5v1.6M8 12.9v1.6M1.5 8h1.6M12.9 8h1.6M3.4 3.4l1.1 1.1M11.5 11.5l1.1 1.1M12.6 3.4l-1.1 1.1M4.5 11.5l-1.1 1.1"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
              />
            </svg>
            <svg v-else viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
              <path
                d="M13.5 9.5A6 6 0 0 1 6.5 2.5a6 6 0 1 0 7 7Z"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linejoin="round"
              />
            </svg>
          </button>

          <!-- 账号簇：≤600px 让位给顶栏搜索框，功能由抽屉承载（§2.1） -->
          <div class="header-account">
            <template v-if="isLoggedIn">
              <!-- 创作域仅管理员：后端 POST/PUT /articles 要求 ADMIN，给普通用户入口只会处处 403 -->
              <el-dropdown
                v-if="isAdmin"
                trigger="click"
                :popper-options="{ strategy: 'fixed' }"
                @visible-change="headerMenuOpen = $event"
                @command="handleWriteCommand"
              >
                <button class="btn-outline write-btn">
                  创作
                  <svg viewBox="0 0 16 16" width="12" height="12" fill="none" aria-hidden="true">
                    <path
                      d="m4 6 4 4 4-4"
                      stroke="currentColor"
                      stroke-width="1.5"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="editor">写文章</el-dropdown-item>
                    <el-dropdown-item command="drafts">草稿箱</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>

              <el-dropdown
                trigger="click"
                :popper-options="{ strategy: 'fixed' }"
                @visible-change="headerMenuOpen = $event"
              >
                <el-avatar :size="30" :src="userAvatar" class="avatar-btn" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="router.push('/profile')">个人主页</el-dropdown-item>
                    <el-dropdown-item @click="router.push('/settings')">设置</el-dropdown-item>
                    <el-dropdown-item v-if="isAdmin" @click="router.push('/admin')"
                      >管理后台</el-dropdown-item
                    >
                    <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <template v-else>
              <RouterLink to="/login" class="auth-link">登录</RouterLink>
              <RouterLink to="/register" class="btn-outline">注册</RouterLink>
            </template>
          </div>
        </div>
      </div>
    </header>

    <main class="layout-main">
      <!-- 单一 router-view 保证 keep-alive 缓存跨路由保留；bare 路由（编辑器）仅去掉容器外框 -->
      <div :class="isBare ? undefined : 'container'">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in" @after-leave="onAfterLeave">
            <keep-alive include="HomeView">
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </div>
    </main>

    <BackToTop v-if="!isBare" />

    <!-- 移动端导航抽屉（≤960px） -->
    <el-drawer
      v-if="!isBare"
      v-model="navOpen"
      direction="ltr"
      size="80%"
      :with-header="false"
      class="nav-drawer"
    >
      <nav class="drawer-nav" aria-label="移动端导航">
        <p class="drawer-nav__label">导航</p>
        <RouterLink to="/" class="drawer-nav__link" @click="navOpen = false">首页</RouterLink>
        <RouterLink to="/about" class="drawer-nav__link" @click="navOpen = false">关于</RouterLink>
        <RouterLink to="/search" class="drawer-nav__link" @click="navOpen = false">搜索</RouterLink>

        <template v-if="isAdmin">
          <p class="drawer-nav__label">写作</p>
          <RouterLink to="/editor" class="drawer-nav__link" @click="navOpen = false"
            >发布文章</RouterLink
          >
          <RouterLink to="/drafts" class="drawer-nav__link" @click="navOpen = false"
            >草稿箱</RouterLink
          >
        </template>
        <template v-if="isLoggedIn">
          <p class="drawer-nav__label">账号</p>
          <RouterLink to="/profile" class="drawer-nav__link" @click="navOpen = false"
            >个人主页</RouterLink
          >
          <RouterLink to="/settings" class="drawer-nav__link" @click="navOpen = false"
            >设置</RouterLink
          >
          <RouterLink v-if="isAdmin" to="/admin" class="drawer-nav__link" @click="navOpen = false"
            >管理后台</RouterLink
          >
          <button class="drawer-nav__link" @click="handleLogout">退出登录</button>
        </template>
        <template v-else>
          <p class="drawer-nav__label">账号</p>
          <RouterLink to="/login" class="drawer-nav__link" @click="navOpen = false"
            >登录</RouterLink
          >
          <RouterLink to="/register" class="drawer-nav__link" @click="navOpen = false"
            >注册</RouterLink
          >
        </template>
      </nav>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { formatMinioUrl, defaultAvatarUrl } from '@/config'
import { useTheme } from '@/composables/useTheme'
import { useHideOnScroll } from '@/composables/useHideOnScroll'
import { scrollControl } from '@/router'
import BackToTop from '@/components/BackToTop.vue'
import SiteLogo from '@/components/SiteLogo.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isDark, toggle } = useTheme()

const searchQuery = ref('')
const searchInputRef = ref<HTMLInputElement>()
const navOpen = ref(false)
const isLoggedIn = computed(() => !!userStore.token)
/** bare 路由（编辑器）：全屏工作台，不渲染站点顶栏/容器外框 */
const isBare = computed(() => route.meta.bare === true)
const isAdmin = computed(() => userStore.userInfo?.role === 'admin')
const userAvatar = computed(() => formatMinioUrl(userStore.userInfo?.avatar) || defaultAvatarUrl)

/** 抽屉展开、或顶栏下拉菜单展开时不收起：菜单 teleport 到 body，顶栏一走它就飘在半空 */
const headerMenuOpen = ref(false)
const { hidden: headerHidden, reset: resetHeader } = useHideOnScroll(
  () => navOpen.value || headerMenuOpen.value,
)

/** 锁只在滚动事件里判定，抽屉打开时补一次复位：不依赖 el-drawer 的锁滚动 */
watch(navOpen, (open) => {
  if (open) resetHeader()
})

const goSearch = () => {
  const q = searchQuery.value.trim()
  if (q) router.push({ path: '/search', query: { q } })
}

/** 搜索页上清空是连结果一起清（URL 去掉 q）；其他页面只清输入并把光标留在框里 */
const clearSearch = () => {
  searchQuery.value = ''
  if (route.name === 'Search' && route.query.q) router.push({ path: '/search' })
  else searchInputRef.value?.focus()
}

/** 宽屏的关键词框在顶栏：回填 URL 上的 q，改词重搜在同一处完成 */
watch(
  () => route.query.q,
  async (q) => {
    if (route.name !== 'Search') return
    const value = typeof q === 'string' ? q : ''
    searchQuery.value = value
    // 空词进搜索页时把光标放进框里；窄屏该框不渲染，focus 自然落空给页内搜索框
    if (!value) {
      await nextTick()
      searchInputRef.value?.focus()
    }
  },
  { immediate: true },
)

/** 旧页面移除（过渡 after-leave）后恢复滚动：前进回顶、后退回到离开时位置；顶栏同步复位 */
const onAfterLeave = () => {
  const y = scrollControl.pendingY
  scrollControl.pendingY = null
  window.scrollTo(0, y ?? 0)
  resetHeader()
}

/** 从管理后台（无过渡）回到前台时，布局重新挂载，在挂载后恢复滚动位置 */
onMounted(() => {
  const y = scrollControl.pendingY
  scrollControl.pendingY = null
  window.scrollTo(0, y ?? 0)
  resetHeader()
})

const handleWriteCommand = (command: string) => {
  if (command === 'editor') {
    router.push('/editor')
  } else if (command === 'drafts') {
    router.push('/drafts')
  }
}

const handleLogout = () => {
  navOpen.value = false
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}
</script>

<style scoped lang="scss">
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
}

/* ---- 顶栏：N9 边缘对齐 ---- */
.site-header {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  background-color: var(--color-paper);
  border-bottom: 1px solid var(--color-rule);
  transition: transform var(--dur-short) var(--ease-out);

  /* 只用 transform 位移：sticky 仍占位在文档流内，收起不牵动正文 */
  &--hidden {
    transform: translateY(-100%);
  }

  &__inner {
    /* 留白常驻并算进 max-width：正文仍是 --container-max 居中，窄桌面下不再贴边 */
    max-width: calc(var(--container-max) + var(--space-lg) * 2);
    width: 100%;
    margin-inline: auto;
    padding-inline: var(--space-lg);
    height: var(--header-h);
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-md);
  }

  &__left {
    display: flex;
    align-items: center;
    gap: var(--space-lg);
    min-width: 0;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
  }
}

.wordmark {
  font-size: var(--text-base);
  color: var(--color-ink);
  white-space: nowrap;

  &:hover {
    color: var(--color-accent);
  }
}

.site-nav {
  display: none;
  align-items: center;
  gap: var(--space-md);

  &__link {
    font-size: var(--text-sm);
    color: var(--color-ink-3);
    padding-block: var(--space-2xs);
    white-space: nowrap;
    transition: color var(--dur-micro) var(--ease-out);

    &:hover {
      color: var(--color-ink);
    }

    &.is-active {
      color: var(--color-ink);
      box-shadow: inset 0 -2px 0 0 var(--color-accent);
    }
  }
}

/* 全站唯一的搜索框，常驻顶栏；聚焦即拉长，宽度随视口收，窄屏不挤掉其他控件 */
.search-box {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;

  &__icon {
    position: absolute;
    inset-inline-start: var(--space-sm);
    color: var(--color-ink-4);
    pointer-events: none;
  }

  &__clear {
    position: absolute;
    inset-inline-end: var(--space-2xs);
    display: grid;
    place-items: center;
    width: 1.5rem;
    height: 1.5rem;
    color: var(--color-ink-4);
    border-radius: var(--radius-sm);
    transition: color var(--dur-micro) var(--ease-out);

    &:hover {
      color: var(--color-ink-2);
    }
  }

  &__input {
    width: min(11rem, 34vw);
    min-width: 0;
    height: 2rem;
    padding-inline: calc(var(--space-sm) * 2 + 0.875rem) calc(var(--space-sm) + 1.5rem);
    font-size: var(--text-sm);
    font-family: var(--font-body);
    color: var(--color-ink);
    background-color: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-sm);
    transition:
      width var(--dur-short) var(--ease-out),
      border-color var(--dur-micro) var(--ease-out);

    &:focus {
      outline: none;
      width: min(20rem, 46vw);
      border-color: var(--color-rule-strong);
    }

    &::placeholder {
      color: var(--color-ink-4);
    }

    &::-webkit-search-cancel-button {
      -webkit-appearance: none;
    }
  }
}

.header-account {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border-radius: var(--radius-sm);
  color: var(--color-ink-3);
  transition:
    color var(--dur-micro) var(--ease-out),
    background-color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-ink);
    background-color: var(--color-paper-3);
  }
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2xs);
  height: 2rem;
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

  &:active {
    transform: translateY(1px);
  }
}

.auth-link {
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  white-space: nowrap;

  &:hover {
    color: var(--color-accent);
  }
}

.avatar-btn {
  cursor: pointer;
}

.burger {
  display: grid;
}

/* ---- 主区 ---- */
.layout-main {
  flex: 1;
  width: 100%;
}

.container {
  /* 与顶栏 __inner 同一套留白算法，保证左右边缘对齐 */
  max-width: calc(var(--container-max) + var(--space-lg) * 2);
  margin-inline: auto;
  padding-inline: var(--space-lg);
  padding-block: var(--space-lg) var(--space-2xl);
}

/* ---- 移动端导航抽屉 ---- */
.drawer-nav {
  display: flex;
  flex-direction: column;
  padding-block: var(--space-md);

  &__label {
    margin-top: var(--space-md);
    margin-bottom: var(--space-2xs);
    font-family: var(--font-mono);
    font-size: var(--text-xs);
    color: var(--color-ink-4);
  }

  &__link {
    padding: var(--space-sm) 0;
    font-size: var(--text-base);
    color: var(--color-ink);
    text-align: start;
    border-bottom: 1px solid var(--color-rule);
    transition: color var(--dur-micro) var(--ease-out);

    &:hover {
      color: var(--color-accent);
    }
  }
}

/* ---- 断点：≥960px 收起汉堡、展开主导航 ---- */
@media (min-width: 60rem) {
  .burger {
    display: none;
  }

  .site-nav {
    display: flex;
  }
}

/* ---- ≤600px 顶栏一行放不下账号簇，让它让位给搜索框（抽屉内已有同组链接） ---- */
@media (max-width: 37.5rem) {
  .header-account {
    display: none;
  }
}

/* ---- 系统「减弱动态效果」：瞬时切换；global.css 的 150ms 兜底会拖住顶栏 ---- */
@media (prefers-reduced-motion: reduce) {
  .site-header {
    transition: none !important;
  }
}
</style>
