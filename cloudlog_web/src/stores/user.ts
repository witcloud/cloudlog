import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '@/api/types/auth'
import { logout as logoutApi } from '@/api/auth'

const TOKEN_KEY = 'token'
const USER_KEY = 'userInfo'
const EMAIL_KEY = 'lastEmail'

/** 「记住我」决定落点：勾选进 localStorage（活到 JWT 过期），不勾进 sessionStorage（关掉标签页即失效）。
 *  读取处两个存储都看，写入处清掉另一个，保证任一时刻同一把键只存在一处 */
const readStored = (key: string) => localStorage.getItem(key) || sessionStorage.getItem(key)

const writeStored = (key: string, value: string, remember: boolean) => {
  const target = remember ? localStorage : sessionStorage
  const other = remember ? sessionStorage : localStorage
  target.setItem(key, value)
  other.removeItem(key)
}

export const useUserStore = defineStore('user', () => {
  const token = ref(readStored(TOKEN_KEY) || '')
  // 用户 ID 现在是雪花 ID（接口下发字符串）。改策略前写进本地存储的缓存里 id 还是数字，
  // 与接口值 === 比较会恒不相等（自己的文章也会亮「关注」按钮），读出来先归一成字符串。
  // 缓存可能被手改成非法 JSON：解析失败按未登录处理，不能让坏缓存把整站初始化炸成白屏
  let cached: UserInfo | null = null
  try {
    cached = JSON.parse(readStored(USER_KEY) || 'null') as UserInfo | null
    if (cached) cached.id = String(cached.id)
  } catch {
    localStorage.removeItem(USER_KEY)
    sessionStorage.removeItem(USER_KEY)
  }
  const userInfo = ref<UserInfo | null>(cached)
  const lastEmail = ref(readStored(EMAIL_KEY) || '')

  const setAuth = (credential: {
    token: string
    user: UserInfo
    email: string
    remember: boolean
  }) => {
    token.value = credential.token
    userInfo.value = credential.user
    lastEmail.value = credential.email
    writeStored(TOKEN_KEY, credential.token, credential.remember)
    writeStored(USER_KEY, JSON.stringify(credential.user), credential.remember)
    // 邮箱跟着凭据进同一个存储：不勾「记住我」时这台机器上不该多留下一样东西
    writeStored(EMAIL_KEY, credential.email, credential.remember)
  }

  const logout = () => {
    const hadToken = !!token.value
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
    // 服务端同步作废该账号的全部 token（失效水位）：JWT 无状态，只清本地等于 token 仍在有效期。
    // fire-and-forget——本地凭据已清，网络失败不该阻塞登出，也不该为此弹错
    if (hadToken) {
      void logoutApi().catch(() => {})
    }
    // lastEmail 故意不清：它服务的不是登录态，是「下次进登录页不必重打邮箱」
  }

  /** 登录态不变、只刷新缓存的用户资料（改头像/改资料后调用）。
   *  走 writeStored 保持「与 token 同存储」的契约——外部直接 setItem(localStorage)
   *  会在未记住我的会话里留下孤儿缓存或残留旧资料 */
  const updateCachedUser = (user: UserInfo) => {
    userInfo.value = user
    const remember = localStorage.getItem(TOKEN_KEY) !== null
    writeStored(USER_KEY, JSON.stringify(user), remember)
  }

  return {
    token,
    userInfo,
    lastEmail,
    setAuth,
    updateCachedUser,
    logout,
  }
})
