<template>
  <div class="about-view">
    <!-- Hero Section -->
    <div class="hero-section">
      <el-avatar
        :size="120"
        src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"
        class="avatar"
      />
      <h1 class="name">Ayun</h1>
      <p class="tagline">Stay hungry, stay foolish.</p>
      <div class="social-links">
        <el-button circle
          ><el-icon><i class="fab fa-github"></i></el-icon
        ></el-button>
        <el-button circle
          ><el-icon><i class="fab fa-twitter"></i></el-icon
        ></el-button>
        <el-button circle
          ><el-icon><i class="fab fa-weixin"></i></el-icon
        ></el-button>
        <el-button circle
          ><el-icon><Message /></el-icon
        ></el-button>
      </div>
    </div>

    <el-row :gutter="40" class="about-content">
      <!-- Left Column: Bio & Skills -->
      <el-col :xs="24" :md="14">
        <section class="bio-section">
          <h2 class="section-title">👋 关于我</h2>
          <div class="bio-text">
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

        <section class="skills-section">
          <h2 class="section-title">🛠️ 技术栈</h2>
          <div class="skill-groups">
            <div v-for="group in skillGroups" :key="group.title" class="skill-group">
              <h3>{{ group.title }}</h3>
              <div class="tags">
                <el-tag v-for="skill in group.skills" :key="skill" effect="plain" round>
                  {{ skill }}
                </el-tag>
              </div>
            </div>
          </div>
        </section>
      </el-col>

      <!-- Right Column: Timeline/Experience -->
      <el-col :xs="24" :md="10">
        <section class="experience-section">
          <h2 class="section-title">🚀 经历</h2>
          <el-timeline>
            <el-timeline-item
              v-for="(item, index) in experience"
              :key="index"
              :timestamp="item.date"
              :type="index === 0 ? 'primary' : ''"
              size="large"
            >
              <h4 class="exp-title">{{ item.title }}</h4>
              <p class="exp-desc">{{ item.desc }}</p>
            </el-timeline-item>
          </el-timeline>
        </section>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Message } from '@element-plus/icons-vue'

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
@use '../styles/variables.scss' as *;

.about-view {
  padding-bottom: $spacing-xl * 2;
}

.hero-section {
  text-align: center;
  padding: $spacing-xl * 2 0;
  margin-bottom: $spacing-xl * 2;
  background: linear-gradient(to bottom, rgba($primary-color, 0.05), transparent);
  border-radius: $border-radius-large;

  .avatar {
    border: 4px solid $white;
    box-shadow: $box-shadow;
    margin-bottom: $spacing-md;
  }

  .name {
    font-size: 36px;
    font-weight: 800;
    margin: 0 0 $spacing-xs;
  }

  .tagline {
    color: $text-secondary;
    font-size: 18px;
    margin-bottom: $spacing-lg;
  }

  .social-links {
    display: flex;
    justify-content: center;
    gap: $spacing-md;
    .el-button {
      font-size: 20px;
    }
  }
}

.section-title {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: $spacing-xl;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.bio-section {
  margin-bottom: $spacing-xl * 2;
  .bio-text {
    font-size: 16px;
    line-height: 1.8;
    color: $text-regular;
    p {
      margin-bottom: $spacing-md;
    }
  }
}

.skills-section {
  .skill-group {
    margin-bottom: $spacing-lg;
    h3 {
      font-size: 16px;
      color: $text-secondary;
      margin-bottom: $spacing-sm;
    }
    .tags {
      display: flex;
      flex-wrap: wrap;
      gap: $spacing-sm;
    }
  }
}

.experience-section {
  .exp-title {
    font-size: 16px;
    font-weight: 700;
    margin: 0 0 4px;
  }
  .exp-desc {
    font-size: 14px;
    color: $text-secondary;
    line-height: 1.6;
  }
}
</style>
