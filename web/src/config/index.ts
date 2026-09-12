/**
 * 项目全局配置项
 * 采用统一的 VITE_APP_ 前缀规范，方便维护和管理
 */

const env = import.meta.env

export const config = {
  // 后端接口基础地址
  apiBaseUrl: env.VITE_APP_API_BASE_URL || 'http://127.0.0.1:8080',
  
  // MinIO 相关配置
  minio: {
    endpoint: env.VITE_APP_MINIO_ENDPOINT || 'http://127.0.0.1:9005',
    bucket: env.VITE_APP_MINIO_BUCKET || 'cloud-blog',
    // 拼接后的 MinIO 基础访问地址：endpoint + bucket
    get baseUrl() {
      return `${this.endpoint}/${this.bucket}`
    }
  }
}

/**
 * 格式化 MinIO 资源地址
 * @param path 资源相对路径 (如: /avatars/user1.png)
 * @returns 完整的资源访问地址
 */
export const formatMinioUrl = (path: string): string => {
  if (!path) return ''
  // 如果已经是完整路径则直接返回
  if (path.startsWith('http') || path.startsWith('https')) {
    return path
  }
  // 确保路径以 / 开头
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${config.minio.baseUrl}${normalizedPath}`
}

export default config
