<template>
  <div class="auth-view">
    <div class="auth-panel">
      <header class="auth-head">
        <h1 class="auth-head__title">{{ isLogin ? '欢迎回来' : '加入我们' }}</h1>
        <p class="auth-head__sub">
          {{ isLogin ? '登录您的 CloudLog 账号' : '创建一个新账号以开始写作' }}
        </p>
      </header>

      <el-form :model="authForm" :rules="rules" ref="authFormRef" label-position="top">
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
            <div class="code-input">
              <el-input v-model="authForm.code" placeholder="请输入 6 位验证码" />
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

        <div v-if="isLogin" class="form-footer">
          <el-checkbox v-model="authForm.remember">记住我</el-checkbox>
          <el-link type="primary" underline="never">忘记密码？</el-link>
        </div>

        <el-button type="primary" class="submit-btn" :loading="loading" @click="handleSubmit">
          {{ isLogin ? '立即登录' : '立即注册' }}
        </el-button>
      </el-form>

      <footer class="auth-foot">
        <span>{{ isLogin ? '还没有账号？' : '已有账号？' }}</span>
        <button class="auth-foot__switch" @click="toggleMode">
          {{ isLogin ? '立即注册' : '立即登录' }}
        </button>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { sendCode, register, login as loginApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const sendCodeLoading = ref(false)
const countdown = ref(0)
const authFormRef = ref<FormInstance>()

const isLogin = computed(() => route.name === 'Login')

const authForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  code: '',
  remember: false,
})

const validatePass2 = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== authForm.password) {
    callback(new Error('两次输入密码不一致!'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' },
  ],
  confirmPassword: [{ validator: validatePass2, trigger: 'blur' }],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码长度为 6 位', trigger: 'blur' },
  ],
}

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
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch {
    // Error handled by interceptor
  } finally {
    sendCodeLoading.value = false
  }
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
          userStore.setToken(res.data.token)
          userStore.setUserInfo(res.data.user)
          ElMessage.success('登录成功')
        } else {
          await register({
            email: authForm.email,
            username: authForm.username,
            password: authForm.password,
            code: authForm.code,
          })
          ElMessage.success('注册成功，请登录')
          router.push('/login')
          return
        }
        router.push('/')
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

<style scoped lang="scss">
.auth-view {
  display: flex;
  justify-content: center;
  padding-block: var(--space-xl);
}

.auth-panel {
  width: 100%;
  max-width: 24rem;
}

.auth-head {
  margin-bottom: var(--space-xl);

  &__title {
    font-size: var(--text-lg);
  }

  &__sub {
    margin-top: var(--space-2xs);
    font-size: var(--text-sm);
    color: var(--color-ink-3);
  }
}

.code-input {
  display: flex;
  gap: var(--space-sm);

  .el-input {
    flex: 1;
  }

  .el-button {
    width: 7.5rem;
    flex-shrink: 0;
  }
}

.form-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-lg);
}

.submit-btn {
  width: 100%;
  height: 2.75rem;
  margin-top: var(--space-sm);
}

.auth-foot {
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-rule);
  text-align: center;
  font-size: var(--text-sm);
  color: var(--color-ink-3);

  &__switch {
    margin-left: var(--space-2xs);
    font-size: var(--text-sm);
    color: var(--color-accent);

    &:hover {
      text-decoration: underline;
      text-underline-offset: 0.25em;
    }
  }
}
</style>
