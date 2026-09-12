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
    router.push(`/articles/${articleId.value}`)
  } else {
    router.push('/')
  }
}

const handleGoHome = () => {
  router.push('/')
}
</script>

<style scoped lang="scss">
.publish-success-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 200px);
  padding: var(--space-md);
}

.success-card {
  width: 100%;
  max-width: 500px;
  border-radius: var(--radius-md);
  text-align: center;
  padding: var(--space-xl) 0;
}

.success-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-lg);

  .success-icon {
    font-size: 72px;
    color: var(--el-color-success);
  }

  .success-title {
    margin: 0;
    font-size: 28px;
    color: var(--color-ink);
  }

  .success-desc {
    margin: 0;
    font-size: 16px;
    color: var(--color-ink-3);
  }

  .action-buttons {
    display: flex;
    gap: var(--space-md);
    margin-top: var(--space-lg);
  }
}

@media (max-width: 480px) {
  .action-buttons {
    flex-direction: column;
    width: 100%;
    padding: 0 var(--space-xl);

    .el-button {
      margin-left: 0 !important;
      width: 100%;
    }
  }
}
</style>
