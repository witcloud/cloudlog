<template>
  <div class="avatar-upload-container" ref="containerRef">
    <el-upload
      class="avatar-uploader"
      :show-file-list="false"
      :before-upload="beforeAvatarUpload"
      :http-request="handleUpload"
      :disabled="loading"
    >
      <div
        class="avatar-wrapper"
        v-loading="loading"
        element-loading-background="var(--color-overlay)"
      >
        <el-avatar :size="size" :src="modelValue || defaultAvatar" class="avatar-image" />
        <div class="avatar-mask">
          <el-icon><Camera /></el-icon>
          <span>修改头像</span>
        </div>
      </div>
    </el-upload>
    <div v-if="showBtn" class="upload-btn-wrapper">
      <el-button type="primary" link @click="triggerUpload" :loading="loading">更换头像</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import request from '@/utils/request'
import type { UploadRawFile, UploadRequestOptions } from 'element-plus'
import type { Result } from '@/api/types/auth'
import { formatMinioUrl, defaultAvatarUrl } from '@/config'
import { UPLOAD_LIMITS } from '@/config/upload'

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  size: {
    type: Number,
    default: 100,
  },
  defaultAvatar: {
    type: String,
    default: defaultAvatarUrl,
  },
  showBtn: {
    type: Boolean,
    default: false,
  },
  /** 上传端点：默认绑当前用户头像，站点级素材传 /upload/site-image 以免改动个人资料 */
  uploadPath: {
    type: String,
    default: '/upload/avatar',
  },
  /** 只转存对象、不即时生效的端点要改这句，避免「成功」被读成「已生效」 */
  successMessage: {
    type: String,
    default: '头像上传成功',
  },
})

const emit = defineEmits(['update:modelValue', 'success'])
const loading = ref(false)
const containerRef = ref<HTMLElement | null>(null)

// 模拟点击触发上传。必须在组件实例内找 input：全局 querySelector 会在
// 页面同时渲染多个实例（个人页 + 编辑弹窗）时点错别人的文件框
const triggerUpload = () => {
  const uploadInput = containerRef.value?.querySelector(
    'input[type="file"]',
  ) as HTMLInputElement | null
  if (uploadInput) {
    uploadInput.click()
  }
}

// 上传前的校验
const beforeAvatarUpload = (rawFile: UploadRawFile) => {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
  if (!allowedTypes.includes(rawFile.type)) {
    ElMessage.error('头像只能是 JPG/PNG/GIF/WEBP 格式!')
    return false
  } else if (rawFile.size > UPLOAD_LIMITS.avatar) {
    ElMessage.error('头像大小不能超过 10MB!')
    return false
  }
  return true
}

// 自定义上传实现
const handleUpload = async (options: UploadRequestOptions) => {
  const { file } = options
  const formData = new FormData()
  formData.append('file', file)
  loading.value = true

  try {
    const res = await request.post<unknown, Result<string>>(props.uploadPath, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      // 全局 10s 对大图弱网上传偏紧，上传请求单独放宽
      timeout: 60000,
    })

    if (res.code === 200) {
      const avatarUrl = formatMinioUrl(res.data)
      emit('update:modelValue', avatarUrl)
      emit('success', avatarUrl)
      ElMessage.success(props.successMessage)
    } else {
      ElMessage.error(res.message || '上传失败')
    }
  } catch (error) {
    console.error('Avatar upload error:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.avatar-upload-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: var(--space-xs);
}

.avatar-uploader {
  :deep(.el-upload) {
    border-radius: 50%;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    transition: var(--el-transition-duration-fast);
  }
}

.avatar-wrapper {
  position: relative;
  width: fit-content;
  height: fit-content;
  border-radius: 50%;
  overflow: hidden;

  &:hover {
    .avatar-mask {
      opacity: 1;
    }
  }
}

.avatar-image {
  display: block;
}

.avatar-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: var(--color-overlay);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: var(--color-paper);
  opacity: 0;
  transition: opacity 0.3s;
  font-size: 12px;
  gap: 4px;

  .el-icon {
    font-size: 20px;
    /* 相机字形自带留白偏上，与下方文字做光学对齐（不占布局，文字位置不动） */
    transform: translateY(2px);
  }
}
</style>
