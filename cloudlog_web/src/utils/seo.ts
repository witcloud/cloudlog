import dayjs from 'dayjs'
import { useSiteStore } from '@/stores/site'

/**
 * 页面标题的唯一拼法：带页面名时挂站点名后缀，不带时只留站点名。
 * 站点名取自 siteStore，公开设置未就位时是内置兜底名（index.html 的静态 <title> 兜的是同一件事）
 */
export const resolveTitle = (title?: string) => {
  const { siteName } = useSiteStore()
  return title ? `${title} - ${siteName}` : siteName
}

/**
 * 站点级描述的原文，模块加载时从 index.html 的静态 meta 上取一次，路由复位时写回它。
 * 不在这里重抄一遍文案——同一句话在 html 与 ts 里各存一份，改一处就漂
 */
const DEFAULT_DESCRIPTION =
  document.querySelector('meta[name="description"]')?.getAttribute('content') ?? ''

/**
 * 由本文件在运行时写上去的 og / twitter 标签，index.html 里没有，复位时整枚摘掉而不是留个空壳。
 * og:image 与 og:site_name 是这一轮补的：社交卡片没有图就没有点击，站点名缺失时卡片不知道来自哪儿
 */
const OG_PROPERTIES = [
  'og:title',
  'og:description',
  'og:type',
  'og:url',
  'og:image',
  'og:site_name',
]

/** twitter 卡片用的是 name= 而不是 property=，摘除时选择器不同，不能混进上面那组 */
const TWITTER_NAMES = ['twitter:card']

/** article:* 只有文章页写，其中 article:tag 一枚标签一条，复位要按前缀整批清 */
const ARTICLE_PROPERTY_PREFIX = 'article:'

/** JSON-LD 与 canonical 不是 meta，摘除时各走自己的选择器；脚本靠这个标记认出是本站注入的 */
const JSON_LD_SELECTOR = 'script[data-seo-jsonld]'

const upsertMeta = (attr: 'name' | 'property', key: string, content: string) => {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute(attr, key)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

/** 同名可多枚的标签（article:tag）：先按 property 清空再逐条写，留着上一条就是换篇文章后标签还在 */
const replaceMetaAll = (property: string, values: string[]) => {
  document.head.querySelectorAll(`meta[property="${property}"]`).forEach((el) => el.remove())
  values.forEach((value) => {
    const el = document.createElement('meta')
    el.setAttribute('property', property)
    el.setAttribute('content', value)
    document.head.appendChild(el)
  })
}

/**
 * 本页的规范地址：协议 + 主机 + 路由路径，**不含查询参数**。
 * 同一篇文章的 ?from=rss、?utm_source=… 与分页形态都归一到同一个 URL，权重不再被参数切开。
 * 前端路由是 history 模式，路径本身就是可寻址的页面
 */
export const canonicalUrl = () => window.location.origin + window.location.pathname

const upsertCanonical = (href: string) => {
  let link = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
  if (!link) {
    link = document.createElement('link')
    link.rel = 'canonical'
    document.head.appendChild(link)
  }
  link.href = href
}

/**
 * 后端下发的时刻是 "yyyy-MM-dd HH:mm:ss"（GMT+8），而 article:published_time 与 JSON-LD 的
 * datePublished 要的是 ISO 8601。meta 与脚本两处换算必须同一个函数，否则同页两个时刻
 */
export const toIso8601 = (moment: string) => dayjs(moment).toISOString()

/**
 * 写这一页的 SEO 元信息。
 * title 是**最终**的 document.title：文章页传后端合成的 seoTitle（「文章标题 - 站点名」），
 * 其余页面传 resolveTitle(页面名)。
 * description / keywords 原样落 meta，空值不报错：空描述写空串，空关键词干脆不写那枚标签。
 * image 传封面的完整地址（库里存的就是上传时拼好的绝对 URL），没有封面就回落到随发布的图标——
 * 卡片没有图等于没有卡片。有图才配 summary_large_image，无图给 large 会渲染成空白块。
 * structuredData 只给文章详情页用，非文章页不注入 JSON-LD
 */
export const setSeoMeta = ({
  title,
  description,
  keywords,
  image,
  publishedTime,
  tags,
  structuredData,
}: {
  title?: string | null
  description?: string | null
  keywords?: string | null
  image?: string | null
  publishedTime?: string | null
  tags?: string[] | null
  structuredData?: Record<string, unknown> | null
}) => {
  const page = title || resolveTitle()
  const cover = image || window.location.origin + '/apple-touch-icon.png'
  const canonical = canonicalUrl()

  document.title = page

  upsertMeta('name', 'description', description ?? '')
  if (keywords) {
    upsertMeta('name', 'keywords', keywords)
  }

  upsertMeta('property', 'og:title', page)
  upsertMeta('property', 'og:description', description ?? '')
  upsertMeta('property', 'og:type', 'article')
  upsertMeta('property', 'og:url', canonical)
  upsertCanonical(canonical)
  upsertMeta('property', 'og:image', cover)
  upsertMeta('property', 'og:site_name', useSiteStore().siteName)
  upsertMeta('name', 'twitter:card', image ? 'summary_large_image' : 'summary')

  if (publishedTime) {
    upsertMeta('property', 'article:published_time', toIso8601(publishedTime))
  }
  if (tags?.length) {
    replaceMetaAll('article:tag', tags)
  }

  if (structuredData) {
    const script = document.createElement('script')
    script.type = 'application/ld+json'
    script.dataset.seoJsonld = 'true'
    // 标题里出现 </script> 就能把这段 JSON 提前结束、后面接上任意脚本，
    // 所以把 < 转成 JSON 自带的 \u003c 写法：语义不变，注入面封掉
    script.textContent = JSON.stringify(structuredData).replace(/</g, '\\u003c')
    document.head.querySelector(JSON_LD_SELECTOR)?.remove()
    document.head.appendChild(script)
  }
}

/**
 * 路由切换时把 head 复位到「没有文章」的状态：描述回站点默认，运行时写上去的
 * keywords / og / twitter / article:* / canonical / JSON-LD 全部摘掉。
 * 少了这一步，从文章页回到首页时 meta description 还挂着上一篇文章的摘要，
 * canonical 与 og:url 会把读者带向上一篇——它们指向的是「这一篇」，跨页留着就是错的
 */
export const resetSeoMeta = () => {
  upsertMeta('name', 'description', DEFAULT_DESCRIPTION)
  document.head.querySelector('meta[name="keywords"]')?.remove()
  OG_PROPERTIES.forEach((property) => {
    document.head.querySelector(`meta[property="${property}"]`)?.remove()
  })
  TWITTER_NAMES.forEach((name) => {
    document.head.querySelector(`meta[name="${name}"]`)?.remove()
  })
  document.head.querySelectorAll(`meta[property^="${ARTICLE_PROPERTY_PREFIX}"]`).forEach((el) => el.remove())
  document.head.querySelector('link[rel="canonical"]')?.remove()
  document.head.querySelector(JSON_LD_SELECTOR)?.remove()
}
