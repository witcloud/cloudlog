<template>
  <div class="admin">
    <!-- 侧边导航：≥960px 固定显示 -->
    <aside class="admin__aside">
      <div class="admin__brand">
        <RouterLink to="/" class="admin__wordmark"><SiteLogo /></RouterLink>
        <span class="admin__brand-badge">管理后台</span>
      </div>
      <nav class="admin__nav" aria-label="后台导航">
        <AdminNavLinks :groups="navGroups" @navigate="navOpen = false" />
      </nav>
    </aside>

    <div class="admin__main">
      <header class="admin__topbar">
        <div class="admin__topbar-left">
          <button class="admin__icon-btn burger" aria-label="打开导航" @click="navOpen = true">
            <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
              <path
                d="M2.5 4h11M2.5 8h11M2.5 12h11"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
              />
            </svg>
          </button>
          <nav class="admin__crumb" aria-label="面包屑">
            <RouterLink to="/admin" class="admin__crumb-root">管理后台</RouterLink>
            <span class="admin__crumb-sep" aria-hidden="true">/</span>
            <span class="admin__crumb-current">{{ currentTitle }}</span>
          </nav>
        </div>

        <div class="admin__topbar-right">
          <RouterLink to="/" class="admin__back">
            <svg viewBox="0 0 16 16" width="13" height="13" fill="none" aria-hidden="true">
              <path
                d="M9.5 3.5 5 8l4.5 4.5"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            返回博客
          </RouterLink>

          <button
            class="admin__icon-btn"
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

          <el-dropdown trigger="click" :popper-options="{ strategy: 'fixed' }">
            <el-avatar :size="30" :src="userAvatar" class="admin__avatar" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">个人主页</el-dropdown-item>
                <el-dropdown-item @click="router.push('/settings')">设置</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="admin__content" @click="onContentClick">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in" @after-leave="onAfterLeave">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <!-- 移动端导航抽屉（≤960px） -->
    <el-drawer v-model="navOpen" direction="ltr" size="75%" :with-header="false">
      <div class="admin-drawer">
        <div class="admin-drawer__brand">
          <RouterLink to="/" class="admin__wordmark" @click="navOpen = false"
            ><SiteLogo
          /></RouterLink>
          <span class="admin__brand-badge">管理后台</span>
        </div>
        <nav class="admin-drawer__nav" aria-label="后台导航">
          <AdminNavLinks :groups="navGroups" @navigate="navOpen = false" />
        </nav>
        <RouterLink to="/" class="admin-drawer__back" @click="navOpen = false"
          >← 返回博客</RouterLink
        >
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Odometer,
  Document,
  ChatDotRound,
  FolderOpened,
  PriceTag,
  User,
  Setting,
  Tickets,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'
import { formatMinioUrl, defaultAvatarUrl } from '@/config'
import { scrollControl } from '@/router'
import '@/assets/styles/admin.css'
import AdminNavLinks from '@/views/admin/components/AdminNavLinks.vue'
import SiteLogo from '@/components/SiteLogo.vue'

interface NavItem {
  path: string
  label: string
  icon: Component
}

interface NavGroup {
  label: string | null
  items: NavItem[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isDark, toggle } = useTheme()

const navOpen = ref(false)

const navGroups: NavGroup[] = [
  {
    label: null,
    items: [{ path: '/admin/dashboard', label: '工作台', icon: Odometer }],
  },
  {
    label: '内容管理',
    items: [
      { path: '/admin/articles', label: '文章', icon: Document },
      { path: '/admin/comments', label: '评论', icon: ChatDotRound },
      { path: '/admin/categories', label: '分类', icon: FolderOpened },
      { path: '/admin/tags', label: '标签', icon: PriceTag },
    ],
  },
  {
    label: '系统管理',
    items: [
      { path: '/admin/users', label: '用户', icon: User },
      { path: '/admin/settings', label: '系统设置', icon: Setting },
      // 两类日志共用这一个入口：导航按管理对象切分，访问日志与登录日志是同一对象下的两个页签
      { path: '/admin/logs', label: '日志管理', icon: Tickets },
    ],
  },
]

const currentTitle = computed(() => (route.meta.title as string) || '')

const userAvatar = computed(() => formatMinioUrl(userStore.userInfo?.avatar) || defaultAvatarUrl)

/** 布局间切换（前台 ↔ 后台）与后台内部跳转后，在页面交换完成时恢复/回到顶部 */
const onAfterLeave = () => {
  const y = scrollControl.pendingY
  scrollControl.pendingY = null
  window.scrollTo(0, y ?? 0)
}

/**
 * 翻页后回到表头。分页器钉在卡片底部，翻完页人还停在页底，下一眼看不到第一行；
 * 而换页不改路由，上面那条 after-leave 管不到。
 * 走一处委托而不是给六个分页器各加一个 handler：后台每一张表都该有这一条，
 * 少一处就多一处不一致。
 */
const onContentClick = (event: MouseEvent) => {
  if ((event.target as HTMLElement).closest('.el-pagination')) window.scrollTo(0, 0)
}

onMounted(() => {
  window.scrollTo(0, 0)
})

const handleLogout = () => {
  navOpen.value = false
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}
</script>

<style scoped lang="scss">
.admin {
  display: flex;
  min-height: 100dvh;
  background-color: var(--color-paper);
}

/* ---- 侧边导航 ---- */
.admin__aside {
  position: fixed;
  inset-block: 0;
  left: 0;
  z-index: var(--z-raised);
  display: flex;
  flex-direction: column;
  width: 13.5rem;
  background-color: var(--color-paper-2);
  border-right: 1px solid var(--color-rule);
}

.admin__brand {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  height: var(--header-h);
  padding-inline: var(--space-lg);
  border-bottom: 1px solid var(--color-rule);
  flex-shrink: 0;
}

.admin__wordmark {
  font-size: var(--text-base);
  color: var(--color-ink);
  white-space: nowrap;

  &:hover {
    color: var(--color-accent);
  }
}

.admin__brand-badge {
  padding: 1px var(--space-2xs);
  font-size: var(--text-xs);
  color: var(--color-accent-ink);
  background-color: var(--color-accent-wash);
  border-radius: var(--radius-xs);
  white-space: nowrap;
}

.admin__nav {
  flex: 1;
  padding: var(--space-md) var(--space-sm);
  overflow-y: auto;
}

/* ---- 顶栏 ---- */
.admin__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  margin-left: 13.5rem;
}

.admin__topbar {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  height: var(--header-h);
  padding-inline: var(--space-lg);
  background-color: var(--color-paper-2);
  border-bottom: 1px solid var(--color-rule);
}

.admin__topbar-left {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

/* 优先级需盖过 .admin__icon-btn 的 display: grid */
.admin__icon-btn.burger {
  display: none;
}

.admin__crumb {
  display: flex;
  align-items: center;
  gap: var(--space-2xs);
  min-width: 0;
  font-size: var(--text-sm);
  white-space: nowrap;

  &-root {
    color: var(--color-ink-4);
    transition: color var(--dur-micro) var(--ease-out);

    &:hover {
      color: var(--color-ink-2);
    }
  }

  &-sep {
    color: var(--color-ink-4);
  }

  &-current {
    overflow: hidden;
    color: var(--color-ink);
    text-overflow: ellipsis;
  }
}

.admin__topbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.admin__back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2xs);
  font-size: var(--text-sm);
  color: var(--color-ink-3);
  white-space: nowrap;
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
  }
}

.admin__icon-btn {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  color: var(--color-ink-3);
  border-radius: var(--radius-sm);
  transition:
    color var(--dur-micro) var(--ease-out),
    background-color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-ink);
    background-color: var(--color-paper-3);
  }
}

.admin__avatar {
  cursor: pointer;
}

/* ---- 内容区 ---- */
.admin__content {
  width: 100%;
  max-width: 84rem;
  margin-inline: auto;
  padding: var(--space-lg);
  flex: 1;
}

/* ---- 移动端抽屉 ---- */
.admin-drawer {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.admin-drawer__brand {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--color-rule);
}

.admin-drawer__nav {
  padding-block: var(--space-sm);
}

.admin-drawer__back {
  margin-top: auto;
  padding: var(--space-sm) 0;
  font-size: var(--text-sm);
  color: var(--color-ink-3);
  border-top: 1px solid var(--color-rule);
  transition: color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
  }
}

/* ---- 断点：≤960px 收起侧栏，改用抽屉 ---- */
@media (max-width: 60rem) {
  .admin__aside {
    display: none;
  }

  .admin__main {
    margin-left: 0;
  }

  .admin__icon-btn.burger {
    display: grid;
  }

  .admin__content {
    padding: var(--space-md);
  }
}
</style>
