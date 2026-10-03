/** 评论者信息（后端评论装载的关联字段，仅 id / 用户名 / 头像） */
export interface CommentUser {
  /** 用户雪花 ID */
  id: string
  username: string
  avatar: string | null
}

/** 评论实体（对应后端 Comment，含关联装载字段） */
export interface Comment {
  id: number
  /** 雪花 ID：字符串透传，不做数值运算 */
  postId: string
  content: string
  /** `0` = 根评论，否则为父评论 id */
  parentId: number
  status: string
  createdAt: string
  user: CommentUser | null
  /** 后端按「根 + 其直接回复」装载，回复挂在根评论上 */
  replies?: Comment[] | null
}

/** 提交评论入参；评论者身份由服务端从登录态回填，客户端不传 */
export interface AddCommentPayload {
  /** 文章雪花 ID，字符串透传 */
  postId: string
  content: string
  parentId: number
}
