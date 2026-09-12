# CloudLog

个人博客系统，前后端代码共存于同一仓库。

## 目录结构

```text
cloudlog/
├── web/          # 前端项目（Vue 3 + TypeScript + Vite）
├── api/          # 后端项目（Spring Boot 3 + MyBatis）
├── README.md
└── .gitignore
```

## 功能概览

- 用户注册 / 登录（邮箱验证码），角色分为博主（admin）与普通用户
- 文章管理：Markdown 编辑、草稿箱、发布、分类、标签、置顶、阅读量统计
- 评论系统（多级回复）、SEO 元数据、访问日志
- 图片存储基于 MinIO（文章配图与头像上传）

## 技术栈

- **前端（web/）**：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Axios、md-editor-v3
- **后端（api/）**：Java 21、Spring Boot 3、Spring Security + JWT、MyBatis、MySQL 8、Redis、MinIO、SpringDoc OpenAPI

## 快速开始

### 1. 准备依赖服务

- MySQL 8.0+：执行 `api/src/main/resources/schema.sql` 初始化表结构
- Redis 6.0+
- MinIO（仅图片上传需要，可选）

### 2. 后端（api/）

```bash
cd api
cp src/main/resources/application.example.yml src/main/resources/application.yml
# 编辑 application.yml，填入数据库/Redis/邮箱密码与 JWT 密钥
mvn spring-boot:run
```

- 启动后接口文档：`http://localhost:8080/api/v1/swagger-ui.html`

### 3. 前端（web/）

环境要求：Node.js ^20.19.0 || >=22.12.0

```bash
cd web
cp .env.example .env
npm install
npm run dev
```

- 开发模式下通过 Vite 代理将 `/api` 请求转发至后端（默认 `http://127.0.0.1:8080`，可在 `web/.env` 中调整）
- 生产构建：`npm run build`；代码检查：`npm run lint`；类型检查：`npm run type-check`

## 配置与敏感信息约定

配置文件本体（含真实密码/密钥）不提交，Git 中只保留 `*.example` 模板：

```text
web/
├── .env.example                # 提交（模板）
└── .env                        # 不提交（真实配置）

api/src/main/resources/
├── application.example.yml     # 提交（模板）
└── application.yml             # 不提交（真实配置，含密码/密钥）
```

两者均已被 `.gitignore` 忽略，克隆后需按模板各自创建。
