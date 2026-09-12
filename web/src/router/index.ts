import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '',
          name: 'Home',
          component: () => import('../views/home/HomeView.vue'),
          meta: { title: '首页' },
        },
        {
          path: 'article/:id',
          name: 'ArticleDetail',
          component: () => import('../views/article/ArticleDetail.vue'),
          meta: { title: '文章详情' },
        },
        {
          path: 'editor/:id?',
          name: 'ArticleEditor',
          component: () => import('../views/article/ArticleEditor.vue'),
          meta: { requiresAuth: true, title: '编辑器' },
        },
        {
          path: 'publish-success',
          name: 'PublishSuccess',
          component: () => import('../views/article/PublishSuccess.vue'),
          meta: { requiresAuth: true, title: '发布成功' },
        },
        {
          path: 'about',
          name: 'About',
          component: () => import('../views/AboutView.vue'),
          meta: { title: '关于我' },
        },
        {
          path: 'login',
          name: 'Login',
          component: () => import('../views/auth/LoginView.vue'),
          meta: { title: '登录' },
        },
        {
          path: 'register',
          name: 'Register',
          component: () => import('../views/auth/LoginView.vue'), // Reuse login for now
          meta: { title: '注册' },
        },
        {
          path: 'drafts',
          name: 'Drafts',
          component: () => import('../views/auth/DraftsView.vue'),
          meta: { requiresAuth: true, title: '草稿箱' },
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('../views/profile/ProfileView.vue'),
          meta: { requiresAuth: true, title: '个人中心' },
        },
        {
          path: 'admin',
          name: 'Admin',
          component: () => import('../views/admin/CategoryTagView.vue'),
          meta: { requiresAuth: true, role: 'blogger', title: '系统管理' },
        },
      ],
    },
  ],
})

router.beforeEach((to, from, next) => {
  // Set page title
  if (to.meta.title) {
    document.title = `${to.meta.title} - CloudLog`
  } else {
    document.title = 'CloudLog'
  }

  // Auth guard
  const userStore = useUserStore()
  const isLoggedIn = !!userStore.token
  if (to.meta.requiresAuth && !isLoggedIn) {
    next('/login')
  } else {
    next()
  }
})

export default router
