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
            <el-input
              v-model="resetForm.code"
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
import { ref, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { sendResetCode, resetPassword } from '@/api/auth'
import { useResendCooldown } from '@/composables/useResendCooldown'
import SiteLogo from '@/components/SiteLogo.vue'

const router = useRouter()
const loading = ref(false)
const sendCodeLoading = ref(false)
const resetFormRef = ref<FormInstance>()

const resetForm = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})

/** 重发窗口按邮箱记在 localStorage：刷新一次就把 60 秒清零，只会让人再去撞一次「发送过于频繁」 */
const { countdown, arm: armResend } = useResendCooldown(() => resetForm.email)

/** 新密码一改，确认位那条不一致就得重算：它原先只在自身 blur 时触发 */
watch(
  () => resetForm.newPassword,
  () => {
    if (!resetForm.confirmPassword) return
    resetFormRef.value?.validateField('confirmPassword').catch(() => {})
  },
)

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
    // 六位数字：填进字母只会得到后端那句「验证码错误或已过期」，还白吃一次错误额度
    { pattern: /^\d{6}$/, message: '验证码是 6 位数字', trigger: 'blur' },
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
    // 中性话术：后端对未注册邮箱静默跳过（防账号枚举），响应一律成功，这里不能替后端断言「已发送」
    ElMessage.success('若该邮箱已注册，验证码将发送至您的邮箱')
    armResend()
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
      // 带上刚重置的邮箱：登录页预填的是「上次登录过的账号」，不是这个正要登的
      router.push({ path: '/login', query: { email: resetForm.email } })
    } catch {
      // Error handled by interceptor
    } finally {
      loading.value = false
    }
  })
}
</script>
