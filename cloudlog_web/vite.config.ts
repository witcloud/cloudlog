import { fileURLToPath, URL } from 'node:url'
import { createRequire } from 'node:module'
import { existsSync, readFileSync, cpSync, mkdirSync } from 'node:fs'
import { join, extname, normalize, sep } from 'node:path'

import { defineConfig, loadEnv, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

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
    plugins: [vue(), vueDevTools(), vditorLocalCdn()],
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
  }
})
