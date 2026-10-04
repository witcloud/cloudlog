import { fileURLToPath, URL } from 'node:url'
import { createRequire } from 'node:module'
import { existsSync, readFileSync, cpSync, mkdirSync } from 'node:fs'
import { join, extname, normalize, sep } from 'node:path'

import { defineConfig, loadEnv, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

/**
 * Vditor 运行时资产本地化：编辑器内核（lute）、代码高亮（highlight.js）、
 * i18n、图标与内容主题按需从 /vditor 加载，替代默认的 unpkg CDN，
 * 保证离线与国内网络下所见即所得编辑器可用。
 */
const VDITOR_ASSET_DIRS = ['js/lute', 'js/highlight.js', 'js/i18n', 'js/icons', 'css']

function vditorLocalCdn(): Plugin {
  const require = createRequire(import.meta.url)
  // Vditor 以 `${cdn}/dist/<path>` 拼运行时资源 URL，这里直接以包根为服务根
  const vditorRoot = join(require.resolve('vditor/package.json'), '..')
  let outDirRoot = 'dist'

  const mime: Record<string, string> = {
    '.js': 'text/javascript',
    '.mjs': 'text/javascript',
    '.css': 'text/css',
    '.json': 'application/json',
    '.map': 'application/json',
    '.svg': 'image/svg+xml',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
    '.ttf': 'font/ttf',
  }

  return {
    name: 'vditor-local-cdn',
    configResolved(config) {
      outDirRoot = config.build.outDir
    },
    configureServer(server) {
      server.middlewares.use('/vditor', (req, res, next) => {
        const requestPath = decodeURIComponent((req.url ?? '').split('?')[0])
        const relative = normalize(requestPath).replace(/^([/\\])+/, '')
        if (relative.startsWith('..')) {
          next()
          return
        }
        const filePath = join(vditorRoot, relative)
        if (!filePath.startsWith(vditorRoot + sep) || !existsSync(filePath)) {
          next()
          return
        }
        res.setHeader('Content-Type', mime[extname(filePath)] ?? 'application/octet-stream')
        res.end(readFileSync(filePath))
      })
    },
    closeBundle() {
      const outDir = join(process.cwd(), outDirRoot, 'vditor', 'dist')
      mkdirSync(outDir, { recursive: true })
      for (const dir of VDITOR_ASSET_DIRS) {
        const from = join(vditorRoot, 'dist', dir)
        if (existsSync(from)) {
          cpSync(from, join(outDir, dir), { recursive: true })
        }
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // 注：rolldown 内核的 vite 8 已移除 esbuild.drop 选项，生产剥 console 暂不具备条件，保持默认

  return {
    plugins: [
      vue(),
      vueDevTools(),
      vditorLocalCdn(),
      // Element Plus 按需注册模板里用到的组件（全仓 29 个），不再 app.use(ElementPlus) 全量注册。
      // importStyle: false 是刻意的——组件样式仍由 main.ts 里那份 element-plus/dist/index.css 统一提供：
      // 按需注入的组件 CSS 会跟着路由 chunk 排到全局样式之后，而 src/assets/styles 里有 24 处未 scoped 的
      // .el-* 覆盖（.el-table / .el-select__wrapper / .el-input__wrapper.is-focus / .el-dialog / .el-button 等），
      // 等特异性下顺序一变就翻盘。CSS 只占 46.9 kB gzip，JS 占 298.9 kB，先收 JS 这一份
      Components({
        resolvers: [ElementPlusResolver({ importStyle: false })],
        dts: 'src/types/components.d.ts',
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        '/api': {
          target: env.VITE_APP_API_BASE_URL || 'http://localhost:8080',
          changeOrigin: true,
          // rewrite: (path) => path.replace(/^\/api/, '') // 根据后端接口是否包含 /api 决定是否重写
        },
      },
    },
    build: {
      rollupOptions: {
        output: {
          // 入口那两个大文件里绝大部分是第三方代码：拆出来后，改业务代码不再改变 element-plus / vue
          // 那部分产物的内容哈希，配合 nginx 对 /assets/ 的 immutable 缓存，回访只需重下业务代码那一小块。
          // 只拆入口急加载的这两组；markdown 相关（md-editor-v3 / markdown-it / dompurify）本来就只被
          // 详情页与编辑器的懒加载路由引用，不放进这里，免得把按需的东西拽回首屏。
          // 写成函数而不是对象：vite 8 的 rolldown 内核只接受 ManualChunksFunction，对象形式会直接抛
          // TypeError: manualChunks is not a function
          manualChunks(id: string) {
            if (!id.includes('node_modules')) return
            if (/[\\/]node_modules[\\/](element-plus|@element-plus)[\\/]/.test(id)) return 'element-plus'
            if (/[\\/]node_modules[\\/](vue|vue-router|pinia|@vue)[\\/]/.test(id)) return 'vue-vendor'
          },
        },
      },
    },
  }
})
