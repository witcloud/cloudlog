import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import MainLayout from '../layouts/MainLayout.vue'
import { useUserStore } from '@/stores/user'
import { useSiteStore } from '@/stores/site'
import { resetSeoMeta, resolveTitle } from '@/utils/seo'

/* 顶部细条：反馈的是「这次导航的请求还没回来」，覆盖懒加载路由的 chunk 下载与接口等待。
   旋转圈与右端光晕不属于本站语言，见 global.css 的压掉规则。 */
NProgress.configure({ showSpinner: false })

/** 待恢复的滚动位置：前进为 0，后退为 savedPosition。
 *  不能在导航确认时立即滚动——旧页面此时仍可见，会先瞬移到顶部再换页（闪屏）。
 *  由 MainLayout 在过渡 after-leave（旧页面移除）后消费。 */
export const scrollControl = {
  pendingY: null as number | null,
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(_to, _from, savedPosition) {
    scrollControl.pendingY = savedPosition ? savedPosition.top : 0
    // 阻止路由器自带的「确认即滚动」，改由布局在页面交换后执行
    return false
  },
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          // 编辑器为全屏工作台；bare 表示不渲染站点顶栏等外框
          // 放在布局内是为了让外层 keep-alive 的缓存（如首页）跨编辑器访问保留
          // role 与后端一致：POST/PUT /articles 都要求 ADMIN，普通用户进来只会处处 403
          path: 'editor/:id?',
          name: 'ArticleEditor',
          component: () => import('../views/article/ArticleEditor.vue'),
          meta: { requiresAuth: true, role: 'admin', bare: true, title: '编辑器' },
        },
        {
          path: '',
          name: 'Home',
          component: () => import('../views/home/HomeView.vue'),
          meta: { title: '首页' },
        },
        {
          path: 'articles/:id',
          name: 'ArticleDetail',
          component: () => import('../views/article/ArticleDetail.vue'),
          meta: { title: '文章详情' },
        },
        {
          // 他人作者主页：公开只读，编辑入口仍只在 /profile
          path: 'authors/:id',
          name: 'AuthorProfile',
          component: () => import('../views/author/AuthorProfileView.vue'),
          meta: { title: '作者主页' },
        },
        {
          path: 'search',
          name: 'Search',
          component: () => import('../views/search/SearchView.vue'),
          meta: { title: '搜索' },
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
          component: () => import('../views/auth/LoginView.vue'), // 注册与登录是同一组件的两种模式，靠 route.name 切换
          meta: { title: '注册' },
        },
        {
          path: 'forgot-password',
          name: 'ForgotPassword',
          component: () => import('../views/auth/ForgotPasswordView.vue'),
          meta: { title: '找回密码' },
        },
        {
          // 草稿只能由编辑器产生，编辑器是管理员域，草稿箱随之同域
          path: 'drafts',
          name: 'Drafts',
          component: () => import('../views/auth/DraftsView.vue'),
          meta: { requiresAuth: true, role: 'admin', title: '草稿箱' },
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('../views/profile/ProfileView.vue'),
          meta: { requiresAuth: true, title: '个人主页' },
        },
        {
          path: 'settings',
          name: 'Settings',
          component: () => import('../views/settings/SettingsView.vue'),
          meta: { requiresAuth: true, title: '账号设置' },
        },
        {
          // 兜底路由必须排在 children 末尾，否则会抢在具名路由之前命中；不设 requiresAuth，404 无需登录
          path: ':pathMatch(.*)*',
          name: 'NotFound',
          component: () => import('../views/NotFoundView.vue'),
          meta: { title: '页面不存在' },
        },
      ],
    },
    {
      // 管理后台：独立布局（左侧导航 + 顶栏），仅管理员可进入
      path: '/admin',
      component: () => import('../layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, role: 'admin', title: '管理后台' },
      children: [
        { path: '', redirect: { name: 'AdminDashboard' } },
        {
          path: 'dashboard',
          name: 'AdminDashboard',
          component: () => import('../views/admin/AdminDashboard.vue'),
          meta: { title: '工作台' },
        },
        {
          path: 'articles',
          name: 'AdminArticles',
          component: () => import('../views/admin/AdminArticlesView.vue'),
          meta: { title: '文章管理' },
        },
        {
          path: 'comments',
          name: 'AdminComments',
          component: () => import('../views/admin/AdminCommentsView.vue'),
          meta: { title: '评论管理' },
        },
        {
          path: 'categories',
          name: 'AdminCategories',
          component: () => import('../views/admin/AdminCategoriesView.vue'),
          meta: { title: '分类管理' },
        },
        {
          path: 'tags',
          name: 'AdminTags',
          component: () => import('../views/admin/AdminTagsView.vue'),
          meta: { title: '标签管理' },
        },
        {
          path: 'users',
          name: 'AdminUsers',
          component: () => import('../views/admin/AdminUsersView.vue'),
          meta: { title: '用户管理' },
        },
        {
          path: 'settings',
          name: 'AdminSettings',
          component: () => import('../views/admin/AdminSettingsView.vue'),
          meta: { title: '系统设置' },
        },
        {
          // 日志管理：一类日志一个页签，页签由路由驱动（AdminLogsLayout 读当前路由决定选中项）
          path: 'logs',
          component: () => import('../views/admin/logs/AdminLogsLayout.vue'),
          children: [
            { path: '', redirect: { name: 'AdminVisitLogs' } },
            {
              path: 'visits',
              name: 'AdminVisitLogs',
              component: () => import('../views/admin/logs/AdminVisitLogsView.vue'),
              meta: { title: '访问日志' },
            },
            {
              path: 'login',
              name: 'AdminLoginLogs',
              component: () => import('../views/admin/logs/AdminLoginLogsView.vue'),
              meta: { title: '登录日志' },
            },
          ],
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  NProgress.start()

  // 这一页的 head 从这里开始：标题按路由 meta 重写，描述/关键词复位到站点级默认，
  // 免得从文章页离开后 meta description 还挂着上一篇文章的摘要
  // 站点名参与标题，首屏先等它一回（只等第一次，之后走 store 缓存）；拉不到就用内置兜底名
  await useSiteStore().ensureLoaded()
  document.title = resolveTitle(to.meta.title as string | undefined)
  resetSeoMeta()

  // Auth guard
  const userStore = useUserStore()
  const isLoggedIn = !!userStore.token
  if (to.meta.requiresAuth && !isLoggedIn) {
    return '/login'
  }

  // Role guard：声明了 role 的路由仅对应角色可进入（如 /admin 仅管理员）
  if (to.meta.role && userStore.userInfo?.role !== to.meta.role) {
    return '/'
  }
})

router.afterEach(() => {
  NProgress.done()
})

// 懒加载 chunk 请求失败时导航以 reject 结束，afterEach 不会执行（vue-router 走 onError 后直接抛出），
// 少了这一条细条会永久停在半途
router.onError(() => {
  NProgress.done()
})

export default router
