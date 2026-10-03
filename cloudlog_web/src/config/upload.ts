/** 前端上传体积上限（后端 spring.servlet.multipart 总上限 10MB，这里的值必须 ≤ 它）。
 *  正文配图 / 封面 / 头像三处入口共用一份口径，不再各写各的数字 */
export const UPLOAD_LIMITS = {
  /** 文章正文配图 */
  articleImage: 5 * 1024 * 1024,
  /** 文章封面 */
  cover: 5 * 1024 * 1024,
  /** 用户头像 */
  avatar: 10 * 1024 * 1024,
} as const

/** 是否图片文件。accept 属性只是文件选择器的提示，拖拽与改名文件绕得过，MIME 必须显式判 */
export const isImageFile = (file: File) => file.type.startsWith('image/')
