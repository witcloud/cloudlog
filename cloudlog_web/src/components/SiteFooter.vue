<script setup lang="ts">
// Ft2 · 单行页脚：一条发丝线，一行落款，置于内容列底部。填了备案号才多出第二行，未填不渲染。
import { useSiteStore } from '@/stores/site'

const siteStore = useSiteStore()
</script>

<template>
  <footer class="site-footer">
    <p>© {{ new Date().getFullYear() }} {{ siteStore.siteName }} · 版权所有</p>
    <p v-if="siteStore.icpNumber" class="site-footer__beian">
      <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">{{
        siteStore.icpNumber
      }}</a>
    </p>
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

/* 备案号是落款的附注：不另起字档，只与上一行拉开发丝间距 */
.site-footer__beian {
  margin-top: var(--space-2xs);
}

/* 链接不抢主色，否则页脚会出现第三种灰 */
.site-footer__beian a {
  color: inherit;
  text-decoration: none;
}

.site-footer__beian a:hover {
  color: var(--color-ink);
}
</style>
