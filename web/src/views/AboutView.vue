<template>
  <div class="about-view">
    <header class="about-head">
      <el-avatar
        :size="72"
        src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"
        class="about-head__avatar"
      />
      <div class="about-head__intro">
        <h1 class="about-head__name">Ayun</h1>
        <p class="about-head__tagline">Stay hungry, stay foolish.</p>
        <p class="about-head__social tnum">
          <span>GitHub</span>
          <span aria-hidden="true">·</span>
          <span>Twitter</span>
          <span aria-hidden="true">·</span>
          <span>微信</span>
          <span aria-hidden="true">·</span>
          <span>邮件</span>
        </p>
      </div>
    </header>

    <div class="about-grid">
      <div class="about-main">
        <section class="about-section">
          <h2 class="about-section__title">关于我</h2>
          <div class="about-section__prose">
            <p>
              你好！我是
              Ayun，一名热衷于前端技术的开发工程师。我喜欢通过代码构建美丽且实用的用户界面，并在这个博客上分享我的学习心得和项目实践。
            </p>
            <p>
              目前专注于 Vue 3、TypeScript 和 Web
              性能优化领域。我相信技术改变生活，而分享让技术更有温度。
            </p>
          </div>
        </section>

        <section class="about-section">
          <h2 class="about-section__title">技术栈</h2>
          <dl class="skill-list">
            <div v-for="group in skillGroups" :key="group.title" class="skill-list__group">
              <dt>{{ group.title }}</dt>
              <dd>{{ group.skills.join(' · ') }}</dd>
            </div>
          </dl>
        </section>
      </div>

      <aside class="about-aside">
        <section class="about-section">
          <h2 class="about-section__title">经历</h2>
          <ol class="timeline">
            <li v-for="(item, index) in experience" :key="index" class="timeline__item">
              <p class="timeline__date tnum">{{ item.date }}</p>
              <h3 class="timeline__title">{{ item.title }}</h3>
              <p class="timeline__desc">{{ item.desc }}</p>
            </li>
          </ol>
        </section>
      </aside>
    </div>

    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import SiteFooter from '@/components/SiteFooter.vue'

const skillGroups = ref([
  { title: 'Frontend', skills: ['Vue 3', 'React', 'TypeScript', 'Vite', 'Sass', 'Element Plus'] },
  { title: 'Backend', skills: ['Node.js', 'Koa', 'NestJS', 'MySQL', 'Redis'] },
  { title: 'Others', skills: ['Docker', 'Git', 'CI/CD', 'SEO'] },
])

const experience = ref([
  {
    date: '2025 - 至今',
    title: '高级前端开发',
    desc: '负责企业级 SaaS 平台的前端架构设计与核心组件库开发。',
  },
  {
    date: '2023 - 2025',
    title: '全栈工程师',
    desc: '参与多个从 0 到 1 的移动端及 Web 项目，主导了后台管理系统的重构。',
  },
  {
    date: '2021 - 2023',
    title: '初级前端开发',
    desc: '开启了职业生涯，负责公司官网及营销页面的开发与维护。',
  },
  { date: '2017 - 2021', title: '计算机科学学士', desc: '在大学期间打下了坚实的计算机理论基础。' },
])
</script>

<style scoped lang="scss">
.about-head {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  padding-bottom: var(--space-xl);
  margin-bottom: var(--space-xl);
  border-bottom: 1px solid var(--color-rule);

  &__avatar {
    flex-shrink: 0;
    border: 1px solid var(--color-rule);
  }

  &__name {
    font-size: var(--text-xl);
    font-weight: 600;
    line-height: 1.2;
  }

  &__tagline {
    margin-top: var(--space-2xs);
    font-size: var(--text-base);
    color: var(--color-ink-3);
  }

  &__social {
    margin-top: var(--space-sm);
    display: flex;
    gap: var(--space-xs);
    font-size: var(--text-xs);
    color: var(--color-ink-4);
  }
}

.about-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-2xl);
}

.about-section {
  & + .about-section {
    margin-top: var(--space-2xl);
  }

  &__title {
    font-size: var(--text-md);
    margin-bottom: var(--space-md);
  }

  &__prose {
    max-width: 65ch;
    font-size: var(--text-base);
    line-height: 1.85;
    color: var(--color-ink-2);

    p + p {
      margin-top: var(--space-md);
    }
  }
}

.skill-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);

  &__group {
    display: grid;
    grid-template-columns: 6rem minmax(0, 1fr);
    gap: var(--space-md);
    align-items: baseline;

    dt {
      font-family: var(--font-mono);
      font-size: var(--text-xs);
      color: var(--color-ink-4);
    }

    dd {
      font-size: var(--text-sm);
      line-height: 1.8;
      color: var(--color-ink-2);
    }
  }
}

.timeline {
  border-inline-start: 1px solid var(--color-rule);
}

.timeline__item {
  position: relative;
  padding-block: var(--space-xs);
  padding-inline-start: var(--space-lg);

  &::before {
    content: '';
    position: absolute;
    inset-inline-start: -0.1875rem;
    top: 1.05rem;
    width: 0.375rem;
    height: 0.375rem;
    background-color: var(--color-rule-strong);
    border-radius: var(--radius-xs);
  }

  &:first-child::before {
    background-color: var(--color-accent);
  }

  & + .timeline__item {
    margin-top: var(--space-md);
  }
}

.timeline__date {
  font-size: var(--text-xs);
  color: var(--color-ink-4);
}

.timeline__title {
  margin-top: var(--space-3xs);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-ink);
}

.timeline__desc {
  margin-top: var(--space-3xs);
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--color-ink-3);
}

@media (min-width: 60rem) {
  .about-grid {
    grid-template-columns: minmax(0, 1fr) 16rem;
  }
}
</style>
