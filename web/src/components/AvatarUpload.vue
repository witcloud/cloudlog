<template>
  <div class="avatar-upload-container">
    <el-upload
      class="avatar-uploader"
      action="/api/v1/upload/avatar"
      :show-file-list="false"
      :before-upload="beforeAvatarUpload"
      :http-request="handleUpload"
      :disabled="loading"
    >
      <div
        class="avatar-wrapper"
        v-loading="loading"
        element-loading-background="rgba(0, 0, 0, 0.3)"
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
import type { UploadRequestOptions } from 'element-plus'
import { formatMinioUrl } from '@/config'

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
    default: formatMinioUrl('/default-avatar.png'),
  },
  showBtn: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:modelValue', 'success'])
const loading = ref(false)

// 模拟点击触发上传
const triggerUpload = () => {
  const uploadInput = document.querySelector(
    '.avatar-uploader input[type="file"]',
  ) as HTMLInputElement
  if (uploadInput) {
    uploadInput.click()
  }
}

// 上传前的校验
const beforeAvatarUpload = (rawFile: any) => {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
  if (!allowedTypes.includes(rawFile.type)) {
    ElMessage.error('头像只能是 JPG/PNG/GIF/WEBP 格式!')
    return false
  } else if (rawFile.size / 1024 / 1024 > 10) {
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
    const res: any = await request.post('/upload/avatar', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })

    if (res.code === 200) {
      const avatarUrl = formatMinioUrl(res.data)
      emit('update:modelValue', avatarUrl)
      emit('success', avatarUrl)
      ElMessage.success('头像上传成功')
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
@use '@/styles/variables.scss' as *;

.avatar-upload-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: $spacing-sm;
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
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #fff;
  opacity: 0;
  transition: opacity 0.3s;
  font-size: 12px;
  gap: 4px;

  .el-icon {
    font-size: 20px;
  }
}
</style>
