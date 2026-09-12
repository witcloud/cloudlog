<template>
  <div class="toc-sidebar">
    <el-card class="toc-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>文章目录</span>
        </div>
      </template>
      <div class="toc-content">
        <div 
          v-for="item in tocItems" 
          :key="item.id" 
          :class="['toc-item', { active: activeId === item.id, sub: item.level > 1 }]"
          @click="scrollTo(item.id)"
        >
          {{ item.text }}
        </div>
        <el-empty v-if="tocItems.length === 0" :image-size="40" description="暂无目录" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface TocItem {
  id: string
  text: string
  level: number
}

// In a real application, you would extract headings from the markdown content
// For now, we'll use some placeholder items or allow passing them in
defineProps<{
  tocItems: TocItem[]
}>()

const activeId = ref('')

const scrollTo = (id: string) => {
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth' })
    activeId.value = id
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.toc-sidebar {
  position: sticky;
  top: 84px;

  .toc-card {
    border-radius: $border-radius-large;
    border: 1px solid $border-color;

    .toc-content {
      max-height: calc(100vh - 200px);
      overflow-y: auto;

      .toc-item {
        font-size: 14px;
        padding: 8px 0;
        color: $text-regular;
        cursor: pointer;
        transition: all 0.3s;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;

        &:hover {
          color: $primary-color;
          padding-left: 4px;
        }

        &.active {
          color: $primary-color;
          font-weight: 700;
          border-left: 2px solid $primary-color;
          padding-left: 8px;
        }

        &.sub {
          padding-left: 16px;
          font-size: 13px;
          color: $text-secondary;
          &:hover {
            padding-left: 20px;
          }
        }
      }
    }
  }
}
</style>
