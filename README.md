# CloudLog

个人博客系统，前后端代码共存于同一仓库。

## 目录结构

```text
cloudlog/
├── cloudlog_web/ # 前端项目（Vue 3 + TypeScript + Vite）
│   ├── Dockerfile      # node 构建 → nginx 托管 dist
│   └── nginx.conf      # 三条 SEO 精确映射 + SPA 回退 + /api/v1 反代
├── cloudlog_api/ # 后端项目（Spring Boot 3.5 + MyBatis）
│   └── Dockerfile      # maven 构建 → JRE 21 运行
├── deploy/ # 容器化部署：挂载进 api 的结构配置 + 最小闭环验证脚本
├── docs/ # 项目规范（业务模型 / 数据库设计 / UI 设计）、schema.sql（建表真源）与 data.sql（演示数据）
├── docker-compose.yml # 单实例编排：MySQL + Redis + 后端 + 前端（MinIO 见 --profile images）
├── .env.example # 容器部署的凭据模板（复制为 .env，.env 不入库）
├── README.md
└── .gitignore
```

## 功能概览

- 用户注册 / 登录（邮箱验证码）、找回密码，角色分为博主（admin）与普通用户
- 文章管理：Markdown 编辑、草稿箱、定时发布、发布、分类、标签（含合并）、置顶、阅读量统计
- 文章状态四态：`draft` / `published` / `scheduled` / `offline`；`offline`（已下线）仅作者与管理员可见，读者访问一律按不存在处理（404）
- 列表排序 `sort` 三档：`latest`（接口缺省值，发布时间倒序，搜索页与作者主页走这档）、`recommended`（热度加权，首页默认展示档）、`popular`（纯热度，侧栏热榜专用）；热度 = 阅读 + 点赞×3 + 评论×5，并按发布天数衰减
- 置顶文章在 `latest` / `recommended` 下恒定排在前，多篇置顶之间按 `top_order` 权重从大到小
- 互动：评论（两级回复，违禁词机审 + 人工审核）、点赞、关注作者（个人主页含关注/粉丝/点赞列表）
- 站点运营：后台文章/评论/分类/标签/用户管理、站点开关、关于页文案配置、文章级 SEO 元数据（详情页的标题/描述/关键词，未填项按文章标题、摘要、标签名回退）、访问日志（概览/趋势/Top 榜/明细，来源地址脱敏）
- 登录日志：后台可查登录成败与爆破尝试（概览 / 攻击 Top 榜 / 明细，来源地址脱敏），锁定阈值与时长在后台可调（默认 5 次 / 15 分钟）
- 图片存储基于 MinIO（文章配图、封面与头像上传）

## 技术栈

- **前端（cloudlog_web/）**：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、Axios、Sass、Vditor（编辑）、md-editor-v3（预览）、dayjs（时间格式化）、nprogress（导航进度条）、qrcode-generator（分享二维码本地生成）、@fontsource（自托管字体）
- **后端（cloudlog_api/）**：Java 21、Spring Boot 3.5、Spring Security + JWT、MyBatis + PageHelper、MySQL 8、Redis、MinIO、SpringDoc OpenAPI、JavaMail（邮箱验证码与找回密码）、Hutool（雪花 ID）

## 快速开始

### 1. 准备依赖服务

- MySQL 8.0+
- Redis 6.0+（只放验证码、频控与阅读去重这类短时键，空实例即可，无需初始化数据）
- MinIO（仅图片上传需要，可选）

建库并导入表结构，在仓库根目录执行：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS cloudlog DEFAULT CHARACTER SET utf8mb4"
mysql -u root -p cloudlog < docs/schema.sql
```

库名默认 `cloudlog`，与 `application.example.yml` 的数据源 URL 一致。`docs/schema.sql` 是数据库唯一真源，最终态全量 DDL（`posts` 的 ngram 全文索引已在建表语句里）；结构变更直接改该文件并清库重建，不保留面向存量库的增量升级脚本。

需要演示数据时（可选）：

```bash
mysql -u root -p cloudlog < docs/data.sql
```

`docs/data.sql` 是一份自包含的演示/测试数据集（11 张表共 339 行）：23 个用户（2 个 admin + 21 个普通用户，含 1 个禁用账号）、28 篇覆盖四态（draft / published / scheduled / offline）与置顶、未分类、多作者的中文技术文章，以及三态评论（含两级回复与匿名评论）、点赞、访问日志、关注关系与违禁词库。所有账号密码均为 `123456`，博主账号 `tcloud@example.com`。其中两个小 ID 账号（`author@example.com`、`alex@example.com`）是为 mapper 层与过滤链测试写死的取值补的锚点，理由写在文件内 §12 的注释里。它只含 INSERT（事务包裹，一次成功或整体回滚），不建表也不删表——必须在 `schema.sql` 之后、且只在空库上导入一次，重复执行会主键冲突；结构变更后需重新生成该文件。

### 2. 后端（cloudlog_api/）

```bash
cd cloudlog_api
cp src/main/resources/application.example.yml src/main/resources/application.yml
# 编辑 application.yml，填入数据库/Redis/邮箱密码与 JWT 密钥
mvn spring-boot:run
```

- 启动后接口文档：`http://localhost:8080/api/v1/swagger-ui.html`

### 3. 前端（cloudlog_web/）

环境要求：Node.js ^20.19.0 || >=22.12.0

```bash
cd cloudlog_web
cp .env.example .env
npm install
npm run dev
```

- 开发模式下通过 Vite 代理将 `/api` 请求转发至后端，代理目标取自 `cloudlog_web/.env` 的 `VITE_APP_API_BASE_URL`（模板值 `http://127.0.0.1:8080`）
- 生产构建：`npm run build`；代码检查：`npm run lint`；类型检查：`npm run type-check`
- 起好后前台在 `http://localhost:5173`（Vite 缺省端口，`vite.config.ts` 未覆写），管理后台在 `/admin`（只放 `admin` 角色进，提升方法见下一步）

### 4. 注册账号并提升为首任博主

打开前台注册一个账号，再把它提成博主。这一步是必需的：注册接口固定写 `role = 'user'`（`AuthService.java:96`），后台的用户管理只有列表与启用/禁用两项、改不了角色（`AdminUserController`），`docs/schema.sql` 也不预置任何账号——所以首个以及此后每一个管理员都要这样手工提升，界面上没有入口。（例外：若第 1 步导入了演示数据 `docs/data.sql`，库里已有现成博主 `tcloud@example.com`，密码 `123456`，本步可跳过。）

```bash
mysql -u root -p cloudlog -e "UPDATE users SET role = 'admin' WHERE email = '你的注册邮箱'"
```

还没配真实 SMTP 时收不到验证码邮件——码只写 Redis、不打日志，5 分钟过期、校验通过即销毁。点过「发送验证码」之后直接把它读出来：

```bash
redis-cli GET "auth:code:你的注册邮箱"
```

### 5. SEO 产物与反向代理

`robots.txt`、`sitemap.xml`、`feed.xml` 由后端直出，但应用整体挂在 `server.servlet.context-path: /api/v1` 之下，实际地址是 `/api/v1/robots.txt` 这一组。爬虫只认**域名根**上的 `robots.txt`，因此前端由 nginx 托管时要精确放行这三条（其余路径照旧给静态文件）：

```nginx
location = /robots.txt  { proxy_pass http://127.0.0.1:8080/api/v1/robots.txt; }
location = /sitemap.xml { proxy_pass http://127.0.0.1:8080/api/v1/sitemap.xml; }
location = /feed.xml    { proxy_pass http://127.0.0.1:8080/api/v1/feed.xml; }

location / {
    try_files $uri $uri/ /index.html; # 单页应用的前端路由兜底
}
```

三条都必须用 `location =` 精确匹配：SPA 的 `try_files` 会把任何未命中路径都回落到 `index.html`，用前缀匹配的话爬虫拿到的是一份 HTML，等于一份「谁都不许抓」的空 robots 规则。

填好后到后台「系统设置 → 站点信息 → 站点域名」写一次对外地址（如 `https://你的域名`，末尾不必带斜杠）：sitemap 的 `<loc>`、RSS 的 `link`、robots 的 `Sitemap:` 行都由它拼。留空时按当次请求的 Host 兜底，本机自测够用，正式上线不该依赖它——那个值会随请求头变。

容器化部署时这三条已经在 `cloudlog_web/nginx.conf` 里落地，与上面的片段有两处必要差别：后端不是 `127.0.0.1:8080` 而是 compose 网络里的服务名 `api`；转发头用 `Host $http_host` 而不是 `$host`，后者会把端口丢掉，`WEB_PORT` 不是 80 时兜底出来的地址就打不开。

## 容器化部署（自托管一键起）

对应 `docs/business-model.md` §3.3「自托管部署」。假设一台装了 Docker（含 compose 子命令）的机器、单实例（§5.7 第 5 条），没有 K8s、没有镜像仓库推送、没有多副本。

### 步骤

```bash
cp .env.example .env    # 逐项填写：凭据只在这里，仓库里只留模板
docker compose up -d --build
```

MySQL 容器首次起跑会把 `docs/schema.sql` 导进新建的空库（只在数据目录为空时执行，真源不变）；演示数据 `docs/data.sql` 不在编排挂载里，需要时参照快速开始第 1 步手工导入。`api` 等 MySQL 与 Redis 变 healthy 才起跑，`web` 等 `api` 变 healthy 才起跑。站点入口只有 `web` 的 `${WEB_PORT:-80}` 一个端口，后端 8080 不对外发布。

注册账号并提升为首任博主的口径与上面第 4 步一样（界面上没有改角色的入口），只是命令换成走容器：

```bash
docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" \
  -e "UPDATE users SET role = 'admin' WHERE email = '你的注册邮箱'"
```

没配真实 SMTP 时按第 4 步那样从 Redis 读验证码：`docker compose exec -T redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning GET "auth:code:你的邮箱"`。后端是无条件装配 `JavaMailSender` 的，所以 `SMTP_*` 留空会让容器起不来；填占位值则发码接口报错、但验证码已先写进 Redis，注册照样能完成。

验收走 `./deploy/verify-deploy.sh`：注册 → 发文 → 详情可读 → 三份 SEO 产物在域名根可达 → 自行清理容器与数据卷（`--keep` 保留现场）。

### 配置怎么进容器

- 镜像里既没有凭据也没有 `application.yml`（它不入库，也被 `cloudlog_api/.dockerignore` 挡在构建上下文之外）。
- `deploy/api/application-prod.yml` 挂到 `/app/config/application-prod.yml`，与 jar 内同名文件**叠加**：本文件补齐「没有 `application.yml` 时会缺」的结构键，jar 内那份的三项收紧（SpringDoc 关闭、actuator 只留 `health`、日志压到 info 并落文件）照旧生效。
- 为什么不干脆全部写成环境变量：`pagehelper.params=count=countSql` 这类标量值经环境变量传入会被当成字符串、绑不进 Map，启动即 `BindException`（以 Spring Boot 3.5.14 的 Binder 实测）。
- **改 `application.example.yml` 的结构键要同步 `deploy/api/application-prod.yml`**，数据源 URL 上那组时区参数尤其要紧（口径与回归见 `DataSourceTimeZoneTest`）。

### 四条差别与代价

1. **来源地址**：`APP_TRUST_FORWARDED_FOR` 在容器档里默认 `true`，前提是 api 的 8080 不对外发布、且 nginx 以覆盖方式写 `X-Forwarded-For`（两处配置都是这么写的）。一旦你把 8080 发布出去或改成追加式代理，必须改回 `false`，否则登录爆破计数与发码日额度就成了「换一个头换一个额度」。
2. **时区**：镜像的 `JAVA_OPTS` 里钉了 `-Duser.timezone=Asia/Shanghai`。库里存的是北京墙钟，而评论频控、日志保留、SEO 产物这三处仍按 JVM 默认时区取「现在」（`business-model.md` §6 未决项），容器默认起在 UTC 就是静默偏 8 小时。
3. **Redis 不留盘**：编排里关掉 RDB/AOF，与「只放短时键、空实例即可」一致。但口令重置与登出推的 token 失效水位也在 Redis（§5.5），**重建 Redis 容器会让那批本该作废的旧 token 复活**；要在重启后仍保水位，就改 `command` 开 AOF 并给它加卷。
4. **MinIO 不在默认起跑清单里**：2026-09 起 MinIO 停发免费 Docker 镜像（实测 Docker Hub 的 `minio/minio`、`minio/mc` 已 404，`quay.io/minio/minio` 匿名拉取 401），所以它排在 `--profile images` 后面，且镜像名由 `.env` 的 `MINIO_IMAGE` 提供——自备一个（自己构建，或可信镜像源里的同名镜像）再 `docker compose --profile images up -d`。图片链路另有两件仍是手工事：① 桶要设成匿名只读（后端只建桶、不设策略，控制台 9001 里点，或 `mc anonymous set download`）；② `MINIO_ENDPOINT` 会被拼进落库的图片绝对地址，**换值要回刷库**（`docs/db-design.md` §5.5），本机自测想让浏览器和后端都够得着它，得给一个两边都能解析的名字，正式上线填真实公网域名就天然一致。

## 配置与敏感信息约定

配置文件本体（含真实密码/密钥）不提交，Git 中只保留 `*.example` 模板和不含任何凭据的 `application-prod.yml`：

```text
cloudlog_web/
├── .env.example                # 提交（模板）
└── .env                        # 不提交（真实配置）

cloudlog_api/src/main/resources/
├── application.example.yml     # 提交（模板，密码/密钥均为占位符）
├── application.yml             # 不提交（真实配置，含密码/密钥）
└── application-prod.yml        # 提交（生产收紧覆盖，无凭据）

仓库根/
├── .env.example                # 提交（容器部署的凭据模板）
├── .env                        # 不提交（docker compose 读取的真实凭据）
└── deploy/api/
    └── application-prod.yml    # 提交（容器档的结构键 + ${ENV} 占位，无凭据）
```

三者分工：

- `application.example.yml`：入库模板。克隆后复制为 `application.yml`，填入真实凭据。
- `application.yml`：本地/服务器真实配置，不入库。数据源、Redis、SMTP、MinIO 凭据只写在这里。
- `application-prod.yml`：入库的 profile 覆盖文件，只含安全收紧项（关闭 SpringDoc 的 `/v3/api-docs`、`/swagger-ui`，actuator 仅暴露 `health`），不含任何密钥。生产启动必须带上 profile：

```bash
cd cloudlog_api
mvn clean package -DskipTests
java -jar target/*.jar --spring.profiles.active=prod
```

未指定 profile 时走默认配置，接口文档与 actuator 端点保持开放，收紧只在 `prod` 下生效。

容器化部署走的是同一条 `prod` 口径，只是凭据来源换成仓库根的 `.env`、`application.yml` 不进镜像，改由 `deploy/api/application-prod.yml` 挂载补齐结构键——细节与「为什么不是全部用环境变量」写在上面的容器化部署一节。

## 数据备份（必做）

文章内容只存在于 MySQL，`LogRetentionService` 只负责清日志、不负责备数据——库损坏或误删等于内容全失。至少按天做一次逻辑备份：

```bash
# 手动备份（保留 14 天滚动）
mkdir -p /var/backups/cloudlog
mysqldump -u root -p'你的数据库密码' --single-transaction --routines cloudlog \
  | gzip > /var/backups/cloudlog/cloudlog-$(date +%F).sql.gz
find /var/backups/cloudlog -name '*.sql.gz' -mtime +14 -delete
```

把这两行挂进 crontab（每天 02:30，避开 03:30 的日志清理任务）：

```cron
30 2 * * * mysqldump -u root -p'你的数据库密码' --single-transaction cloudlog | gzip > /var/backups/cloudlog/cloudlog-$(date +\%F).sql.gz
30 2 * * * find /var/backups/cloudlog -name '*.sql.gz' -mtime +14 -delete
```

`--single-transaction` 保证 InnoDB 下备份不锁表；恢复：`gunzip < 备份文件 | mysql -u root -p cloudlog`。MinIO 里的图片建议按 bucket 周期快照或双桶镜像，量小可接受时也可与 `mysqldump` 同频用 `mc mirror` 同步。

## 持续集成

`.github/workflows/ci.yml` 在每次 push/PR 时跑两件事：后端全量测试（临时 MySQL + Redis 服务容器，凭据经环境变量覆盖）、前端 type-check 与构建。合并前绿灯是硬门槛——本仓有 300+ 后端测试，本地跑一遍不便宜，交给 CI 兜底。
