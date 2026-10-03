<template>
  <div class="settings">
    <!-- 页头 -->
    <header class="settings__head">
      <h1 class="settings__title">账号设置</h1>
      <p class="settings__sub">管理账号与显示偏好。</p>
    </header>

    <!-- 账号安全 -->
    <section class="settings__group" aria-labelledby="settings-security">
      <h2 id="settings-security" class="settings__group-title">账号安全</h2>

      <dl class="settings__rows">
        <div class="settings__row">
          <dt>
            <span class="settings__label">登录邮箱</span>
            <span class="settings__desc">登录凭据，修改后需重新登录。</span>
          </dt>
          <dd class="settings__cell">
            <span class="settings__value">{{ user.email || '—' }}</span>
            <button class="settings__action" @click="openEmailDialog">修改</button>
          </dd>
        </div>
        <div class="settings__row">
          <dt>
            <span class="settings__label">登录密码</span>
            <span class="settings__desc">改完后此前的登录状态全部失效，需用新密码重新登录。</span>
          </dt>
          <dd class="settings__cell">
            <button class="settings__action" @click="openPasswordDialog">修改</button>
          </dd>
        </div>
        <div class="settings__row">
          <dt>
            <span class="settings__label">当前会话</span>
            <span class="settings__desc">退出后需用邮箱重新登录。</span>
          </dt>
          <dd class="settings__cell">
            <button class="settings__action settings__action--danger" @click="handleLogout">
              退出登录
            </button>
          </dd>
        </div>
      </dl>
    </section>

    <!-- 显示 -->
    <section class="settings__group" aria-labelledby="settings-display">
      <h2 id="settings-display" class="settings__group-title">显示</h2>

      <dl class="settings__rows">
        <div class="settings__row">
          <dt>
            <span class="settings__label">外观</span>
            <span class="settings__desc">仅影响当前浏览器。</span>
          </dt>
          <dd class="settings__cell">
            <div class="theme-switch" role="group" aria-label="外观模式">
              <button :class="{ 'is-active': !isDark }" @click="setTheme('light')">亮色</button>
              <button :class="{ 'is-active': isDark }" @click="setTheme('dark')">暗色</button>
            </div>
          </dd>
        </div>
      </dl>
    </section>

    <!-- 修改登录邮箱：新邮箱 + 验证码 + 当前密码三项齐备才可提交，成功后强制重新登录 -->
    <el-dialog
      v-model="emailDialogVisible"
      title="修改登录邮箱"
      width="26rem"
      @closed="resetEmailForm"
    >
      <el-form
        id="email-change-form"
        ref="emailFormRef"
        :model="emailForm"
        :rules="emailRules"
        label-position="top"
        @submit.prevent="handleChangeEmail"
      >
        <el-form-item label="新邮箱" prop="newEmail">
          <el-input v-model="emailForm.newEmail" placeholder="请输入新邮箱" autocomplete="off" />
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <div class="settings__code">
            <el-input v-model="emailForm.code" placeholder="请输入 6 位验证码" />
            <el-button plain :disabled="countdown > 0" :loading="sending" @click="handleSendCode">
              {{ countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="当前密码" prop="password">
          <el-input
            v-model="emailForm.password"
            type="password"
            placeholder="请输入当前密码"
            show-password
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button text @click="emailDialogVisible = false">取消</el-button>
        <!-- 按钮在 footer、不在 form 内，用 form 属性认回表单，回车才会走同一个提交 -->
        <el-button
          type="primary"
          native-type="submit"
          form="email-change-form"
          :disabled="!emailFormReady"
          :loading="changing"
        >
          确认修改
        </el-button>
      </template>
    </el-dialog>

    <!-- 修改登录密码：当前密码 + 新密码 + 确认新密码，成功后此前的登录状态全部作废 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="修改登录密码"
      width="26rem"
      @closed="resetPasswordForm"
    >
      <el-form
        id="password-change-form"
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-position="top"
        @submit.prevent="handleChangePassword"
      >
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="请输入当前密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="请输入 8-72 位新密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button text @click="passwordDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          native-type="submit"
          form="password-change-form"
          :disabled="!passwordFormReady"
          :loading="changingPassword"
        >
          确认修改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  getUserProfile,
  sendEmailChangeCode,
  changeEmail,
  changePassword,
  type UserProfile,
  type EmailChangeRequest,
} from '@/api/user'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const userStore = useUserStore()
const { isDark, setTheme } = useTheme()

const user = reactive<UserProfile>({
  username: '',
  email: '',
  avatar: '',
  bio: '',
  occupation: '',
})

// 初始化加载数据
const fetchProfile = async () => {
  try {
    const res = await getUserProfile()
    if (res.code === 200 && res.data) {
      Object.assign(user, res.data)
    }
  } catch (error) {
    console.error('Failed to fetch profile:', error)
  }
}

onMounted(() => {
  fetchProfile()
})

const handleLogout = () => {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}

/* ---- 修改登录邮箱 ---- */

const emailDialogVisible = ref(false)
const emailFormRef = ref<FormInstance>()
const sending = ref(false)
const changing = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | undefined

const emailForm = reactive<EmailChangeRequest>({
  newEmail: '',
  code: '',
  password: '',
})

const emailRules = {
  newEmail: [
    { required: true, message: '请输入新邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码长度为 6 位', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
}

/** 三项齐备才允许提交 */
const emailFormReady = computed(
  () => !!emailForm.newEmail.trim() && !!emailForm.code.trim() && !!emailForm.password.trim(),
)

const stopCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = undefined
  }
}

const openEmailDialog = () => {
  emailDialogVisible.value = true
}

const resetEmailForm = () => {
  stopCountdown()
  countdown.value = 0
  emailForm.newEmail = ''
  emailForm.code = ''
  emailForm.password = ''
  emailFormRef.value?.clearValidate()
}

const handleSendCode = async () => {
  const email = emailForm.newEmail.trim()
  if (!email) {
    ElMessage.warning('请先输入新邮箱')
    return
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(email)) {
    ElMessage.warning('请输入正确的邮箱格式')
    return
  }

  try {
    sending.value = true
    await sendEmailChangeCode(email)
    ElMessage.success('验证码已发送，请注意查收')
    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        stopCountdown()
      }
    }, 1000)
  } catch {
    console.error('Failed to send email change code')
  } finally {
    sending.value = false
  }
}

const handleChangeEmail = async () => {
  if (!emailFormRef.value || !emailFormReady.value) return

  await emailFormRef.value.validate(async (valid) => {
    if (!valid) return

    const nextEmail = emailForm.newEmail.trim()
    changing.value = true
    try {
      const res = await changeEmail({ newEmail: nextEmail, code: emailForm.code, password: emailForm.password })
      if (res.code === 200) {
        // 邮箱是 JWT 的 subject：改绑后旧 token 下一次请求即失效，会话必须重开
        userStore.logout()
        emailDialogVisible.value = false
        ElMessage.success(`邮箱已改为 ${nextEmail}，请用新邮箱重新登录`)
        router.push({ name: 'Login' })
      }
    } catch (error) {
      console.error('Failed to change email:', error)
    } finally {
      changing.value = false
    }
  })
}

/* ---- 修改登录密码 ---- */

const passwordDialogVisible = ref(false)
const passwordFormRef = ref<FormInstance>()
const changingPassword = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  // 与后端 PasswordChangeRequest 同档：8 是口令强度下限，72 是 BCrypt 的截断边界
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 72, message: '密码长度需在 8-72 位之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) =>
        value === passwordForm.newPassword ? callback() : callback(new Error('两次输入密码不一致!')),
      trigger: 'blur',
    },
  ],
}

/** 三项齐备才允许提交 */
const passwordFormReady = computed(
  () =>
    !!passwordForm.oldPassword.trim() &&
    !!passwordForm.newPassword.trim() &&
    !!passwordForm.confirmPassword.trim(),
)

const openPasswordDialog = () => {
  passwordDialogVisible.value = true
}

const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value || !passwordFormReady.value) return

  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return

    changingPassword.value = true
    try {
      await changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword,
      })
      // 口令不是 JWT 的 subject，旧 token 原本仍能验过签名；服务端已把该账号的失效水位推到此刻，
      // 手里这枚从此只会换 401，所以会话必须在这里就重开，而不是等下一次请求被打回
      userStore.logout()
      passwordDialogVisible.value = false
      ElMessage.success('密码已修改，请用新密码重新登录')
      router.push({ name: 'Login' })
    } catch (error) {
      console.error('Failed to change password:', error)
    } finally {
      changingPassword.value = false
    }
  })
}

onUnmounted(stopCountdown)
</script>

<style scoped lang="scss">
.settings {
  max-width: 40rem;
  padding-bottom: var(--space-xl);
}

/* ---- 页头 ---- */
.settings__head {
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--color-rule);
}

.settings__title {
  margin: 0;
  font-size: var(--text-lg);
  color: var(--color-ink);
}

.settings__sub {
  margin: var(--space-2xs) 0 0;
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

/* ---- 分组：小标题在上，抬升面板在下 ---- */
.settings__group {
  margin-top: var(--space-lg);

  & + .settings__group {
    margin-top: var(--space-xl);
  }
}

.settings__group-title {
  margin: 0 0 var(--space-sm);
  font-size: var(--text-sm);
  color: var(--color-ink-2);
}

/* ---- 设置盒：卡片范式（docs/ui-design.md:310）；行自带左右内衬，盒不留 padding ---- */
.settings__rows {
  margin: 0;
  background-color: var(--color-paper-2);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
}

/* ---- 行：左侧「设置名 + 后果说明」，右侧控件齐右 ---- */
.settings__row {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  padding: var(--space-md) var(--space-lg);

  /* 缩进分隔线：从内容左沿起，不顶到盒边 */
  & + .settings__row::before {
    content: '';
    position: absolute;
    inset-inline: var(--space-lg);
    inset-block-start: 0;
    height: 1px;
    background-color: var(--color-rule);
  }

  dt {
    display: flex;
    flex-direction: column;
    gap: var(--space-2xs);
    min-width: 0;
  }
}

.settings__label {
  font-size: var(--text-sm);
  color: var(--color-ink);
}

.settings__desc {
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.settings__cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-md);
  margin-inline-start: auto;
  min-width: 0;
}

.settings__value {
  min-width: 0;
  font-size: var(--text-sm);
  color: var(--color-ink);
  overflow-wrap: anywhere;
}

/* ---- 次级入口：发丝线描边（§6.2） ---- */
.settings__action {
  flex-shrink: 0;
  padding: var(--space-2xs) var(--space-md);
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  background-color: transparent;
  border: 1px solid var(--color-rule-strong);
  border-radius: var(--radius-sm);
  transition:
    color var(--dur-micro) var(--ease-out),
    border-color var(--dur-micro) var(--ease-out);

  &:hover {
    color: var(--color-accent);
    border-color: var(--color-accent);
  }

  &--danger:hover {
    color: var(--color-danger);
    border-color: var(--color-danger);
  }
}

.settings__code {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
}

/* ---- 外观切换：底面让给盒子的 paper-2，选中项转凹陷面 ---- */
.theme-switch {
  display: inline-flex;
  border: 1px solid var(--color-rule-strong);
  border-radius: var(--radius-sm);
  overflow: hidden;

  button {
    padding: var(--space-2xs) var(--space-md);
    font-size: var(--text-sm);
    color: var(--color-ink-3);
    background-color: transparent;
    transition:
      color var(--dur-micro) var(--ease-out),
      background-color var(--dur-micro) var(--ease-out);

    & + button {
      border-left: 1px solid var(--color-rule);
    }

    &:hover {
      color: var(--color-ink);
    }

    &.is-active {
      color: var(--color-ink);
      background-color: var(--color-paper-3);
    }
  }
}

@media (max-width: 40rem) {
  .settings__row {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-sm);
  }

  .settings__cell {
    margin-inline-start: 0;
    justify-content: flex-start;
  }
}
</style>
