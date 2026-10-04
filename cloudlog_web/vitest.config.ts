import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * 不与 vite.config.ts 合并：那份配置挂着 vue-devtools 与 vditor 本地 CDN 两枚插件，
 * 前者只在 dev 有意义，后者会往测试进程里塞中间件与 closeBundle 的拷贝动作。
 * 测试要的只是 SFC 编译与 @ 别名。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    // seo.ts 在模块加载时就读 document，@vue/test-utils 的 mount 也要 document，node 环境进不来
    environment: 'happy-dom',
    include: ['src/**/__tests__/*.spec.ts'],
    // 把运行时时区钉成 UTC，与后端 ArticleServiceTest 的 R14 同一条纪律。
    // 本机在 +08，「显式 +08:00 解析」与「按浏览器本地时区裸解析」算出同一个瞬间，
    // 时区漂移在这台机器上是测不出来的——只有离开东八区才看得见
    env: { TZ: 'UTC' },
  },
})
