<template>
  <template v-for="group in groups" :key="group.label ?? 'main'">
    <p v-if="group.label" class="nav-links__label">{{ group.label }}</p>
    <RouterLink
      v-for="item in group.items"
      :key="item.path"
      :to="item.path"
      class="nav-links__item"
      :class="{ 'is-active': isActive(item.path) }"
      @click="emit('navigate')"
    >
      <el-icon :size="15" aria-hidden="true">
        <component :is="item.icon" />
      </el-icon>
      <span>{{ item.label }}</span>
    </RouterLink>
  </template>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import type { Component } from 'vue'

interface NavItem {
  path: string
  label: string
  icon: Component
}

interface NavGroup {
  label: string | null
  items: NavItem[]
}

defineProps<{ groups: NavGroup[] }>()

const emit = defineEmits<{ navigate: [] }>()

const route = useRoute()

/** 前缀匹配当前路径，命中即高亮（如 /admin/articles 命中「文章」） */
const isActive = (path: string) =>
  route.path === path || route.path.startsWith(`${path}/`)
</script>

<style scoped lang="scss">
.nav-links__label {
  margin: var(--space-md) var(--space-xs) var(--space-2xs);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-ink-4);

  &:first-child {
    margin-top: 0;
  }
}

.nav-links__item {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  height: 2.25rem;
  padding-inline: var(--space-xs);
  margin-bottom: 1px;
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  border-radius: var(--radius-sm);
  transition:
    color var(--dur-micro) var(--ease-out),
    background-color var(--dur-micro) var(--ease-out);

  .el-icon {
    color: var(--color-ink-3);
    transition: color var(--dur-micro) var(--ease-out);
  }

  &:hover {
    color: var(--color-ink);
    background-color: var(--color-paper-3);
  }

  &.is-active {
    font-weight: 500;
    color: var(--color-accent-ink);
    background-color: var(--color-accent-wash);

    .el-icon {
      color: var(--color-accent-ink);
    }
  }
}
</style>
