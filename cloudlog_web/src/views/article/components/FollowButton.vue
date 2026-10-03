<script setup lang="ts">
defineProps<{
  followed: boolean
  pending: boolean
}>()

const emit = defineEmits<{ toggle: [] }>()
</script>

<template>
  <el-button
    class="follow-button"
    :class="{ 'is-followed': followed }"
    type="primary"
    plain
    :loading="pending"
    @click="emit('toggle')"
    >{{ followed ? '取消关注' : '关注' }}</el-button
  >
</template>

<style scoped>
/* 文案说的是动作不是状态，所以「当前有没有关注」只由洗染底与描边区分。
   三类名：压过 el-button 自带的 primary/plain 配色 */
.follow-button.el-button.is-followed {
  color: var(--color-accent);
  border-color: var(--color-accent);
  background-color: var(--color-accent-wash);
}
</style>
