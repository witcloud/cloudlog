import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getSiteInfo } from '@/api/site'

/** 内置兜底值，与后端 SettingsService 的缺省值同词同数：公开设置还没拉到（首屏、后端不可达）时靠它们撑住版式 */
const FALLBACK_SITE_NAME = 'CloudLog'
const FALLBACK_COMMENT_MAX_LENGTH = 500

export const useSiteStore = defineStore('site', () => {
  const siteName = ref(FALLBACK_SITE_NAME)
  const icpNumber = ref('')
  /** 评论字数上限，0 表示不限制 */
  const commentMaxLength = ref(FALLBACK_COMMENT_MAX_LENGTH)
  const loaded = ref(false)

  /** 站点名/备案号/评论上限来自同一个请求，整页共用一份；后台保存后下次进入即生效 */
  const load = async () => {
    try {
      const { data } = await getSiteInfo()
      siteName.value = data.siteName
      icpNumber.value = data.icpNumber
      commentMaxLength.value = data.commentMaxLength
    } catch {
      // 提示由拦截器统一处理，这里只保住兜底值，不让页脚和标题空着
    } finally {
      loaded.value = true
    }
  }

  /** 站点名要参与标签页标题，晚到就会让首屏一直挂着兜底名 —— 首次导航前等它一回，之后直接用缓存 */
  const ensureLoaded = () => (loaded.value ? Promise.resolve() : load())

  return { siteName, icpNumber, commentMaxLength, loaded, load, ensureLoaded }
})
