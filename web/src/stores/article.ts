import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import type { Article, Category, Tag } from '@/api/types/article'
import { getArticleDetail } from '@/api/article'

export const useArticleStore = defineStore('article', () => {
  // 当前查看的文章详情
  const currentArticle = ref<Article | null>(null)
  const loading = ref(false)

  // --- 编辑器相关状态 ---
  const editArticle = reactive({
    id: null as number | null,
    title: '',
    content: '',
    cover: '' as string | null,
    categoryId: null as number | null,
    tags: [] as number[],
    excerpt: '',
    status: 'draft' as 'draft' | 'published' | 'scheduled',
    publishTime: null as string | null,
  })

  // 标记编辑器内容是否有未保存的更改
  const isDirty = ref(false)

  // 封面图本地文件
  const localCover = ref<File | null>(null)

  /**
   * 获取文章详情用于查看
   */
  const fetchArticleDetail = async (id: number) => {
    loading.value = true
    try {
      const res = await getArticleDetail(id)
      if (res.code === 200) {
        currentArticle.value = res.data
        return res.data
      }
    } catch (error) {
      console.error('Failed to fetch article detail:', error)
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
    editArticle.content = article.content || ''
    editArticle.cover = article.cover || null
    editArticle.categoryId = article.category?.id || article.categoryId || null
    editArticle.tags = article.tags?.map((t: any) => (typeof t === 'number' ? t : t.id)) || []
    editArticle.excerpt = article.excerpt || ''
    editArticle.status = (article.status as any) || 'draft'
    editArticle.publishTime = article.publishTime || null

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
    editArticle.excerpt = ''
    editArticle.status = 'draft'
    editArticle.publishTime = null

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
  }

  return {
    currentArticle,
    loading,
    editArticle,
    isDirty,
    localCover,
    fetchArticleDetail,
    initEditArticle,
    resetEditArticle,
    setLocalCover,
    clearArticle,
  }
})
