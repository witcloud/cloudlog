<template>
  <div class="settings admin-page">
    <header class="admin-page__head">
      <div>
        <h1 class="admin-page__title">系统设置</h1>
        <p class="admin-page__subtitle">影响系统运行的配置与关于页文案，保存后立即生效</p>
      </div>
    </header>

    <div v-loading="loading" class="settings__body">
      <!-- 运行开关：两项都是点控件，标签占左缘、开关贴右缘，行间用发丝线分 -->
      <section class="admin-card settings__group">
        <header class="admin-card__head">
          <h2 class="admin-card__title">运行开关</h2>
        </header>
        <div class="admin-card__body admin-card__body--flush">
          <div class="settings__row">
            <div class="settings__info">
              <h3 class="settings__label">评论审核</h3>
              <p class="settings__desc">
                开启后，新评论先经违禁词机审：命中「转待审」的进入待审队列，命中「直接拒绝」的当场被拒，未命中的直接发布；关闭则两者都跳过。词库在「评论管理」的「违禁词库」页签维护。
              </p>
            </div>
            <el-switch v-model="form.commentModeration" />
          </div>

          <div class="settings__row">
            <div class="settings__info">
              <h3 class="settings__label">开放注册</h3>
              <p class="settings__desc">关闭后，新用户将无法注册账号，已有用户不受影响。</p>
            </div>
            <el-switch v-model="form.registrationEnabled" />
          </div>
        </div>
      </section>

      <!-- 评论防护：与运行开关同为点控件，沿用标签占左缘、控件贴右缘的行结构 -->
      <section class="admin-card settings__group">
        <header class="admin-card__head">
          <h2 class="admin-card__title">评论防护</h2>
        </header>
        <div class="admin-card__body admin-card__body--flush">
          <div class="settings__row">
            <div class="settings__info">
              <h3 class="settings__label">评论字数上限</h3>
              <p class="settings__desc">
                单条评论可输入的字数，0 表示不限制。前台评论框按此限长，服务端同样按此校验。
              </p>
            </div>
            <el-input-number v-model="form.commentMaxLength" :min="0" :max="5000" />
          </div>

          <div class="settings__row">
            <div class="settings__info">
              <h3 class="settings__label">发布间隔</h3>
              <p class="settings__desc">
                同一个人连续发评论（含回复）之间要等的秒数，0 表示不限制。
              </p>
            </div>
            <el-input-number v-model="form.commentIntervalSeconds" :min="0" :max="86400" />
          </div>
        </div>
      </section>

      <!-- 站点信息：两项都是块控件，与关于页文案同族，标签在控件上方 -->
      <section class="admin-card settings__group">
        <header class="admin-card__head">
          <h2 class="admin-card__title">站点信息</h2>
        </header>
        <div class="admin-card__body settings__fields">
          <p class="settings__desc">
            前台标签页标题后缀与页脚落款使用的站点名、页脚展示的备案号，以及爬虫与订阅器拼绝对链接用的站点域名。
          </p>

          <div class="settings__field">
            <label class="settings__label" for="site-name">站点名称</label>
            <el-input id="site-name" v-model="form.siteName" maxlength="30" />
          </div>

          <div class="settings__field">
            <label class="settings__label" for="site-icp">ICP 备案号</label>
            <el-input id="site-icp" v-model="form.icpNumber" maxlength="50" />
            <p class="settings__desc">留空则页脚不展示备案信息。</p>
          </div>

          <div class="settings__field">
            <label class="settings__label" for="site-url">站点域名</label>
            <el-input id="site-url" v-model="form.siteUrl" maxlength="255" placeholder="https://example.com" />
            <p class="settings__desc">
              用于 sitemap、RSS 与 robots 里的绝对地址，含协议、末尾不必带斜杠。留空即按当次请求的来源地址兜底——
              本机自测够用，正式上线要填一次，否则订阅源里的地址会随访问用的 Host 头变。
            </p>
          </div>
        </div>
      </section>

      <!-- 关于页文案：四项都是块控件，标签在控件上方，与上面的点控件分行 -->
      <section class="admin-card settings__group">
        <header class="admin-card__head">
          <h2 class="admin-card__title">关于页文案</h2>
        </header>
        <div class="admin-card__body settings__fields">
          <p class="settings__desc">
            前台「关于」页使用的头像与三段文字，某项留空即恢复它的默认值。
          </p>

          <div class="settings__field">
            <!-- 上传控件的 file input 藏在 el-upload 内部，无法用 for 关联，只做可见标签 -->
            <span class="settings__label">头像</span>
            <div class="settings__avatar">
              <AvatarUpload
                v-model="aboutForm.avatar"
                :size="72"
                upload-path="/upload/site-image"
                success-message="头像已上传，保存更改后生效"
              />
              <div class="settings__avatar-side">
                <span v-if="avatarPending" class="settings__pending">待保存</span>
                <el-button
                  v-if="myAvatar && aboutForm.avatar !== myAvatar"
                  class="settings__use-mine"
                  type="primary"
                  link
                  @click="useMyAvatar"
                >
                  使用我的头像
                </el-button>
              </div>
            </div>
          </div>

          <div class="settings__field">
            <label class="settings__label" for="about-name">作者名</label>
            <el-input id="about-name" v-model="aboutForm.name" maxlength="50" />
          </div>

          <div class="settings__field">
            <label class="settings__label" for="about-role">身份说明</label>
            <el-input id="about-role" v-model="aboutForm.role" maxlength="50" />
          </div>

          <div class="settings__field">
            <label class="settings__label" for="about-lead">导语</label>
            <el-input
              id="about-lead"
              v-model="aboutForm.lead"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
            />
          </div>
        </div>
      </section>

      <div class="settings__actions">
        <el-button type="primary" :disabled="!dirty" :loading="saving" @click="save">
          保存更改
        </el-button>
        <span v-if="dirty" class="settings__dirty-hint">有未保存的更改</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAboutContent,
  getSystemSettings,
  updateAboutContent,
  updateSystemSettings,
} from '@/api/admin'
import type { SystemSettings } from '@/api/types/admin'
import type { AboutContent } from '@/api/types/site'
import AvatarUpload from '@/components/AvatarUpload.vue'
import { useUserStore } from '@/stores/user'
import { formatMinioUrl } from '@/config'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const saved = ref<SystemSettings | null>(null)
const savedAbout = ref<AboutContent | null>(null)

const form = reactive<SystemSettings>({
  commentModeration: true,
  registrationEnabled: true,
  siteName: 'CloudLog',
  icpNumber: '',
  siteUrl: '',
  commentMaxLength: 500,
  commentIntervalSeconds: 60,
})

const aboutForm = reactive<AboutContent>({
  name: '',
  role: '',
  lead: '',
  avatar: '',
})

/* 拷一次而不是引用：站点头像此后独立，管理员再换头像不会静默改掉公开页 */
const myAvatar = computed(() => formatMinioUrl(userStore.userInfo?.avatar))

const useMyAvatar = () => {
  aboutForm.avatar = myAvatar.value
}

/* 预览换图不等于生效，就地标出待存的是哪一项 */
const avatarPending = computed(
  () => savedAbout.value !== null && aboutForm.avatar !== savedAbout.value.avatar,
)

/* 六个字段逐条比迟早会漏掉新加的，整份浅比一次；关于页文案另有 savedAbout，单独比 */
const dirty = computed(() => {
  if (saved.value === null || savedAbout.value === null) return false
  const settingsChanged = (Object.keys(form) as (keyof SystemSettings)[]).some(
    (key) => form[key] !== saved.value?.[key],
  )
  const aboutChanged =
    aboutForm.name !== savedAbout.value.name ||
    aboutForm.role !== savedAbout.value.role ||
    aboutForm.lead !== savedAbout.value.lead ||
    avatarPending.value
  return settingsChanged || aboutChanged
})

const fetchSettings = async () => {
  loading.value = true
  try {
    const { data } = await getSystemSettings()
    saved.value = data
    Object.assign(form, data)
  } catch (error) {
    console.error('获取系统设置失败:', error) // 提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

const fetchAboutContent = async () => {
  try {
    const { data } = await getAboutContent()
    savedAbout.value = data
    Object.assign(aboutForm, data)
  } catch (error) {
    console.error('获取关于页文案失败:', error)
  }
}

const save = async () => {
  saving.value = true
  try {
    await Promise.all([updateSystemSettings({ ...form }), updateAboutContent({ ...aboutForm })])
    ElMessage.success('设置已保存')
    fetchSettings()
    fetchAboutContent()
  } catch (error) {
    console.error('保存系统设置失败:', error)
  } finally {
    saving.value = false
  }
}

/* 上传的头像只是表单里的待存值，离开前拦一道，避免「传了却没保存」的静默丢失 */
onBeforeRouteLeave((to, from, next) => {
  if (!dirty.value) {
    next()
    return
  }
  ElMessageBox.confirm('当前修改尚未保存，确定要离开吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => next())
    .catch(() => next(false))
})

onMounted(() => {
  fetchSettings()
  fetchAboutContent()
})
</script>

<style scoped lang="scss">
.settings__body {
  max-width: 40rem;
  min-height: 8rem;
}

.settings__group + .settings__group {
  margin-top: var(--space-md);
}

.settings__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  padding: var(--space-lg);
}

.settings__row + .settings__row {
  border-top: 1px solid var(--color-rule);
}

.settings__info {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xs);
  min-width: 0;
}

.settings__fields {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

/* 字段间距走表单档（md），标签与控件间距走组件内部档（xs） */
.settings__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

/* 头像是点控件，动作贴着盘走，不再向下堆成第三行；结构与后台表格 .cell-user 同源 */
.settings__avatar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.settings__avatar-side {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2xs);
}

/* link 按钮自带左右内边距，去掉后文字才与侧列左缘齐平 */
.settings__use-mine {
  padding: 0;
  font-size: var(--text-sm);
  font-weight: 400;
}

/* 待存态归功能色：§4.1 把「待处理提示、未保存点」划给功能色，accent 不占这个语义位 */
.settings__pending {
  font-size: var(--text-xs);
  color: var(--color-warning);
}

/* 卡片名交给 admin-card__title，这里只标字段名：同档不同色，标签比标题更近墨 */
.settings__label {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-ink);
}

.settings__desc {
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-ink-3);
}

.settings__actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-top: var(--space-lg);
}

.settings__dirty-hint {
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}
</style>
