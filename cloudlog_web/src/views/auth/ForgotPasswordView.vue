<template>
  <div class="auth-view">
    <div class="auth-panel">
      <header class="auth-head">
        <RouterLink to="/" class="auth-brand" aria-label="返回首页"><SiteLogo /></RouterLink>
        <h1 class="auth-head__title">找回密码</h1>
        <p class="auth-head__sub">向注册邮箱发送验证码，验证通过后设置新密码</p>
      </header>

      <el-form
        :model="resetForm"
        :rules="rules"
        ref="resetFormRef"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="resetForm.email" placeholder="your@email.com" />
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <div class="auth-code-input">
            <el-input v-model="resetForm.code" placeholder="请输入 6 位验证码" />
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

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="resetForm.newPassword"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="resetForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>

        <el-button type="primary" class="auth-submit" native-type="submit" :loading="loading">
          重置密码
        </el-button>
      </el-form>

      <footer class="auth-foot">
        <span>记起密码了？</span>
        <RouterLink to="/login" class="auth-foot__switch">返回登录</RouterLink>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { sendResetCode, resetPassword } from '@/api/auth'
import SiteLogo from '@/components/SiteLogo.vue'

const router = useRouter()
const loading = ref(false)
const sendCodeLoading = ref(false)
const countdown = ref(0)
const resetFormRef = ref<FormInstance>()

const resetForm = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})

const validatePass2 = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value === '') {
    callback(new Error('请再次输入新密码'))
  } else if (value !== resetForm.newPassword) {
    callback(new Error('两次输入密码不一致!'))
  } else {
    callback()
  }
}

const rules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码长度为 6 位', trigger: 'blur' },
  ],
  // 与后端 PasswordResetRequest 同档：8 是强度下限，72 是 BCrypt 的截断边界
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 72, message: '密码长度需在 8-72 位之间', trigger: 'blur' },
  ],
  confirmPassword: [{ validator: validatePass2, trigger: 'blur' }],
}

const handleSendCode = async () => {
  if (!resetForm.email) {
    ElMessage.warning('请先输入邮箱地址')
    return
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(resetForm.email)) {
    ElMessage.warning('请输入正确的邮箱格式')
    return
  }

  try {
    sendCodeLoading.value = true
    await sendResetCode(resetForm.email)
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
  if (!resetFormRef.value) return

  await resetFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      await resetPassword({
        email: resetForm.email,
        code: resetForm.code,
        newPassword: resetForm.newPassword,
      })
      ElMessage.success('密码已重置，请用新密码登录')
      router.push('/login')
    } catch {
      // Error handled by interceptor
    } finally {
      loading.value = false
    }
  })
}
</script>
