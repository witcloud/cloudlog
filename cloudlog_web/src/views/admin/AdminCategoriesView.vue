<template>
  <div class="categories admin-page">
    <header class="admin-page__head">
      <div>
        <h1 class="admin-page__title">分类管理</h1>
        <p class="admin-page__subtitle">分类由管理员维护，用户写作时可直接选用</p>
      </div>
      <div class="admin-page__head-actions">
        <el-button type="primary" @click="openCreate">新增分类</el-button>
      </div>
    </header>

    <div class="admin-card">
      <div class="admin-card__body admin-card__body--flush">
        <el-table v-loading="loading" :data="categories" row-key="id">
          <el-table-column label="分类名称" min-width="160">
            <template #default="{ row }">
              <span class="categories__name">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="描述" min-width="240">
            <template #default="{ row }">
              <span v-if="row.description">{{ row.description }}</span>
              <span v-else class="cell-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="文章数" width="100">
            <template #default="{ row }">
              <span class="tnum">{{ row.articleCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">
              <span class="tnum">{{ formatTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <button class="row-action" @click="openEdit(row)">编辑</button>
              <button class="row-action row-action--danger" @click="confirmDelete(row)">删除</button>
            </template>
          </el-table-column>

          <template #empty>
            <div class="empty-state">
              <p class="empty-state__title">还没有分类。</p>
              <p class="empty-state__hint">创建第一个分类，帮助读者找到感兴趣的内容。</p>
              <button class="empty-state__action" @click="openCreate">新增分类</button>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '编辑分类' : '新增分类'"
      width="440px"
    >
      <el-form
        ref="formRef"
        :model="dialog.form"
        :rules="rules"
        label-position="top"
        @submit.prevent="save"
      >
        <el-form-item label="分类名称" prop="name">
          <el-input
            v-model="dialog.form.name"
            placeholder="如：前端开发"
            maxlength="20"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="dialog.form.description"
            type="textarea"
            :rows="3"
            placeholder="简要说明该分类收录的内容（可选）"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createCategory, deleteCategory, getAdminCategories, updateCategory } from '@/api/admin'
import type { AdminCategoryItem } from '@/api/types/admin'
import dayjs from 'dayjs'

const loading = ref(false)
const saving = ref(false)
const categories = ref<AdminCategoryItem[]>([])
const formRef = ref<FormInstance>()

const dialog = reactive({
  visible: false,
  isEdit: false,
  form: { id: 0, name: '', description: '' },
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
}

const fetchCategories = async () => {
  loading.value = true
  try {
    const { data } = await getAdminCategories()
    categories.value = data
  } catch (error) {
    console.error('获取分类列表失败:', error) // 提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  dialog.isEdit = false
  dialog.form = { id: 0, name: '', description: '' }
  dialog.visible = true
}

const openEdit = (row: AdminCategoryItem) => {
  dialog.isEdit = true
  dialog.form = { id: row.id, name: row.name, description: row.description ?? '' }
  dialog.visible = true
}

const save = async () => {
  if (saving.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = { name: dialog.form.name.trim(), description: dialog.form.description.trim() }
    if (dialog.isEdit) {
      await updateCategory(dialog.form.id, payload)
      ElMessage.success('分类已更新')
    } else {
      await createCategory(payload)
      ElMessage.success('分类已创建')
    }
    dialog.visible = false
    fetchCategories()
  } catch (error) {
    console.error('保存分类失败:', error)
  } finally {
    saving.value = false
  }
}

const confirmDelete = (row: AdminCategoryItem) => {
  // 计数是含草稿与定时的全量口径（GET /admin/categories），与后端删除前的校验同一把尺子
  if (row.articleCount > 0) {
    ElMessageBox.alert(
      `「${row.name}」下还有 ${row.articleCount} 篇文章（含草稿），删除分类前需先迁移这些文章。`,
      '无法删除分类',
      { type: 'warning', confirmButtonText: '知道了' },
    )
    return
  }
  ElMessageBox.confirm(`确定要删除分类「${row.name}」吗？`, '删除分类', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await deleteCategory(row.id)
      ElMessage.success('分类已删除')
      fetchCategories()
    } catch (error) {
      console.error('删除分类失败:', error)
    }
  })
}

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm')

onMounted(() => {
  fetchCategories()
})
</script>

<style scoped lang="scss">
.categories__name {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-ink);
}

.row-action + .row-action {
  margin-left: var(--space-md);
}
</style>
