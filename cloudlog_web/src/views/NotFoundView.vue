<template>
  <div class="notfound">
    <!-- 断成两节的链环：文案已把原因归到「链接失效」，图标只把这个名词画一遍 -->
    <svg class="notfound__mark" viewBox="0 0 24 24" width="64" height="64" fill="none" aria-hidden="true">
      <g transform="rotate(-45 12 12)" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
        <path d="M11 8.5H8.5a3.5 3.5 0 0 0 0 7H11" />
        <path d="M13 8.5h2.5a3.5 3.5 0 0 1 0 7H13" />
      </g>
    </svg>

    <p class="notfound__code">404</p>
    <h1 class="notfound__title">页面不存在</h1>
    <p class="notfound__hint">这个地址下没有内容。链接可能已经失效，或地址里多了一笔。</p>

    <!-- 请求的地址：不写出来，输错的人看不出自己错在哪 -->
    <p class="notfound__path">{{ route.fullPath }}</p>

    <RouterLink to="/" class="notfound__action">回到首页</RouterLink>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<style scoped lang="scss">
.notfound {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  max-width: 30rem;
  margin-inline: auto;
  /* 扣满布局真正占掉的高度：顶栏（含 1px 发丝线）+ 主区上下留白。
     少扣一点就溢出，内容看着显示完了却多出一条滚动条 */
  min-height: calc(100dvh - var(--header-h) - 1px - var(--space-xl) - var(--space-2xl));
  text-align: center;
}

.notfound__mark {
  margin: 0 0 var(--space-lg);
  color: var(--color-ink-4);
}

.notfound__code {
  margin: 0;
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  color: var(--color-ink-4);
}

.notfound__title {
  margin: var(--space-2xs) 0 0;
  font-size: var(--text-lg);
  color: var(--color-ink);
}

.notfound__hint {
  margin: var(--space-xs) 0 0;
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--color-ink-3);
}

/* 地址行：上下两道发丝线夹住，长地址就地折行 */
.notfound__path {
  width: 100%;
  margin: var(--space-xl) 0 0;
  padding-block: var(--space-xs);
  border-block: 1px solid var(--color-rule);
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  overflow-wrap: anywhere;
}

/* 次级入口用发丝线描边，本页没有提交类动作，不给主按钮 */
.notfound__action {
  display: inline-flex;
  align-items: center;
  margin-top: var(--space-lg);
  padding: var(--space-2xs) var(--space-lg);
  font-size: var(--text-sm);
  color: var(--color-ink);
  border: 1px solid var(--color-rule-strong);
  border-radius: var(--radius-sm);
  transition:
    color var(--dur-micro) var(--ease-out),
    border-color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
    border-color: var(--color-accent);
  }

  &:active {
    transform: translateY(1px);
  }
}
</style>
