# CloudLog

个人博客 Monorepo，前后端代码共存于同一仓库。

## 目录结构

```text
cloudlog/
├── web/          # 前端项目（Vue 3 + TypeScript + Vite）
├── api/          # 后端项目（Spring Boot 3 + MyBatis）
├── README.md
└── .gitignore
```

## 技术栈

- **前端（web/）**：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Axios、md-editor-v3
- **后端（api/）**：Java 21、Spring Boot 3、Spring Security + JWT、MyBatis、MySQL、Redis、MinIO、SpringDoc OpenAPI

两个子项目各自维护独立的依赖管理文件（`package.json` / `pom.xml`）、环境变量配置和构建方式，互不干扰。

## 快速开始

### 后端（api/）

环境要求：JDK 21+、Maven 3.8+、MySQL 8.0+、Redis 6.0+

1. 执行 `api/src/main/resources/schema.sql` 初始化数据库；
2. 按需修改 `api/src/main/resources/application.yml` 中的数据库、Redis、MinIO 连接信息；
3. 启动：

```bash
cd api
mvn spring-boot:run
```

启动后接口文档：`http://localhost:8080/api/v1/swagger-ui.html`

### 前端（web/）

环境要求：Node.js ^20.19.0 || >=22.12.0

```bash
cd web
npm install
npm run dev
```

开发模式下通过 Vite 代理将 `/api` 请求转发至后端（默认 `http://127.0.0.1:8080`，可在 `web/.env.development` 中调整）。

## 分支约定

分支仅用于功能开发、缺陷修复与版本发布，前后端始终共存于同一分支：

- `main`：稳定分支
- `feature/xxx`：新功能开发
- `fix/xxx`：缺陷修复
- `release/xxx`：版本发布
