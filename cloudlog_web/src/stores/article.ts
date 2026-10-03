import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import dayjs from 'dayjs'
import type { Article, Tag } from '@/api/types/article'
import type { ArticleStatus } from '@/api/types/admin'
import { getArticleDetail } from '@/api/article'
import { splitParagraphLines } from '@/utils/markdownBlocks'

export const useArticleStore = defineStore('article', () => {
  // 当前查看的文章详情
  const currentArticle = ref<Article | null>(null)
  const loading = ref(false)
  // 详情加载失败且原因不是 404：文章可能在世，页面不能报成「不存在」
  const detailFailed = ref(false)

  // --- 编辑器相关状态 ---
  // 摘要不在这份状态里：它由服务端在对外发布时从正文派生，作者侧没有输入口
  const editArticle = reactive({
    id: null as string | null,
    title: '',
    content: '',
    cover: '' as string | null,
    categoryId: null as number | null,
    tags: [] as number[],
    status: 'draft' as ArticleStatus,
    publishTime: null as string | null,
  })

  // 标记编辑器内容是否有未保存的更改
  const isDirty = ref(false)

  /**
   * 库中状态与发布时刻：详情接口返回的只读事实。
   * editArticle.status 会被「发布方式」单选改写（表达本次提交意图），
   * 界面要回答「这篇文章现在是什么」只能看这两个字段。
   */
  const dbStatus = ref<ArticleStatus>('draft')
  const publishedAt = ref<string | null>(null)

  // 封面图本地文件
  const localCover = ref<File | null>(null)

  /**
   * 获取文章详情用于查看
   */
  const fetchArticleDetail = async (id: string) => {
    loading.value = true
    detailFailed.value = false
    try {
      const res = await getArticleDetail(id)
      if (res.code === 200) {
        currentArticle.value = res.data
        return res.data
      }
    } catch (error) {
      console.error('Failed to fetch article detail:', error)
      // 只有 404 才是「文章不存在」；5xx 与网络故障说明服务端没答上话，文章本身可能在世
      const status = (error as { response?: { status?: number } }).response?.status
      detailFailed.value = status !== 404
    } finally {
      loading.value = false
    }
    return null
  }

  /**
   * 初始化编辑器数据（回显逻辑）
   */
  const initEditArticle = (article: Article) => {
    editArticle.id = article.id
    editArticle.title = article.title || ''
    // 回显即规整：库里的老正文一段多行，进编辑器后标题会落到整块首行
    editArticle.content = splitParagraphLines(article.content || '')
    editArticle.cover = article.cover || null
    editArticle.categoryId = article.category?.id || article.categoryId || null
    editArticle.tags =
      article.tags?.map((t: Tag | number) => (typeof t === 'number' ? t : t.id)) || []
    editArticle.status = (article.status as ArticleStatus) || 'draft'
    // 定时文章的计划时刻即 published_at（调度器沿用计划时刻，见 db-design.md §5.3），
    // 详情接口不单独回传 publishTime，回显时按对外契约格式恢复，避免改定时文章被迫重选时刻
    editArticle.publishTime =
      article.status === 'scheduled' && article.publishedAt
        ? dayjs(article.publishedAt).format('YYYY-MM-DD HH:mm:ss')
        : null
    dbStatus.value = editArticle.status
    publishedAt.value = article.publishedAt || null

    // 清除之前的本地状态
    localCover.value = null
    isDirty.value = false
  }

  /**
   * 重置编辑器状态
   */
  const resetEditArticle = () => {
    editArticle.id = null
    editArticle.title = ''
    editArticle.content = ''
    editArticle.cover = null
    editArticle.categoryId = null
    editArticle.tags = []
    editArticle.status = 'draft'
    editArticle.publishTime = null
    dbStatus.value = 'draft'
    publishedAt.value = null

    localCover.value = null
    isDirty.value = false
  }

  /**
   * 设置本地封面预览
   */
  const setLocalCover = (file: File) => {
    localCover.value = file
  }

  const clearArticle = () => {
    currentArticle.value = null
    detailFailed.value = false
  }

  return {
    currentArticle,
    loading,
    detailFailed,
    editArticle,
    isDirty,
    dbStatus,
    publishedAt,
    localCover,
    fetchArticleDetail,
    initEditArticle,
    resetEditArticle,
    setLocalCover,
    clearArticle,
  }
})
