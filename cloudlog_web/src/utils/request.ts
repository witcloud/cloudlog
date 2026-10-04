import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 失败时不弹全局 toast、也不参与 401 的整站登出：调用方自己兜（如登出请求本身） */
    silent?: boolean
  }
}

/**
 * 同一句失败文案在短时间内的重复播报只留一次。
 * 断网写作时自动保存每 30 秒撞一回网络，不去重就是满屏红 toast——
 * 而那时候用户真正该看到的是顶栏那句「离线·未保存」。
 */
let lastNotice = { text: '', at: 0 }
const notifyError = (text: string) => {
  const now = Date.now()
  if (text === lastNotice.text && now - lastNotice.at < 5000) return
  lastNotice = { text, at: now }
  ElMessage.error(text)
}

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
      if (!response.config.silent) notifyError(res.message || '操作失败，请稍后重试')
      return Promise.reject(new Error(res.message || '操作失败'))
    } else {
      return res
    }
  },
  (error) => {
    // 主动取消不是故障：撤掉在途上传那一枪不该在屏幕上留一句红字
    if (axios.isCancel(error)) return Promise.reject(error)

    let message = error.message
    if (error.response && error.response.data && error.response.data.message) {
      message = error.response.data.message
    }
    const silent = !!error.config?.silent

    // 401 只可能是会话失效（「权限不足」后端已单独留给 403）。本地 token 必须清掉，
    // 否则顶栏仍按已登录渲染，点进受保护页只会反复报错。
    // 但这一整套副作用要跳过登录页本身：口令打错后端同样回 401，
    // 不跳过的话在登录页试错一次就把当前会话一起吃掉（守卫已保证登录后不会停在 /login）
    if (error.response && error.response.status === 401 && !silent) {
      if (router.currentRoute.value.path !== '/login') {
        useUserStore().logout()
        // 带 redirect：重新登录后回到被打断的页面，而不是落回首页丢掉全部上下文
        router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    }

    if (!silent) notifyError(message || '网络错误')
    return Promise.reject(error)
  },
)

export default request
