<template>
  <div class="layout">
    <header class="site-header">
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

          <RouterLink to="/" class="wordmark">CloudLog</RouterLink>

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
          <form class="search-form" role="search" @submit.prevent="goSearch">
            <svg
              class="search-form__icon"
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
              v-model="searchQuery"
              type="search"
              class="search-form__input"
              placeholder="搜索文章…"
              aria-label="搜索文章"
            />
          </form>

          <RouterLink to="/search" class="icon-btn search-link" aria-label="搜索">
            <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
              <path
                d="m10.5 10.5 3 3M7 11.5a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9Z"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
              />
            </svg>
          </RouterLink>

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

          <template v-if="isLoggedIn">
            <el-dropdown trigger="click" @command="handleWriteCommand">
              <button class="btn-outline write-btn">
                写文章
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
                  <el-dropdown-item command="editor">发布文章</el-dropdown-item>
                  <el-dropdown-item command="drafts">草稿箱</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <el-dropdown trigger="click">
              <el-avatar :size="30" :src="userAvatar" class="avatar-btn" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="router.push('/profile')">个人主页</el-dropdown-item>
                  <el-dropdown-item v-if="isAdmin" @click="router.push('/admin')"
                    >系统管理</el-dropdown-item
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
    </header>

    <main class="layout-main">
      <div class="container">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>

    <BackToTop />

    <!-- 移动端导航抽屉（≤960px） -->
    <el-drawer v-model="navOpen" direction="ltr" size="80%" :with-header="false" class="nav-drawer">
      <nav class="drawer-nav" aria-label="移动端导航">
        <p class="drawer-nav__label">导航</p>
        <RouterLink to="/" class="drawer-nav__link" @click="navOpen = false">首页</RouterLink>
        <RouterLink to="/about" class="drawer-nav__link" @click="navOpen = false">关于</RouterLink>
        <RouterLink to="/search" class="drawer-nav__link" @click="navOpen = false">搜索</RouterLink>

        <template v-if="isLoggedIn">
          <p class="drawer-nav__label">写作</p>
          <RouterLink to="/editor" class="drawer-nav__link" @click="navOpen = false"
            >发布文章</RouterLink
          >
          <RouterLink to="/drafts" class="drawer-nav__link" @click="navOpen = false"
            >草稿箱</RouterLink
          >
          <p class="drawer-nav__label">账号</p>
          <RouterLink to="/profile" class="drawer-nav__link" @click="navOpen = false"
            >个人主页</RouterLink
          >
          <RouterLink v-if="isAdmin" to="/admin" class="drawer-nav__link" @click="navOpen = false"
            >系统管理</RouterLink
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
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { formatMinioUrl } from '@/config'
import { useTheme } from '@/composables/useTheme'
import BackToTop from '@/components/BackToTop.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isDark, toggle } = useTheme()

const searchQuery = ref('')
const navOpen = ref(false)
const isLoggedIn = computed(() => !!userStore.token)
const isAdmin = computed(() => userStore.userInfo?.role === 'admin')
const userAvatar = computed(() =>
  formatMinioUrl(userStore.userInfo?.avatar || '/default-avatar.png'),
)

const goSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ path: '/search', query: { q: searchQuery.value.trim() } })
  }
}

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

  &__inner {
    max-width: var(--container-max);
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
  font-family: var(--font-mono);
  font-size: var(--text-base);
  font-weight: 600;
  letter-spacing: 0.01em;
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

/* 搜索：仅桌面展开为输入框 */
.search-form {
  display: none;
  position: relative;
  align-items: center;

  &__icon {
    position: absolute;
    inset-inline-start: var(--space-sm);
    color: var(--color-ink-4);
    pointer-events: none;
  }

  &__input {
    width: 11rem;
    height: 2rem;
    padding-inline: calc(var(--space-sm) * 2 + 0.875rem) var(--space-sm);
    font-size: var(--text-sm);
    font-family: var(--font-body);
    color: var(--color-ink);
    background-color: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-sm);
    transition: border-color var(--dur-micro) var(--ease-out);

    &::placeholder {
      color: var(--color-ink-4);
    }

    &:focus {
      outline: none;
      border-color: var(--color-rule-strong);
    }

    &::-webkit-search-cancel-button {
      -webkit-appearance: none;
    }
  }
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

.search-link {
  display: grid;
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
  max-width: var(--container-max);
  margin-inline: auto;
  padding: var(--space-xl) var(--space-lg) var(--space-2xl);
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

/* ---- 断点：≥960px 展开完整顶栏 ---- */
@media (min-width: 60rem) {
  .burger,
  .search-link {
    display: none;
  }

  .site-nav,
  .search-form {
    display: flex;
  }
}
</style>
