<template>
  <footer class="article-footer">
    <div class="share-actions">
      <span class="share-label">分享到：</span>
      <el-button-group>
        <el-tooltip content="分享到微博" placement="top">
          <el-button circle @click="shareTo('weibo')">
            <el-icon><Share /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="微信扫码分享" placement="top">
          <el-button circle @click="shareTo('wechat')">
            <el-icon><ChatDotRound /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="分享到 Twitter" placement="top">
          <el-button circle @click="shareTo('twitter')">
            <el-icon><Position /></el-icon>
          </el-button>
        </el-tooltip>
      </el-button-group>
    </div>

    <div class="article-copyright">
      <p>本文作者：{{ authorName }}</p>
      <p>
        本文链接：<el-link type="primary" :href="articleLink" target="_blank">{{
          articleLink
        }}</el-link>
      </p>
      <p>
        版权声明：本博客所有文章除特别声明外，均采用
        <el-link
          type="primary"
          href="https://creativecommons.org/licenses/by-nc-sa/4.0/"
          target="_blank"
        >
          CC BY-NC-SA 4.0
        </el-link>
        许可协议。转载请注明来自 AyunBlog！
      </p>
    </div>

    <!-- WeChat QR Code Dialog -->
    <el-dialog v-model="wechatDialogVisible" title="微信扫码分享" width="200px" center>
      <div class="wechat-qr">
        <el-image
          :src="`https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(articleLink)}`"
        />
        <p>微信扫一扫，分享文章</p>
      </div>
    </el-dialog>
  </footer>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Share, ChatDotRound, Position } from '@element-plus/icons-vue'

const props = defineProps<{
  authorName: string
  articleTitle: string
}>()

const wechatDialogVisible = ref(false)
const articleLink = computed(() => window.location.href)

const shareTo = (platform: string) => {
  const url = encodeURIComponent(articleLink.value)
  const title = encodeURIComponent(props.articleTitle)

  let shareUrl = ''
  switch (platform) {
    case 'weibo':
      shareUrl = `http://service.weibo.com/share/share.php?url=${url}&title=${title}`
      window.open(shareUrl, '_blank')
      break
    case 'twitter':
      shareUrl = `https://twitter.com/intent/tweet?url=${url}&text=${title}`
      window.open(shareUrl, '_blank')
      break
    case 'wechat':
      wechatDialogVisible.value = true
      break
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.article-footer {
  margin-top: $spacing-xl;
  padding-top: $spacing-lg;
  border-top: 1px dashed $border-color;

  .share-actions {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    margin-bottom: $spacing-xl;

    .share-label {
      font-size: 14px;
      color: $text-secondary;
    }
  }

  .article-copyright {
    background-color: $bg-color;
    padding: $spacing-md;
    border-left: 4px solid $primary-color;
    font-size: 14px;
    color: $text-regular;
    p {
      margin: $spacing-xs 0;
    }
  }
}

.wechat-qr {
  text-align: center;
  p {
    font-size: 12px;
    color: $text-secondary;
    margin-top: $spacing-sm;
  }
}
</style>
