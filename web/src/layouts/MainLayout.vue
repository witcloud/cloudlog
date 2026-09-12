<template>
  <div class="main-layout">
    <el-header class="header">
      <div class="header-content">
        <div class="logo" @click="$router.push('/')">
          <img src="/logo.png" alt="CloudLog" class="logo-img" />
        </div>

        <nav class="nav">
          <el-menu mode="horizontal" :ellipsis="false" :router="true">
            <el-menu-item index="/">首页</el-menu-item>
            <el-menu-item index="/about">关于</el-menu-item>
          </el-menu>
        </nav>

        <div class="header-right">
          <el-input
            v-model="searchQuery"
            placeholder="搜索文章..."
            class="search-input"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <template v-if="isLoggedIn">
            <el-dropdown trigger="click" @command="handleWriteCommand">
              <el-button type="primary" class="write-btn">
                <el-icon><EditPen /></el-icon> 写文章
                <el-icon class="el-icon--right"><arrow-down /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="editor">发布文章</el-dropdown-item>
                  <el-dropdown-item command="drafts">草稿箱</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-dropdown trigger="click">
              <el-avatar :size="32" :src="userAvatar" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="$router.push('/profile')">个人主页</el-dropdown-item>
                  <el-dropdown-item v-if="isAdmin" @click="$router.push('/admin')"
                    >系统管理</el-dropdown-item
                  >
                  <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button link @click="$router.push('/login')">登录</el-button>
            <el-button type="primary" plain @click="$router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </el-header>

    <main class="main-content">
      <div class="container">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>

    <el-footer class="footer">
      <div class="footer-content">
        <p>© 2026 AyunBlog. Built with Vue 3 & Element Plus.</p>
        <div class="footer-links">
          <el-link :underline="false">GitHub</el-link>
          <el-link :underline="false">RSS</el-link>
          <el-link :underline="false">Privacy</el-link>
        </div>
      </div>
    </el-footer>

    <el-backtop :right="40" :bottom="40" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { formatMinioUrl } from '@/config'

const router = useRouter()
const userStore = useUserStore()

const searchQuery = ref('')
const isLoggedIn = computed(() => !!userStore.token)
const isAdmin = computed(() => userStore.userInfo?.role === 'admin')
const userAvatar = computed(() =>
  formatMinioUrl(userStore.userInfo?.avatar || '/default-avatar.png'),
)

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ name: 'Home', query: { q: searchQuery.value } })
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
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}
</script>

<style scoped lang="scss">
@use '../styles/variables.scss' as *;

.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  background-color: rgba($white, 0.8);
  backdrop-filter: blur(10px);
  position: sticky;
  top: 0;
  z-index: 1000;
  border-bottom: 1px solid $border-color;
  height: 64px;
  display: flex;
  align-items: center;

  .header-content {
    max-width: 1440px;
    margin: 0 auto;
    width: 100%;
    display: flex;
    align-items: center;
    padding: 0 $spacing-md;
  }

  .logo {
    display: flex;
    align-items: center;
    cursor: pointer;
    margin-right: $spacing-md;

    .logo-img {
      height: 32px;
      width: auto;
    }
  }

  .nav {
    flex: 1;
    .el-menu--horizontal {
      border-bottom: none;
      height: 64px;
      background: transparent;
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: $spacing-md;

    .search-input {
      width: 200px;
      transition: width 0.3s;
      &:focus-within {
        width: 260px;
      }
    }

    .write-btn {
      margin-right: $spacing-sm;
    }
  }
}

.main-content {
  flex: 1;
  padding: $spacing-lg 0;
  overflow: visible; // 确保不影响子元素的 sticky

  .container {
    max-width: 1200px; // 增加最大宽度以容纳三栏布局
    margin: 0 auto;
    padding: 0 $spacing-md;
  }
}

.footer {
  border-top: 1px solid $border-color;
  background-color: $white;
  padding: $spacing-xl 0;
  height: auto;

  .footer-content {
    max-width: 1200px;
    margin: 0 auto;
    text-align: center;
    color: $text-secondary;

    p {
      margin-bottom: $spacing-sm;
    }

    .footer-links {
      display: flex;
      justify-content: center;
      gap: $spacing-lg;
    }
  }
}

@media (max-width: 768px) {
  .header-content {
    .nav {
      display: none;
    }
    .search-input {
      display: none;
    }
  }
}
</style>
