<template>
  <div class="profile-view">
    <el-row :gutter="24">
      <el-col :xs="24" :md="8">
        <el-card class="profile-card" shadow="never">
          <div class="user-info">
            <AvatarUpload
              :model-value="formatMinioUrl(user.avatar)"
              @update:model-value="(val) => (user.avatar = val)"
              :size="100"
              @success="handleAvatarSuccess"
            />
            <h2 class="username">{{ user.username }}</h2>
            <p class="email">{{ user.email }}</p>
            <p class="bio">{{ user.bio || '这个博主很懒，什么都没写~' }}</p>
            <el-button type="primary" @click="editProfile">编辑资料</el-button>
          </div>
          <div class="user-stats">
            <div class="stat-item">
              <span class="value">42</span>
              <span class="label">文章</span>
            </div>
            <div class="stat-item">
              <span class="value">1.2k</span>
              <span class="label">获赞</span>
            </div>
            <div class="stat-item">
              <span class="value">560</span>
              <span class="label">粉丝</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="16">
        <el-tabs v-model="activeTab" class="profile-tabs">
          <el-tab-pane label="我的文章" name="articles">
            <div class="article-list">
              <div v-for="article in myArticles" :key="article.id" class="my-article-item">
                <div class="article-main">
                  <h3 class="article-title" @click="$router.push(`/article/${article.id}`)">
                    {{ article.title }}
                  </h3>
                  <div class="article-meta">
                    <span>{{ article.date }}</span>
                    <el-divider direction="vertical" />
                    <span>{{ article.category }}</span>
                    <el-divider direction="vertical" />
                    <span>{{ article.views }} 阅读</span>
                  </div>
                </div>
                <div class="article-actions">
                  <el-button size="small" link @click="$router.push(`/editor/${article.id}`)">
                    <el-icon><Edit /></el-icon> 编辑
                  </el-button>
                  <el-button size="small" link type="danger" @click="handleDelete(article.id)">
                    <el-icon><Delete /></el-icon> 删除
                  </el-button>
                </div>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="收藏的文章" name="favorites">
            <div class="empty-state">
              <el-empty description="暂无收藏文章" />
            </div>
          </el-tab-pane>
          <el-tab-pane label="账号设置" name="settings">
            <el-form :model="settingsForm" label-position="top" class="settings-form">
              <el-form-item label="用户名">
                <el-input v-model="settingsForm.username" />
              </el-form-item>
              <el-form-item label="个人简介">
                <el-input v-model="settingsForm.bio" type="textarea" :rows="4" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="settingsForm.email" disabled />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveSettings">保存更改</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-col>
    </el-row>

    <!-- Edit Profile Dialog -->
    <el-dialog
      v-model="editVisible"
      title="编辑资料"
      width="500px"
      custom-class="edit-profile-dialog"
    >
      <div class="edit-profile-content">
        <!-- 顶部/侧边头像区 -->
        <div class="avatar-edit-section">
          <AvatarUpload
            :model-value="formatMinioUrl(editForm.avatar)"
            @update:model-value="(val) => (editForm.avatar = val)"
            :size="100"
            show-btn
            @success="handleAvatarSuccess"
          />
        </div>

        <!-- 下方表单区 -->
        <el-form
          :model="editForm"
          :rules="rules"
          ref="formRef"
          label-position="top"
          class="edit-form"
        >
          <el-form-item label="用户名" prop="username">
            <el-input v-model="editForm.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="个人简介" prop="bio">
            <el-input
              v-model="editForm.bio"
              type="textarea"
              :rows="3"
              placeholder="介绍一下你自己吧"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveProfile">保存修改</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { formatMinioUrl } from '@/config'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import AvatarUpload from '@/components/AvatarUpload.vue'
import {
  getUserProfile,
  updateUserProfile,
  type UserProfile,
  type EditUserProfile,
} from '@/api/user'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const user = reactive<UserProfile>({
  username: '',
  email: '',
  avatar: '',
  bio: '',
  articlesCount: 0,
  likesCount: 0,
  followersCount: 0,
})

const activeTab = ref('articles')
const editVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

// 表单校验规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在 2 到 20 个字符', trigger: 'blur' },
  ],
  bio: [{ max: 100, message: '长度不能超过 100 个字符', trigger: 'blur' }],
}

const myArticles = ref([
  {
    id: 1,
    title: '如何使用 Vue 3 + TypeScript 构建企业级博客',
    date: '2026-04-20',
    category: 'Vue',
    views: 1254,
  },
  {
    id: 2,
    title: '深度解析 Vite 的插件机制',
    date: '2026-04-18',
    category: '前端工具',
    views: 856,
  },
  {
    id: 3,
    title: '2026 年前端开发趋势展望',
    date: '2026-04-15',
    category: '行业趋势',
    views: 2341,
  },
])

const editForm = reactive<EditUserProfile>({
  username: '',
  avatar: '',
  bio: '',
})
const settingsForm = reactive({ ...user })

// 初始化加载数据
const fetchProfile = async () => {
  try {
    const res = await getUserProfile()
    if (res.code === 200 && res.data) {
      Object.assign(user, res.data)
      Object.assign(settingsForm, res.data)
    }
  } catch (error) {
    console.error('Failed to fetch profile:', error)
  }
}

onMounted(() => {
  fetchProfile()
})

const editProfile = () => {
  Object.assign(editForm, {
    username: user.username,
    avatar: user.avatar,
    bio: user.bio,
  })
  editVisible.value = true
}

const saveProfile = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const res = await updateUserProfile({
          username: editForm.username,
          bio: editForm.bio,
        })
        if (res.code === 200) {
          ElMessage.success('个人资料已更新')
          user.username = editForm.username
          user.bio = editForm.bio

          if (userStore.userInfo) {
            userStore.userInfo.username = editForm.username
            userStore.userInfo.avatar = editForm.avatar

            localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
          }

          editVisible.value = false
        }
      } catch (error) {
        console.error('Failed to update profile:', error)
      } finally {
        saving.value = false
      }
    }
  })
}

const saveSettings = () => {
  Object.assign(user, settingsForm)
  ElMessage.success('设置已保存')
}

const handleAvatarSuccess = (url: string) => {
  user.avatar = url
  editForm.avatar = url

  if (userStore.userInfo) {
    userStore.userInfo.avatar = url
    localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
  }
}

const handleDelete = (id: number) => {
  ElMessageBox.confirm('确定要删除这篇文章吗？', '警告', {
    type: 'warning',
  }).then(() => {
    myArticles.value = myArticles.value.filter((a) => a.id !== id)
    ElMessage.success('文章已删除')
  })
}
</script>

<style scoped lang="scss">
@use '../../styles/variables.scss' as *;

.profile-view {
  padding-bottom: $spacing-xl;
}

.profile-card {
  border-radius: $border-radius-large;
  border: 1px solid $border-color;
  text-align: center;
  padding: $spacing-lg 0;

  .user-info {
    margin-bottom: $spacing-xl;
    .username {
      margin: $spacing-md 0 $spacing-xs;
      font-size: 24px;
      font-weight: 700;
    }
    .email {
      color: $text-secondary;
      font-size: 14px;
      margin-bottom: $spacing-md;
    }
    .bio {
      color: $text-regular;
      font-size: 14px;
      margin-bottom: $spacing-lg;
      padding: 0 $spacing-lg;
    }
  }

  .user-stats {
    display: flex;
    justify-content: space-around;
    padding-top: $spacing-xl;
    border-top: 1px solid rgba($border-color, 0.5);

    .stat-item {
      display: flex;
      flex-direction: column;
      .value {
        font-weight: 700;
        font-size: 18px;
      }
      .label {
        font-size: 12px;
        color: $text-secondary;
      }
    }
  }
}

.profile-tabs {
  background-color: $white;
  border-radius: $border-radius-large;
  border: 1px solid $border-color;
  padding: $spacing-lg;
  min-height: 500px;
}

.my-article-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-md 0;
  border-bottom: 1px solid rgba($border-color, 0.5);

  &:last-child {
    border-bottom: none;
  }

  .article-main {
    .article-title {
      margin: 0 0 $spacing-xs;
      font-size: 18px;
      cursor: pointer;
      transition: color 0.3s;
      &:hover {
        color: $primary-color;
      }
    }
    .article-meta {
      font-size: 13px;
      color: $text-secondary;
    }
  }

  .article-actions {
    display: flex;
    gap: $spacing-md;
  }
}

.settings-form {
  max-width: 500px;
  margin-top: $spacing-md;
}

.edit-profile-content {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;

  .avatar-edit-section {
    padding: $spacing-md 0;
    border-bottom: 1px solid rgba($border-color, 0.5);
  }

  .edit-form {
    padding: 0 $spacing-sm;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-md;
}
</style>
