<script setup lang="ts">
import { RouterView } from 'vue-router'
// 语言包从 app.use(ElementPlus, { locale }) 挪到这里：组件改成按需注册后没有全局 install 那一步了，
// el-config-provider 是官方给的等价入口，分页的「前往」、表格的「暂无数据」这类内部文案靠它
import locale from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <!-- ElConfigProvider 不产生额外 DOM 节点，只是 provide，所以不影响 #app 下的层级选择器 -->
  <ElConfigProvider :locale="locale">
    <RouterView />
  </ElConfigProvider>
</template>

<style>
/* Global styles are in src/assets/styles/ */
</style>
