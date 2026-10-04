import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElLoading } from 'element-plus'
// 全量样式表而不是按需注入：组件 CSS 若跟着路由 chunk 走，会排到下面这些全局样式之后，
// 而 src/assets/styles 里有 24 处未 scoped 的 .el-* 覆盖，等特异性下顺序一变就翻盘（见 vite.config.ts）
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import 'nprogress/nprogress.css'
// 字重只加载 400 与 700，砍掉 500：实测单篇中文文章首访的 woff2 里 400 档 256 kB、700 档 157 kB、500 档 52 kB，
// 三档的 @font-face 声明各是 102.8 kB 的 CSS 文本。为什么不砍 700——浏览器不会为缺失的字重跳到字体栈里的
// 下一个 family，而是在同一 family 内取最近的脸再算法合成假粗；实测假粗的墨量密度只有真 700 的 78%
// （700/400 alpha 比：真字重 1.424 vs 合成 1.107），CJK 下表现为糊而不是重。
// 代价：源码里 9 处 font-weight: 500 按匹配规则落到 400（半粗变常规），集中在卡片标题与后台标签这类层级
import '@fontsource/noto-sans-sc/400.css'
import '@fontsource/noto-sans-sc/700.css'
import '@fontsource/jetbrains-mono/400.css'
import '@fontsource/jetbrains-mono/600.css'
import './assets/styles/reset.css'
import './assets/styles/variables.css'
import './assets/styles/global.css'
import './assets/styles/auth.css'

import App from './App.vue'
import router from './router'

// 图标不再全量注册：整包 2000+ 组件只为了四五个用量，徒增包体。
// 用到的图标一律在组件内局部 import（ArticleDetail / AdminLayout / AvatarUpload）

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 组件本身由 unplugin-vue-components 按模板里的实际用量注册（vite.config.ts），
// 语言包改由 App.vue 的 el-config-provider 提供，所以这里不再 app.use(ElementPlus)。
// v-loading 是指令不是组件，解析器不管它，而全仓有 28 处在用（含首页与个人页），必须显式装一次
app.use(ElLoading)

app.mount('#app')
