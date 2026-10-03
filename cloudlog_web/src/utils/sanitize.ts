import DOMPurify from 'dompurify'

/**
 * Markdown 渲染产物的统一清洗。md-editor 的 markdown-it 开着 html:true，
 * 正文里的内嵌 HTML 会原样进 DOM。当前写入口只限管理员，但渲染前统一过一遍
 * DOMPurify 白名单：后端一旦放开普通用户写作、或内容经其他渠道入库，
 * 就不会升级成存储型 XSS（<script>、onerror 之类一律剪掉）
 */
export const sanitizeHtml = (html: string) => DOMPurify.sanitize(html)
