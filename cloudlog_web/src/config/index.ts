/**
 * 项目全局配置项
 * 采用统一的 VITE_APP_ 前缀规范，方便维护和管理
 */

const env = import.meta.env

export const config = {
  // MinIO 相关配置（默认值与后端配置模板 application.example.yml 的 minio.bucketName 对齐）
  minio: {
    endpoint: env.VITE_APP_MINIO_ENDPOINT || 'http://127.0.0.1:9000',
    bucket: env.VITE_APP_MINIO_BUCKET || 'cloudlog',
    // 拼接后的 MinIO 基础访问地址：endpoint + bucket
    get baseUrl() {
      return `${this.endpoint}/${this.bucket}`
    },
  },
}

/**
 * 格式化 MinIO 资源地址
 * @param path 资源相对路径 (如: /avatars/user1.png)
 * @returns 完整的资源访问地址
 */
export const formatMinioUrl = (path: string | null | undefined): string => {
  if (!path) return ''
  // 如果已经是完整路径则直接返回
  if (path.startsWith('http') || path.startsWith('https')) {
    return path
  }
  // 确保路径以 / 开头
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${config.minio.baseUrl}${normalizedPath}`
}

/** 兜底头像是随前端发布的静态资源，不经对象存储，故不套 formatMinioUrl */
export const defaultAvatarUrl = '/default-avatar.svg'

export default config
