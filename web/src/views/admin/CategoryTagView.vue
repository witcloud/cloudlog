<template>
  <div class="admin-view">
    <el-row :gutter="24">
      <!-- Categories Management -->
      <el-col :xs="24" :md="12">
        <el-card class="admin-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>分类管理</span>
              <el-button type="primary" size="small" @click="addCategory">新增分类</el-button>
            </div>
          </template>
          <el-table :data="categories" style="width: 100%">
            <el-table-column prop="name" label="分类名称" />
            <el-table-column prop="count" label="文章数" width="100" />
            <el-table-column label="操作" width="150">
              <template #default="scope">
                <el-button size="small" @click="editCategory(scope.row)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteCategory(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- Tags Management -->
      <el-col :xs="24" :md="12">
        <el-card class="admin-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>标签管理</span>
              <el-button type="primary" size="small" @click="addTag">新增标签</el-button>
            </div>
          </template>
          <div class="tags-container">
            <el-tag
              v-for="tag in tags"
              :key="tag"
              class="admin-tag"
              closable
              @close="deleteTag(tag)"
            >
              {{ tag }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Dialogs -->
    <el-dialog v-model="categoryDialog.visible" :title="categoryDialog.isEdit ? '编辑分类' : '新增分类'">
      <el-form :model="categoryDialog.form">
        <el-form-item label="分类名称">
          <el-input v-model="categoryDialog.form.name" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { ElMessageBox, ElMessage } from 'element-plus';

const categories = ref([
  { id: 1, name: 'Vue', count: 12 },
  { id: 2, name: 'React', count: 8 },
  { id: 3, name: 'Node.js', count: 15 }
]);

const tags = ref(['Vue3', 'TypeScript', 'Vite', 'AI', 'Nodejs', 'Wasm']);

const categoryDialog = reactive({
  visible: false,
  isEdit: false,
  form: { id: 0, name: '' }
});

const addCategory = () => {
  categoryDialog.isEdit = false;
  categoryDialog.form = { id: 0, name: '' };
  categoryDialog.visible = true;
};

const editCategory = (row: any) => {
  categoryDialog.isEdit = true;
  categoryDialog.form = { ...row };
  categoryDialog.visible = true;
};

const saveCategory = () => {
  if (categoryDialog.isEdit) {
    const index = categories.value.findIndex(c => c.id === categoryDialog.form.id);
    if (index !== -1) categories.value[index].name = categoryDialog.form.name;
  } else {
    categories.value.push({
      id: Date.now(),
      name: categoryDialog.form.name,
      count: 0
    });
  }
  categoryDialog.visible = false;
  ElMessage.success('保存成功');
};

const deleteCategory = (row: any) => {
  ElMessageBox.confirm(`确定要删除分类 "${row.name}" 吗？`, '警告', {
    type: 'warning'
  }).then(() => {
    categories.value = categories.value.filter(c => c.id !== row.id);
    ElMessage.success('删除成功');
  });
};

const addTag = () => {
  ElMessageBox.prompt('请输入标签名称', '新增标签').then(({ value }) => {
    if (value && !tags.value.includes(value)) {
      tags.value.push(value);
      ElMessage.success('标签已添加');
    }
  });
};

const deleteTag = (tag: string) => {
  tags.value = tags.value.filter(t => t !== tag);
  ElMessage.success('标签已移除');
};
</script>

<style scoped lang="scss">
@use "../../styles/variables.scss" as *;

.admin-view {
  padding-bottom: $spacing-xl;
}

.admin-card {
  border-radius: $border-radius-large;
  border: 1px solid $border-color;
  margin-bottom: $spacing-lg;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;

  .admin-tag {
    padding: 8px 12px;
    height: auto;
    font-size: 14px;
  }
}
</style>
