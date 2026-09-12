import { ref, watchEffect } from 'vue'

/**
 * 主题切换：默认跟随系统偏好，用户手动切换后写入 localStorage。
 * 模块级单例，所有组件共享同一份状态。
 */
const STORAGE_KEY = 'cloudlog-theme'

function readStoredPreference(): 'light' | 'dark' | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

const systemDark = window.matchMedia('(prefers-color-scheme: dark)')

const preference = ref<'light' | 'dark' | null>(readStoredPreference())
const isDark = ref(preference.value ? preference.value === 'dark' : systemDark.matches)

// 未手动选择时跟随系统变化
systemDark.addEventListener('change', (event) => {
  if (!preference.value) {
    isDark.value = event.matches
  }
})

watchEffect(() => {
  document.documentElement.classList.toggle('dark', isDark.value)
})

export function useTheme() {
  const toggle = () => {
    const next: 'light' | 'dark' = isDark.value ? 'light' : 'dark'
    preference.value = next
    isDark.value = next === 'dark'
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      /* 隐私模式下存储失败可接受，仅本次会话生效 */
    }
  }

  return { isDark, toggle }
}
