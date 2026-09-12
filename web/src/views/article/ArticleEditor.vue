<template>
  <div class="article-editor">
    <el-card class="editor-card" shadow="never">
      <template #header>
        <div class="editor-header">
          <!-- 文章标题输入 -->
          <el-input
            v-model="article.title"
            placeholder="请输入文章标题..."
            class="title-input"
            maxlength="100"
          />
          <div class="header-actions">
            <span v-if="articleStore.isDirty" class="save-status">
              <el-icon class="is-loading" v-if="submitting"><Loading /></el-icon>
              {{ submitting ? '保存中...' : '有未保存的更改' }}
            </span>
            <span v-else-if="article.id" class="save-status success">
              <el-icon><Check /></el-icon> 已保存到草稿
            </span>
            <el-button :loading="submitting" @click="handleSaveDraft">保存草稿</el-button>
            <el-button
              type="primary"
              :disabled="!article.title || !article.content"
              :loading="submitting"
              @click="showPublishDialog = true"
            >
              发布文章
            </el-button>
          </div>
        </div>
      </template>

      <!-- Markdown 编辑器 -->
      <div class="editor-content">
        <md-editor
          v-model="article.content"
          placeholder="开始你的创作..."
          :toolbars-exclude="['github']"
          @on-upload-img="onUploadImg"
          style="height: calc(100vh - 250px)"
        />
      </div>
    </el-card>

    <!-- 发布设置弹窗 -->
    <PublishDialog v-model="showPublishDialog" :loading="submitting" @confirm="handleRealSave" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Check } from '@element-plus/icons-vue'
import { useArticleStore } from '@/stores/article'
import {
  getArticleDetail,
  createArticle,
  updateArticle,
  uploadArticleImage,
  uploadCoverImage,
} from '@/api/article'
import PublishDialog from './components/PublishDialog.vue'
import { formatMinioUrl } from '@/config'

const route = useRoute()
const router = useRouter()
const articleStore = useArticleStore()

// 使用 Pinia store 中的状态
const article = articleStore.editArticle
const showPublishDialog = ref(false)
const submitting = ref(false)
const autoSaveTimer = ref<any>(null)

// 是否为编辑模式：通过文章 ID 判定，比 local ref 更可靠
const isEdit = computed(() => !!article.id)

const loaded = ref(false) // 初始为 false

/**
 * 加载文章数据
 */
const loadArticleData = async (id?: string) => {
  if (id) {
    try {
      const res = await getArticleDetail(Number(id))
      if (res.code === 200) {
        articleStore.initEditArticle(res.data)
        // 加载完成
        loaded.value = true
      } else {
        ElMessage.error('文章不存在或无权编辑')
        router.replace('/editor')
      }
    } catch (error) {
      console.error('Failed to load article:', error)
      ElMessage.error('获取文章详情失败')
    }
  } else {
    // 如果没有 ID，且 Store 中有 ID（可能是从编辑页跳转过来的），则重置
    if (article.id) {
      articleStore.resetEditArticle()
    }
  }
}

// 监听路由参数变化，处理相同组件不同 ID 的情况
watch(
  () => route.params.id,
  (newId) => {
    if (route.name === 'ArticleEditor') {
      loadArticleData(newId as string)
    }
  },
)

// 监听内容变化，标记为 dirty 并开启自动保存
watch(
  () => [article.title, article.content],
  () => {
    if (!loaded.value) return // 初次加载不标记
    articleStore.isDirty = true
    startAutoSave()
  },
)

onMounted(() => {
  loadArticleData(route.params.id as string)
})

onUnmounted(() => {
  stopAutoSave()
  // 仅在非编辑模式下或确定离开时重置
  // articleStore.resetEditArticle() // 建议在进入时重置，而不是退出时，防止误操作丢失
})

// 路由守卫：未保存提醒
onBeforeRouteLeave((to, from, next) => {
  if (articleStore.isDirty) {
    ElMessageBox.confirm('当前内容尚未保存，确定要离开吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
      .then(() => next())
      .catch(() => next(false))
  } else {
    next()
  }
})

/**
 * 图片处理逻辑
 */
const onUploadImg = async (files: File[], callback: (urls: string[]) => void) => {
  const uploadPromises = files.map(async (file) => {
    try {
      const res = await uploadArticleImage(file)
      if (res.code === 200) {
        return formatMinioUrl(res.data)
      }
      throw new Error(res.message || '上传失败')
    } catch (error) {
      ElMessage.error(`图片 ${file.name} 上传失败`)
      return null
    }
  })

  const results = await Promise.all(uploadPromises)
  const validUrls = results.filter((url): url is string => url !== null)
  callback(validUrls)
}

/**
 * 自动保存逻辑
 */
const startAutoSave = () => {
  stopAutoSave()
  // 30秒自动保存一次草稿
  autoSaveTimer.value = setTimeout(async () => {
    if (articleStore.isDirty && article.title && article.content) {
      console.log('Auto saving draft...')
      await handleRealSave(true, true)
    }
  }, 30000)
}

const stopAutoSave = () => {
  if (autoSaveTimer.value) {
    clearTimeout(autoSaveTimer.value)
    autoSaveTimer.value = null
  }
}

/**
 * 保存草稿按钮点击
 */
const handleSaveDraft = () => {
  article.status = 'draft'
  handleRealSave(true)
}

/**
 * 核心保存/发布流程
 * @param isDraft 是否为草稿模式
 * @param isSilent 是否静默保存（不显示成功提示，用于自动保存）
 */
const handleRealSave = async (isDraft = false, isSilent = false) => {
  if (submitting.value) return
  submitting.value = true

  try {
    // 1. 处理封面图 (如果存在待上传的本地文件)
    let finalCover = article.cover
    if (articleStore.localCover) {
      if (!isSilent) ElMessage.info('正在上传封面图...')
      const res = await uploadCoverImage(articleStore.localCover)
      if (res.code === 200) {
        finalCover = formatMinioUrl(res.data)
        article.cover = finalCover
        articleStore.localCover = null // 上传成功后清空本地文件
      }
    }

    // 2. 构造提交数据
    const tagIds = article.tags.filter((t) => typeof t === 'number')
    const submitData = {
      id: article.id,
      title: article.title,
      content: article.content,
      excerpt: article.excerpt,
      cover: finalCover,

      // 草稿文章分类默认为第一个分类
      categoryId: article.categoryId || 1,
      tagIds,
      status: isDraft ? 'draft' : article.status,
    }

    // 3. 提交到后端
    let result
    if (isEdit.value) {
      result = await updateArticle(article.id!, submitData)
    } else {
      result = await createArticle(submitData)
    }

    if (result.code === 200) {
      console.log(result)
      articleStore.isDirty = false
      const newId = result?.data?.id
      if (!newId) throw new Error('创建文章失败：未返回文章ID')

      // 4. 处理保存后的状态切换
      if (!isEdit.value) {
        // 第一次创建成功，更新 ID 并切换路由
        article.id = newId
        router.replace({ name: 'ArticleEditor', params: { id: newId } })
      }

      if (!isSilent) {
        if (isDraft) {
          ElMessage.success('草稿已保存')
        } else {
          showPublishDialog.value = false
          router.push({ path: '/publish-success', query: { id: newId } })
        }
      }
    }
  } catch (error) {
    console.error('Save failed:', error)
    if (!isSilent) ElMessage.error('操作失败，请重试')
  } finally {
    submitting.value = false
    if (isDraft) startAutoSave() // 保存完后继续开启自动保存
  }
}
</script>

<style scoped lang="scss">
@use '../../styles/variables.scss' as *;

.article-editor {
  padding: $spacing-md;
  background-color: $bg-color;
  min-height: calc(100vh - 64px);
}

.editor-card {
  border-radius: $border-radius-large;
  border: 1px solid $border-color;
  box-shadow: $box-shadow-light;

  .editor-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: $spacing-xl;

    .title-input {
      flex: 1;
      :deep(.el-input__wrapper) {
        box-shadow: none !important;
        padding: 0;
        background: transparent;
      }
      :deep(.el-input__inner) {
        font-size: 24px;
        font-weight: 700;
        height: 50px;
        color: $text-primary;
        &::placeholder {
          color: $text-placeholder;
        }
      }
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: $spacing-md;
      flex-shrink: 0;

      .save-status {
        font-size: 13px;
        color: $text-placeholder;
        display: flex;
        align-items: center;
        gap: 4px;

        &.success {
          color: $success-color;
        }

        .el-icon {
          font-size: 14px;
        }
      }
    }
  }

  .editor-content {
    margin-top: $spacing-sm;
    :deep(.md-editor) {
      border: none;
    }
  }
}

@media (max-width: 768px) {
  .editor-header {
    flex-direction: column;
    align-items: flex-start;
    gap: $spacing-md;

    .title-input {
      width: 100%;
    }

    .header-actions {
      width: 100%;
      justify-content: flex-end;
    }
  }
}
</style>
