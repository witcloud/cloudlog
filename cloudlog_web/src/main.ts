import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import locale from 'element-plus/es/locale/lang/zh-cn'
import 'md-editor-v3/lib/style.css'
import 'nprogress/nprogress.css'
import '@fontsource/noto-sans-sc/400.css'
import '@fontsource/noto-sans-sc/500.css'
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
app.use(ElementPlus, { locale })

app.mount('#app')
