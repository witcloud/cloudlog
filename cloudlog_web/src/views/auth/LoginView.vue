<template>
  <div class="auth-view">
    <div class="auth-panel">
      <header class="auth-head">
        <RouterLink to="/" class="auth-brand" aria-label="返回首页"><SiteLogo /></RouterLink>
        <h1 class="auth-head__title">{{ isLogin ? '欢迎回来' : '加入我们' }}</h1>
        <p class="auth-head__sub">
          {{ isLogin ? '登录您的 CloudLog 账号' : '创建一个新账号以开始写作' }}
        </p>
      </header>

      <el-form
        :model="authForm"
        :rules="rules"
        ref="authFormRef"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <template v-if="!isLogin">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="authForm.username" placeholder="请输入用户名" />
          </el-form-item>
        </template>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="authForm.email" placeholder="your@email.com" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="authForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>

        <template v-if="!isLogin">
          <el-form-item label="验证码" prop="code">
            <div class="auth-code-input">
              <el-input
                v-model="authForm.code"
                maxlength="6"
                placeholder="请输入 6 位验证码（5 分钟内有效）"
              />
              <el-button
                type="primary"
                plain
                :disabled="countdown > 0"
                @click="handleSendCode"
                :loading="sendCodeLoading"
              >
                {{ countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="authForm.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              show-password
            />
          </el-form-item>
        </template>

        <div v-if="isLogin" class="auth-form-footer">
          <el-checkbox v-model="authForm.remember">记住我</el-checkbox>
          <RouterLink to="/forgot-password" class="auth-link">忘记密码？</RouterLink>
        </div>

        <el-button type="primary" class="auth-submit" native-type="submit" :loading="loading">
          {{ isLogin ? '立即登录' : '立即注册' }}
        </el-button>
      </el-form>

      <footer class="auth-foot">
        <!-- 关闭注册时这句换成说明：留着入口只会让人填完邮箱、收完验证码，才在最后一秒被告知此路不通 -->
        <template v-if="isLogin">
          <span>还没有账号？</span>
          <button v-if="registrationOpen" class="auth-foot__switch" @click="toggleMode">
            立即注册
          </button>
          <span v-else class="auth-foot__closed">本站暂未开放注册</span>
        </template>
        <template v-else>
          <p v-if="!registrationOpen" class="auth-foot__closed">本站暂未开放注册，已有账号请登录。</p>
          <span>已有账号？</span>
          <button class="auth-foot__switch" @click="toggleMode">立即登录</button>
        </template>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { sendCode, register, login as loginApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useSiteStore } from '@/stores/site'
import { useResendCooldown } from '@/composables/useResendCooldown'
import SiteLogo from '@/components/SiteLogo.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const siteStore = useSiteStore()
const loading = ref(false)
const sendCodeLoading = ref(false)
const authFormRef = ref<FormInstance>()

const isLogin = computed(() => route.name === 'Login')

/** 后台关掉注册时这一句决定脚注写「立即注册」还是「暂未开放注册」；拉不到设置按开放解读 */
const registrationOpen = computed(() => siteStore.registrationEnabled)

const authForm = reactive({
  username: '',
  // 刚从找回密码页过来的，优先用它带来的邮箱；否则用上次登录过的邮箱
  // （退出登录不清 lastEmail，登录与注册共用这一份初始值，所以注册页也会带上它）
  email: (typeof route.query.email === 'string' && route.query.email) || userStore.lastEmail,
  password: '',
  confirmPassword: '',
  code: '',
  // 默认勾选 = 沿用此前的行为（凭据一直存 localStorage）；取消勾选才降级为关掉标签页即失效
  remember: true,
})

/** 重发窗口按邮箱记在 localStorage：刷新与切页不再把 60 秒清零（见 useResendCooldown） */
const { countdown, arm: armResend } = useResendCooldown(() => authForm.email)

/** 上方口令一改，确认位那条「两次输入不一致」就得重算：它原先只在自身 blur 时触发，
 *  于是改完新密码，旧的红字还挂着（或者旧的通过状态一直留到提交） */
watch(
  () => authForm.password,
  () => {
    if (isLogin.value || !authForm.confirmPassword) return
    authFormRef.value?.validateField('confirmPassword').catch(() => {})
  },
)

const validatePass2 = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== authForm.password) {
    callback(new Error('两次输入密码不一致!'))
  } else {
    callback()
  }
}

// 登录与注册共用这一个组件与同一份 rules，所以 rules 必须随路由重算：
// 实例在 /login ↔ /register 之间是被复用的，静态对象会停在首次进入的那一档
const rules = computed(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    // 2-30 位与字符白名单都跟后端 RegisterRequest 同档：原来限 3-20 位，
    // 两个字的中文名与三十位以内的长名都会被前端挡下，而后端其实收
    { min: 2, max: 30, message: '用户名长度需在 2-30 位之间', trigger: 'blur' },
    {
      pattern: /^[\u4e00-\u9fa5a-zA-Z0-9_-]+$/,
      message: '用户名仅支持中文、字母、数字、下划线和中划线',
      trigger: 'blur',
    },
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    // 强度只属于写入口令的场景：登录再校长度，会把存量短口令账号挡在表单外提交不了。
    // 8-72 与后端 RegisterRequest 同档，6 位那档会让 6-7 位口令过了前端再被后端打回
    ...(isLogin.value
      ? []
      : [{ min: 8, max: 72, message: '密码长度需在 8-72 位之间', trigger: 'blur' }]),
  ],
  confirmPassword: [{ validator: validatePass2, trigger: 'blur' }],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    // 六位数字：填进字母只会得到后端那句「验证码错误或已过期」，还白吃一次错误额度
    { pattern: /^\d{6}$/, message: '验证码是 6 位数字', trigger: 'blur' },
  ],
}))

const handleSendCode = async () => {
  if (!authForm.email) {
    ElMessage.warning('请先输入邮箱地址')
    return
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(authForm.email)) {
    ElMessage.warning('请输入正确的邮箱格式')
    return
  }

  try {
    sendCodeLoading.value = true
    await sendCode(authForm.email)
    ElMessage.success('验证码已发送，请注意查收')
    armResend()
  } catch {
    // Error handled by interceptor
  } finally {
    sendCodeLoading.value = false
  }
}

/** 登录成功后回跳：redirect 参数只接受站内绝对路径，拒绝 //host 与完整 URL 外跳 */
const goAfterLogin = () => {
  const target = route.query.redirect
  if (typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')) {
    router.push(target)
    return
  }
  router.push('/')
}

const handleSubmit = async () => {
  if (!authFormRef.value) return

  await authFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        if (isLogin.value) {
          const res = await loginApi({
            email: authForm.email,
            password: authForm.password,
          })
          userStore.setAuth({
            token: res.data.token,
            user: res.data.user,
            email: authForm.email,
            remember: authForm.remember,
          })
          ElMessage.success('登录成功')
        } else {
          await register({
            email: authForm.email,
            username: authForm.username,
            password: authForm.password,
            code: authForm.code,
          })
          ElMessage.success('注册成功，请登录')
          // 把 redirect 带过去：注册是被守卫打断的中间步骤，登完该回到原本要去的那一页，
          // 而不是把人扔回首页重新点一遍
          router.push({
            path: '/login',
            query: route.query.redirect ? { redirect: route.query.redirect } : {},
          })
          return
        }
        goAfterLogin()
      } catch {
        // Error handled by interceptor
      } finally {
        loading.value = false
      }
    }
  })
}

const toggleMode = () => {
  if (isLogin.value) {
    router.push('/register')
  } else {
    router.push('/login')
  }
}
</script>

