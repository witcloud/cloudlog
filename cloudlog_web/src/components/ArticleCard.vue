<script setup lang="ts">
import type { Article } from '@/api/types/article'
import { useRouter } from 'vue-router'
import { computed } from 'vue'
import { formatMinioUrl } from '@/config'
import PostCounts from '@/components/PostCounts.vue'

const props = defineProps<{
  article: Article
}>()

const router = useRouter()

const goToDetail = () => {
  router.push(`/articles/${props.article.id}`)
}

const coverUrl = computed(() => formatMinioUrl(props.article.cover))
</script>

<template>
  <article class="article-card" @click="goToDetail">
    <div class="article-card__body">
      <h2 class="article-card__title">
        <span v-if="article.isTop" class="article-card__pin">置顶</span>{{ article.title }}
      </h2>
      <p class="article-card__excerpt">{{ article.excerpt }}</p>
      <p class="article-card__meta">
        <span class="article-card__stats">
          <RouterLink
            v-if="article.author?.id"
            class="article-card__author"
            :to="`/authors/${article.author.id}`"
            @click.stop
            >{{ article.author.username }}</RouterLink
          >
          <span v-else class="article-card__author">{{ article.author?.username }}</span>
          <span class="article-card__divider" aria-hidden="true"></span>
          <PostCounts
            :views="article.views"
            :comments="article.comments"
            :likes="article.likes"
          />
        </span>

        <span v-if="article.tags?.length" class="article-card__tags">
          <RouterLink
            v-for="tag in article.tags"
            :key="tag.id"
            class="article-card__tag"
            :to="{ path: '/', query: { tagId: tag.id } }"
            @click.stop
          >
            {{ tag.name }}
          </RouterLink>
        </span>
      </p>
    </div>

    <img
      v-if="article.cover"
      class="article-card__cover"
      :src="coverUrl"
      :alt="article.title"
      loading="lazy"
    />
  </article>
</template>

<style scoped>
.article-card {
  display: flex;
  gap: var(--space-md);
  align-items: flex-start;
  padding-block: var(--space-md);
  cursor: pointer;
}

.article-card__body {
  flex: 1;
  min-width: 0;
}

.article-card__pin {
  display: inline-block;
  margin-right: var(--space-xs);
  padding-inline: var(--space-2xs);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  font-weight: 400;
  line-height: 1.4;
  color: var(--color-accent);
  background-color: var(--color-accent-wash);
  border-radius: var(--radius-xs);
}

.article-card__title {
  font-size: var(--text-base);
  font-weight: 700;
  line-height: 1.5;
  color: var(--color-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color var(--dur-micro) var(--ease-out);
}

.article-card:hover .article-card__title {
  color: var(--color-accent);
}

/* 作者名与标签是这一行之内的独立落点：指针落在它们上只亮它们自己。
   否则标题同时变橙，等于承诺了一次并不会发生的跳转 */
.article-card:has(.article-card__author:hover, .article-card__tag:hover) .article-card__title {
  color: var(--color-ink);
}

/* 摘要固定一行：行高恒定，列表才会被读成「一行一行」而非「一块一块」。
   min-height 让无摘要的行也占住这一行盒——正文列恒 78.8px，封面才能既是固定框又两缘咬住 */
.article-card__excerpt {
  margin-top: var(--space-2xs);
  min-height: calc(var(--text-sm) * 1.6);
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-ink-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.article-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-top: var(--space-xs);
  /* 18 行盒 + 标签芯片上下各 (1 padding + 1 border)。行高取芯片高，
     否则 ≤34rem 隐藏标签时整行塌 4px，跨断点列表会跳一截 */
  min-height: 22px;
  font-size: var(--text-xs);
  line-height: 1.5;
  color: var(--color-ink-3);
}

/* 左侧统计组：可收缩、整项可换行——带封面的行正文列只剩 177px，宁可换行也不压到封面上 */
.article-card__stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-xs);
  min-width: 0;
}

.article-card__author {
  color: var(--color-ink-2);
  font-weight: 500;
  white-space: nowrap;
  transition: color var(--dur-micro) var(--ease-out);
}

/* 只有变色，不叠阴影与位移（ui-design.md §5.2） */
.article-card__author:hover {
  color: var(--color-accent);
}

/* 行内分隔取本行墨色：--color-rule 是 L=88 的横向发丝线，缩成 12px 竖线过不了 ui-design.md:192
   的非文字图形 3:1；--color-rule-strong 又被 :180 禁用于版面分隔 */
.article-card__divider {
  width: 1px;
  height: 1em;
  background-color: currentColor;
}

.article-card__tags {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-xs);
  min-width: 0;
}

.article-card__tag {
  padding: 1px var(--space-2xs);
  color: var(--color-ink-3);
  border: 1px solid var(--color-rule-strong);
  border-radius: var(--radius-sm);
  white-space: nowrap;
  transition:
    color var(--dur-micro) var(--ease-out),
    border-color var(--dur-micro) var(--ease-out);
}

.article-card__tag:hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
}

.article-card__cover {
  width: 128px;
  /* 参考图约 1.7:1。定宽 + 定比例 = 高恒 75.3，图片自身比例再也撑不动行；
     75.3 ≈ 正文列 78.8 减掉下面 4px 顶边偏移，两缘仍咬住标题字面顶与标签行下缘 */
  aspect-ratio: 17 / 10;
  flex-shrink: 0;
  /* 标题行盒 24px、字身 16px，行盒顶部比字面高 4px */
  margin-top: var(--space-2xs);
  object-fit: cover;
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-sm);
}

/* 图标版统计组实测 142–157px，带封面的行在 375px 上还剩 177px，放得下，封面不再让位；
   标签此时只剩 20px 余量，窄屏仍整组隐藏 */
@media (max-width: 34rem) {
  .article-card__tags {
    display: none;
  }

  /* 窄屏正文列只剩 162px，统计组最宽实测 163.2px 已在换行刀口上，封面不再跟着加宽；
     高度跟着 1.7 比例降到 49.4，窄屏不再强求两缘咬住 */
  .article-card__cover {
    width: 84px;
  }
}
</style>
