import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

// 创建 axios 实例
const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 凭据落点由 user store 持有（「记住我」决定进 localStorage 还是 sessionStorage），这里只认 store
    const token = useUserStore().token
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data

    // 统一响应契约：成功 code 恒为 200（business-model.md §5.4），其余一律按失败提示
    if (res.code !== 200) {
      ElMessage.error(res.message || 'Error')
      return Promise.reject(new Error(res.message || 'Error'))
    } else {
      return res
    }
  },
  (error) => {
    let message = error.message
    if (error.response && error.response.data && error.response.data.message) {
      message = error.response.data.message
    }

    // 401 只可能是会话失效（「权限不足」后端已单独留给 403）。本地 token 必须清掉，
    // 否则顶栏仍按已登录渲染，点进受保护页只会反复报错
    if (error.response && error.response.status === 401) {
      useUserStore().logout()
      if (router.currentRoute.value.path !== '/login') {
        router.replace('/login')
      }
    }

    ElMessage.error(message || '网络错误')
    return Promise.reject(error)
  },
)

export default request
