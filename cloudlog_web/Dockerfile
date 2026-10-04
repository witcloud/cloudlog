# syntax=docker/dockerfile:1
# ============================================================
# CloudLog 前端镜像：Node 构建 → nginx 托管静态产物。
# 浏览器侧接口请求走相对路径 /api/v1，由同源的 nginx 反代给后端（cloudlog_web/nginx.conf），
# 所以构建阶段不需要知道后端地址，产物里也不写任何凭据。
# ============================================================

FROM node:22-alpine AS build
WORKDIR /src
# 依赖层与源码层分开：改代码不重装依赖。engines 要求 Node ^20.19.0 || >=22.12.0
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# 这两项只给「库里存过相对路径」的历史数据兜底（config/index.ts 的 fallback），
# 新上传的头像/封面由后端返回绝对地址。默认值与 application.example.yml 的 minio 段对齐
ARG VITE_APP_MINIO_ENDPOINT=http://minio:9000
ARG VITE_APP_MINIO_BUCKET=cloudlog
ENV VITE_APP_MINIO_ENDPOINT=$VITE_APP_MINIO_ENDPOINT \
    VITE_APP_MINIO_BUCKET=$VITE_APP_MINIO_BUCKET

# npm run build = type-check + vite build（与 CI 的 frontend job 同口径），并触发
# vite.config.ts 里的 vditor 本地 CDN 拷贝，编辑器的 lute/highlight/i18n 资源随之进 dist
RUN npm run build

FROM nginx:1.28-alpine AS runtime
# 覆盖默认站点配置：三条 SEO 精确映射、SPA 回退、/api/v1 反代都在这里
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html

EXPOSE 80

# 只探测 nginx 自身能否出静态首页；后端是否就绪由 api 容器的 healthcheck 与 depends_on 负责
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/index.html >/dev/null || exit 1
