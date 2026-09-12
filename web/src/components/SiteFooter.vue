<script setup lang="ts">
// Ft2 · 单行页脚：一条发丝线，一行落款，置于内容列底部。
</script>

<template>
  <footer class="site-footer">
    <p>© {{ new Date().getFullYear() }} CloudLog · 由 Vue 3 与 Markdown 驱动</p>
  </footer>
</template>

<style scoped>
.site-footer {
  margin-top: var(--space-xl);
  padding-block: var(--space-lg);
  border-top: 1px solid var(--color-rule);
}

.site-footer p {
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}
</style>
