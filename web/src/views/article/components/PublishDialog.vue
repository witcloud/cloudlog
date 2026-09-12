<template>
  <el-dialog
    v-model="visible"
    title="发布文章"
    width="600px"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <el-form :model="form" label-width="100px" class="publish-form">
      <!-- 文章分类 -->
      <el-form-item label="文章分类" required>
        <el-select v-model="form.categoryId" placeholder="请选择分类" class="full-width">
          <el-option
            v-for="item in categories"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <!-- 文章标签 -->
      <el-form-item label="文章标签">
        <el-select
          v-model="form.tags"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder="请选择或输入标签"
          class="full-width"
        >
          <el-option v-for="item in tags" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>

      <!-- 文章封面 -->
      <el-form-item label="文章封面">
        <div class="cover-uploader" @click="triggerUpload">
          <template v-if="coverUrl">
            <img :src="coverUrl" class="cover-image" />
            <div class="cover-mask">
              <el-icon><Edit /></el-icon>
              <span>更换封面</span>
            </div>
          </template>
          <div v-else class="uploader-placeholder">
            <el-icon><Plus /></el-icon>
            <span>上传封面</span>
          </div>
          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            class="hidden-input"
            @change="handleFileChange"
          />
        </div>
        <div class="cover-tips">建议尺寸 800x450px，支持 jpg, png, webp</div>
      </el-form-item>

      <!-- 文章摘要 -->
      <el-form-item label="文章摘要">
        <el-input
          v-model="form.excerpt"
          type="textarea"
          :rows="4"
          placeholder="请输入文章摘要，若不填写将自动从正文中提取..."
          maxlength="200"
          show-word-limit
        />
        <div class="excerpt-actions">
          <el-button type="primary" link size="small" @click="handleAutoGenerate">
            <el-icon><MagicStick /></el-icon> 自动生成
          </el-button>
        </div>
      </el-form-item>

      <!-- 发布类型 -->
      <el-form-item label="发布类型">
        <el-radio-group v-model="form.status">
          <el-radio label="published">立即发布</el-radio>
          <el-radio label="draft">保存草稿</el-radio>
          <el-radio label="scheduled">定时发布</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 定时发布时间 -->
      <el-form-item v-if="form.status === 'scheduled'" label="发布时间" required>
        <el-date-picker
          v-model="form.publishTime"
          type="datetime"
          placeholder="选择发布时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          :disabled-date="disabledDate"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleConfirm">
          {{ form.status === 'draft' ? '保存草稿' : '确定发布' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Plus, Edit, MagicStick } from '@element-plus/icons-vue'
import { getCategories, getTags } from '@/api/article'
import type { Category, Tag } from '@/api/types/article'
import { useArticleStore } from '@/stores/article'
import { ElMessage } from 'element-plus'
import { formatMinioUrl } from '@/config'

const props = defineProps<{
  modelValue: boolean
  loading?: boolean
}>()

const emit = defineEmits(['update:modelValue', 'confirm'])

const articleStore = useArticleStore()
const form = articleStore.editArticle

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

// 封面预览地址
const coverUrl = computed(() => {
  if (articleStore.localCover) {
    return URL.createObjectURL(articleStore.localCover)
  }
  return formatMinioUrl(form.cover)
})

onMounted(async () => {
  try {
    const [catRes, tagRes] = await Promise.all([getCategories(), getTags()])
    categories.value = catRes.data
    tags.value = tagRes.data
  } catch (error) {
    console.error('Failed to fetch categories/tags:', error)
  }
})

const triggerUpload = () => {
  fileInput.value?.click()
}

const handleFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    if (file.size > 2 * 1024 * 1024) {
      ElMessage.warning('封面图不能超过 2MB')
      return
    }
    articleStore.setLocalCover(file)
  }
}

const handleAutoGenerate = () => {
  // 模拟 AI 自动生成摘要
  if (!form.content) {
    ElMessage.warning('请先输入文章内容')
    return
  }
  // 简单提取前 150 个字符作为演示
  const pureText = form.content.replace(/[#*`>]/g, '').substring(0, 150)
  form.excerpt = pureText + '...'
  ElMessage.success('摘要已自动生成')
}

const disabledDate = (time: Date) => {
  return time.getTime() < Date.now()
}

const handleConfirm = () => {
  if (!form.categoryId) {
    ElMessage.warning('请选择文章分类')
    return
  }
  if (form.status === 'scheduled' && !form.publishTime) {
    ElMessage.warning('请选择定时发布时间')
    return
  }
  emit('confirm')
}
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.publish-form {
  padding: 0 $spacing-md;
}

.full-width {
  width: 100%;
}

.cover-uploader {
  width: 240px;
  height: 135px;
  border: 1px dashed $border-color;
  border-radius: $border-radius-base;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-color: $bg-color;
  transition: border-color 0.3s;

  &:hover {
    border-color: $primary-color;

    .cover-mask {
      opacity: 1;
    }
  }

  .cover-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .cover-mask {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.4);
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    color: #fff;
    opacity: 0;
    transition: opacity 0.3s;
    gap: $spacing-xs;
  }

  .uploader-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    color: $text-secondary;
    gap: $spacing-xs;

    .el-icon {
      font-size: 24px;
    }
  }
}

.cover-tips {
  font-size: 12px;
  color: $text-placeholder;
  margin-top: $spacing-xs;
}

.hidden-input {
  display: none;
}

.excerpt-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: $spacing-xs;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-md;
}
</style>
