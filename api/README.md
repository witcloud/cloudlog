# AyunBlog-Springboot3

AyunBlog-Springboot3 是一个基于 Spring Boot 3 框架构建的现代化博客系统后端项目。它集成了权限管理、文章发布、评论系统、SEO 优化等功能，并提供了完善的 API 接口文档。

## 🌟 项目特性

- **现代技术栈**：采用 Java 21 和 Spring Boot 3.5.14 构建。
- **安全认证**：集成 Spring Security，使用 JWT (JSON Web Token) 进行身份验证与授权。
- **持久层框架**：使用 MyBatis 作为 ORM 框架，配合 MySQL 数据库。
- **高性能缓存**：集成 Redis 用于数据缓存。
- **API 文档**：集成 SpringDoc OpenAPI (Swagger UI)，方便前后端联调。
- **完善的功能**：
  - 用户管理（注册、登录、个人中心）
  - 文章管理（支持 Markdown、分类、标签、置顶、阅读量统计）
  - 评论系统（支持多级评论回复）
  - 搜索优化（SEO 元数据管理）
  - 访问监控（详细的访问日志记录）

## 🛠️ 技术栈

- **核心框架**：Spring Boot 3.5.14
- **安全框架**：Spring Security
- **认证机制**：JJWT (Java JWT)
- **数据库**：MySQL 8.0+
- **ORM 框架**：MyBatis
- **缓存**：Redis
- **工具库**：Hutool, Lombok
- **API 文档**：SpringDoc OpenAPI
- **分页插件**：PageHelper

## 🚀 快速开始

### 环境要求
- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### 配置步骤
1. **数据库初始化**：执行 `src/main/resources/schema.sql` 中的脚本创建表结构。
2. **配置文件修改**：修改 `src/main/resources/application.yml` 中的数据库及 Redis 连接信息。
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/ayun_blog
       username: your_username
       password: your_password
     data:
       redis:
         host: localhost
         port: 6379
         password: your_password
   ```
3. **编译运行**：
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

## 📖 接口文档

项目运行后，可以通过以下地址访问 Swagger 接口文档：
- **Swagger UI**: `http://localhost:8080/api/v1/swagger-ui.html`
- **OpenAPI Docs**: `http://localhost:8080/api/v1/v3/api-docs`

## 📂 项目结构

```text
src/main/java/com/ayun/
├── common/         # 公共类（如通用返回结果）
├── config/         # 配置类（Redis, OpenAPI 等）
├── controller/     # 接口层
├── dto/            # 数据传输对象
├── entity/         # 数据库实体类
├── mapper/         # MyBatis Mapper 接口
├── security/       # 安全配置与 JWT 逻辑
└── service/        # 业务逻辑层
```
