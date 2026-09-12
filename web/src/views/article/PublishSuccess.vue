<template>
  <div class="publish-success-container">
    <el-card class="success-card">
      <div class="success-content">
        <el-icon class="success-icon"><CircleCheckFilled /></el-icon>
        <h2 class="success-title">发布成功</h2>
        <p class="success-desc">您的文章已成功发布，快去看看吧！</p>

        <div class="action-buttons">
          <el-button @click="handleBackEdit">返回编辑</el-button>
          <el-button type="primary" @click="handleViewArticle">查看文章</el-button>
          <el-button @click="handleGoHome">去首页</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CircleCheckFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const articleId = computed(() => route.query.id as string)

console.log(articleId.value)

const handleBackEdit = () => {
  if (articleId.value) {
    router.push(`/editor/${articleId.value}`)
  } else {
    router.push('/editor')
  }
}

const handleViewArticle = () => {
  if (articleId.value) {
    router.push(`/article/${articleId.value}`)
  } else {
    router.push('/')
  }
}

const handleGoHome = () => {
  router.push('/')
}
</script>

<style scoped lang="scss">
@use '../../styles/variables.scss' as *;

.publish-success-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 200px);
  padding: $spacing-md;
}

.success-card {
  width: 100%;
  max-width: 500px;
  border-radius: $border-radius-large;
  text-align: center;
  padding: $spacing-xl 0;
}

.success-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-lg;

  .success-icon {
    font-size: 72px;
    color: $success-color;
  }

  .success-title {
    margin: 0;
    font-size: 28px;
    color: $text-primary;
  }

  .success-desc {
    margin: 0;
    font-size: 16px;
    color: $text-secondary;
  }

  .action-buttons {
    display: flex;
    gap: $spacing-md;
    margin-top: $spacing-lg;
  }
}

@media (max-width: 480px) {
  .action-buttons {
    flex-direction: column;
    width: 100%;
    padding: 0 $spacing-xl;

    .el-button {
      margin-left: 0 !important;
      width: 100%;
    }
  }
}
</style>
