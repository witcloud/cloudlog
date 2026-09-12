<template>
  <footer class="article-footer">
    <div class="article-footer__share">
      <span class="article-footer__label">分享</span>
      <button class="article-footer__share-link" @click="shareTo('weibo')">微博</button>
      <span aria-hidden="true">·</span>
      <button class="article-footer__share-link" @click="shareTo('wechat')">微信</button>
      <span aria-hidden="true">·</span>
      <button class="article-footer__share-link" @click="shareTo('twitter')">Twitter</button>
    </div>

    <div class="article-footer__copyright">
      <p>本文作者：{{ authorName }}</p>
      <p>
        本文链接：<a class="article-footer__link" :href="articleLink" target="_blank">{{
          articleLink
        }}</a>
      </p>
      <p>
        版权声明：本博客所有文章除特别声明外，均采用
        <a
          class="article-footer__link"
          href="https://creativecommons.org/licenses/by-nc-sa/4.0/"
          target="_blank"
        >
          CC BY-NC-SA 4.0
        </a>
        许可协议。转载请注明来自 CloudLog。
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

<style scoped>
.article-footer {
  margin-top: var(--space-xl);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-rule);
}

.article-footer__share {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.article-footer__label {
  font-size: var(--text-xs);
  color: var(--color-ink-4);
  margin-right: var(--space-2xs);
}

.article-footer__share-link {
  font-size: var(--text-sm);
  color: var(--color-ink-2);
  white-space: nowrap;
  transition: color var(--dur-micro) var(--ease-out);
}

.article-footer__share-link:hover {
  color: var(--color-accent);
}

.article-footer__copyright {
  margin-top: var(--space-lg);
  padding: var(--space-md);
  background-color: var(--color-paper-3);
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  line-height: 1.8;
  color: var(--color-ink-2);
}

.article-footer__copyright p + p {
  margin-top: var(--space-3xs);
}

.article-footer__link {
  color: var(--color-accent);
  overflow-wrap: anywhere;
}

.article-footer__link:hover {
  text-decoration: underline;
  text-underline-offset: 0.25em;
}

.wechat-qr {
  text-align: center;
}

.wechat-qr p {
  margin-top: var(--space-sm);
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}
</style>
