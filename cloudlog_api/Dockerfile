# syntax=docker/dockerfile:1
# ============================================================
# CloudLog 后端镜像：Maven 构建 → JRE 21 运行时。
#
# 镜像里没有任何凭据：application.yml 不入库（.gitignore），也被 .dockerignore 挡在构建上下文之外，
# 所以运行期配置全部来自「挂载进来的 deploy/api/application-prod.yml + 仓库根 .env 注入的环境变量」。
# 单实例假设不变（business-model.md §5.7 第 5 条），不做镜像仓库推送与多副本编排。
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
# 容器里没有宿主 ~/.m2 的缓存，整棵依赖树要现下；直连中央仓库实测约 95 KB/s 且会把 2 MB 的 jar
# 截断，构建失败在依赖解析上，所以显式换一个国内源（理由与实测数字写在该文件头部注释里）
COPY settings.xml ./
COPY src ./src
# 测试要外部 MySQL/Redis（business-model.md §3.3「自动化测试」），回归由 .github/workflows/ci.yml 兜底，
# 构建阶段只打包。
# --mount=type=cache 让依赖跨构建复用（只有 BuildKit 支持这一行；compose 在 Docker Desktop 上默认就是
# BuildKit，若改用经典构建器这行会以 Unknown flag: mount 失败）；缓存卷是空目录起步，所以 settings.xml
# 用 -s 显式指，不能指望 ~/.m2 里那份
RUN --mount=type=cache,target=/root/.m2 mvn -B -s ./settings.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime

# -Duser.timezone：库里存的是北京墙钟（db-design.md §1），而评论频控、日志保留、SEO 产物这三处
# 仍按 JVM 默认时区取「现在」。容器默认起在 UTC，不钉住就是静默偏 8 小时
# ENV 而非写死在 ENTRYPOINT 里，便于按机器覆盖
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Shanghai" \
    SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/app/config/

# 非 root 运行；logs 目录归它，prod 配置里的 logging.file.name 是相对进程工作目录的 logs/cloudlog.log
RUN addgroup -S cloudlog && adduser -S cloudlog -G cloudlog
WORKDIR /app
COPY --from=build --chown=cloudlog:cloudlog /src/target/CloudLog-*.jar app.jar
RUN mkdir -p /app/logs && chown cloudlog:cloudlog /app/logs
USER cloudlog

EXPOSE 8080

# 健康检查走 prod 下唯一放行的 actuator 端点（business-model.md §3.3「运维面收敛」）；
# alpine 自带 busybox 的 wget，不必额外装 curl
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/api/v1/actuator/health | grep -q '"status":"UP"' || exit 1

# profile 由命令行参数注入，与 README「生产启动必须带上 profile」同一条口径；
# sh -c + exec 让 java 顶替 shell 进程，SIGTERM 能直达 JVM 走优雅停机
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=prod"]
