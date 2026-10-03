<template>
  <div class="about">
    <header class="about__head">
      <div class="about__identity">
        <el-avatar :size="72" :src="avatarUrl" class="about__avatar" />
        <div>
          <h1 class="about__name">{{ content.name }}</h1>
          <p class="about__role">{{ content.role }}</p>
        </div>
      </div>

      <p class="about__lead">{{ content.lead }}</p>
    </header>

    <section class="about__section">
      <h2 class="about__label">本站技术栈</h2>
      <dl class="stack">
        <div v-for="group in skillGroups" :key="group.title" class="stack__row">
          <dt class="stack__key">{{ group.title }}</dt>
          <dd class="stack__value">{{ group.skills.join(' · ') }}</dd>
        </div>
      </dl>
    </section>

    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue'
import { getAboutContent } from '@/api/site'
import type { AboutContent } from '@/api/types/site'
import { formatMinioUrl, defaultAvatarUrl } from '@/config'
import SiteFooter from '@/components/SiteFooter.vue'

/* 与 SettingsService 的内置默认值同字：接口返回前先按它渲染，后台清空某项时也由它兜底 */
const content = reactive<AboutContent>({
  name: 'tcloud',
  role: '记录技术与生活',
  lead: '主要写给自己看——把学过、用过、想过的东西记下来，方便回头查。如果恰好也帮到了别人，那就更好。',
  avatar: '',
})

const avatarUrl = computed(() => formatMinioUrl(content.avatar) || defaultAvatarUrl)

onMounted(async () => {
  try {
    const { data } = await getAboutContent()
    Object.assign(content, data)
  } catch (error) {
    console.error('获取关于页文案失败:', error) // 提示由拦截器统一处理
  }
})

const skillGroups = [
  { title: 'Frontend', skills: ['Vue 3', 'TypeScript', 'Vite', 'Pinia', 'Element Plus'] },
  { title: 'Backend', skills: ['Java 21', 'Spring Boot 3', 'Spring Security + JWT', 'MyBatis'] },
  { title: 'Infra', skills: ['MySQL 8', 'Redis', 'MinIO'] },
]
</script>

<style scoped lang="scss">
/* 功能型页面按用途收窄中心区，docs/ui-design.md §3.6 的双栏随站标块一并撤除 */
.about {
  max-width: 44rem;
}

.about__head {
  padding-bottom: var(--space-xl);
  border-bottom: 1px solid var(--color-rule);
}

.about__identity {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.about__avatar {
  flex-shrink: 0;
  border: 1px solid var(--color-rule);
}

.about__name {
  font-size: var(--text-2xl);
  letter-spacing: -0.01em;
}

.about__role {
  margin-top: var(--space-2xs);
  font-size: var(--text-sm);
  color: var(--color-ink-3);
}

.about__lead {
  max-width: 38rem;
  margin-top: var(--space-lg);
  font-size: var(--text-base);
  line-height: 1.85;
  color: var(--color-ink-2);
}

.about__section {
  margin-top: var(--space-xl);
}

.about__label {
  margin-bottom: var(--space-sm);
  font-size: var(--text-xs);
  font-weight: 400;
  letter-spacing: 0.08em;
  color: var(--color-ink-4);
}

.stack__row {
  display: grid;
  grid-template-columns: 7rem minmax(0, 1fr);
  gap: var(--space-md);
  align-items: baseline;
  padding-block: var(--space-md);

  & + .stack__row {
    border-top: 1px solid var(--color-rule);
  }
}

.stack__key {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-ink-3);
}

.stack__value {
  font-size: var(--text-base);
  line-height: 1.8;
  color: var(--color-ink-2);
}
</style>
