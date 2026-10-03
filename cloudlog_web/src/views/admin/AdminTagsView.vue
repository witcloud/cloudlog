<template>
  <div class="tags admin-page">
    <header class="admin-page__head">
      <div>
        <h1 class="admin-page__title">标签管理</h1>
        <p class="admin-page__subtitle">标签由用户写作时创建，这里负责重名、冗余标签的治理</p>
      </div>
    </header>

    <div class="admin-card">
      <div class="admin-card__body admin-card__body--flush">
        <el-table v-loading="loading" :data="tags" row-key="id">
          <el-table-column label="标签" min-width="160">
            <template #default="{ row }">
              <span class="tags__chip">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="关联文章数" width="120">
            <template #default="{ row }">
              <span class="tnum">{{ row.articleCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">
              <span class="tnum">{{ formatTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <button class="row-action" @click="openEdit(row)">编辑</button>
              <button class="row-action" @click="openMerge(row)">合并</button>
              <button class="row-action row-action--danger" @click="confirmDelete(row)">删除</button>
            </template>
          </el-table-column>

          <template #empty>
            <div class="empty-state">
              <p class="empty-state__title">还没有标签。</p>
              <p class="empty-state__hint">用户在写作时创建的标签会出现在这里。</p>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editDialog.visible" title="编辑标签" width="400px">
      <el-form
        ref="editFormRef"
        :model="editDialog.form"
        :rules="editRules"
        label-position="top"
        @submit.prevent="saveEdit"
      >
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="editDialog.form.name" placeholder="标签名称" maxlength="20" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 合并弹窗 -->
    <el-dialog v-model="mergeDialog.visible" title="合并标签" width="440px">
      <el-form ref="mergeFormRef" :model="mergeDialog.form" :rules="mergeRules" label-position="top">
        <el-form-item label="保留的标签">
          <el-select
            v-model="mergeDialog.form.targetId"
            placeholder="选择合并目标"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="tag in mergeCandidates"
              :key="tag.id"
              :label="`${tag.name}（${tag.articleCount} 篇）`"
              :value="tag.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p v-if="mergeDialog.source" class="tags__merge-hint">
        「{{ mergeDialog.source.name }}」（{{ mergeDialog.source.articleCount }} 篇文章）的标签关联将全部转移到目标标签，「{{ mergeDialog.source.name }}」随后被删除。此操作不可撤销。
      </p>
      <template #footer>
        <el-button @click="mergeDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMerge">确认合并</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { deleteAdminTag, getAdminTags, mergeTag, updateTag } from '@/api/admin'
import type { AdminTagItem } from '@/api/types/admin'
import dayjs from 'dayjs'

const loading = ref(false)
const saving = ref(false)
const tags = ref<AdminTagItem[]>([])
const editFormRef = ref<FormInstance>()
const mergeFormRef = ref<FormInstance>()

const editDialog = reactive({
  visible: false,
  form: { id: 0, name: '' },
})

const mergeDialog = reactive({
  visible: false,
  source: null as AdminTagItem | null,
  form: { targetId: null as number | null },
})

const editRules: FormRules = {
  name: [{ required: true, message: '请输入标签名称', trigger: 'blur' }],
}

const mergeRules: FormRules = {
  targetId: [{ required: true, message: '请选择合并目标标签', trigger: 'change' }],
}

const mergeCandidates = computed(() =>
  tags.value.filter((tag) => tag.id !== mergeDialog.source?.id),
)

const fetchTags = async () => {
  loading.value = true
  try {
    const { data } = await getAdminTags()
    tags.value = data
  } catch (error) {
    console.error('获取标签列表失败:', error) // 提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

const openEdit = (row: AdminTagItem) => {
  editDialog.form = { id: row.id, name: row.name }
  editDialog.visible = true
}

const saveEdit = async () => {
  if (saving.value) return
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await updateTag(editDialog.form.id, editDialog.form.name.trim())
    ElMessage.success('标签已更新')
    editDialog.visible = false
    fetchTags()
  } catch (error) {
    console.error('更新标签失败:', error)
  } finally {
    saving.value = false
  }
}

const openMerge = (row: AdminTagItem) => {
  mergeDialog.source = row
  mergeDialog.form.targetId = null
  mergeDialog.visible = true
}

const saveMerge = async () => {
  const valid = await mergeFormRef.value?.validate().catch(() => false)
  if (!valid || !mergeDialog.source) return

  saving.value = true
  try {
    await mergeTag(mergeDialog.source.id, mergeDialog.form.targetId as number)
    ElMessage.success('标签已合并')
    mergeDialog.visible = false
    fetchTags()
  } catch (error) {
    console.error('合并标签失败:', error)
  } finally {
    saving.value = false
  }
}

const confirmDelete = (row: AdminTagItem) => {
  const hint =
    row.articleCount > 0
      ? `「${row.name}」已关联 ${row.articleCount} 篇文章，删除后这些关联将一并移除。`
      : `确定要删除标签「${row.name}」吗？`
  ElMessageBox.confirm(hint, '删除标签', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await deleteAdminTag(row.id)
      ElMessage.success('标签已删除')
      fetchTags()
    } catch (error) {
      console.error('删除标签失败:', error)
    }
  })
}

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm')

onMounted(() => {
  fetchTags()
})
</script>

<style scoped lang="scss">
.tags__chip {
  display: inline-block;
  padding: 2px var(--space-xs);
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  border: 1px solid var(--color-rule-strong);
  border-radius: var(--radius-xs);
  white-space: nowrap;
}

.tags__merge-hint {
  margin: 0;
  padding: var(--space-sm);
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-ink-3);
  background-color: var(--color-paper-3);
  border-radius: var(--radius-sm);
}

.row-action + .row-action {
  margin-left: var(--space-md);
}
</style>
