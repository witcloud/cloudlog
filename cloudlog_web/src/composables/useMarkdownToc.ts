import { onBeforeUnmount, ref, watch, type Ref } from 'vue'

export interface TocItem {
  key: string
  id: string
  text: string
  level: number
  index: number
}

interface UseMarkdownTocOptions {
  /** Markdown 原文：容器里还没有渲染标题时（vditor 初始化前、md-editor 渲染间隙）的推导来源 */
  source: Ref<string>
  /** DOM 标题级别相对源码级别的偏移。详情页渲染链把标题整体下移一级取 1；
      原级映射取 0——vditor 编辑区与编辑器预览的 MdPreview 都按 `#` → h1 原级渲染 */
  levelOffset?: number
  /** 每次收集 DOM 前的宿主钩子。详情页借它把渲染标题整体下移一级，
      保证任何时刻（含延迟到期的兜底收集）收到的都是归位后的级别 */
  beforeCollect?: (container: HTMLElement) => void
}

const HEADING_SELECTOR = 'h1, h2, h3, h4, h5, h6'

/**
 * 逐行扫源码推导目录（ArticleDetail 的既有口径迁移）：
 * 围栏（``` / ~~~）内的 `#` 是注释不是标题；`##` 后无正文的空标题被 (.+) 天然挡掉；
 * id 只保证「同名同文」时可拼，key 追加序号保证同名标题条目唯一
 */
export const deriveTocFromSource = (content: string, levelOffset: number): TocItem[] => {
  if (!content) return []

  const headings: TocItem[] = []
  const reg = /^(#+)\s+(.+)$/
  let inFence = false

  for (const line of content.split('\n')) {
    if (/^(?:```|~~~)/.test(line.trimStart())) {
      inFence = !inFence
      continue
    }
    if (inFence) continue

    const match = reg.exec(line)
    if (!match) continue

    // 偏移对齐 DOM 那条来源：两边级别口径一致，TocSidebar 的相对缩进才统一
    const level = (match[1] ?? '').length + levelOffset
    const text = match[2] ?? ''
    const id = text.toLowerCase().replace(/[^\w\u4e00-\u9fa5]+/g, '-')
    const index = headings.length
    headings.push({ key: `${id}-${index}`, id, text, level, index })
  }

  return headings
}

/**
 * Markdown 目录：DOM 优先（containerRef 内的渲染标题，同名标题按序号区分），
 * 源码推导兜底。编辑器与详情页共用；目录一律实时推导，不入库。
 */
export function useMarkdownToc(options: UseMarkdownTocOptions) {
  const { source, levelOffset = 0, beforeCollect } = options

  /** 宿主绑定的渲染容器：编辑/预览等形态切换时换绑后调 refresh() */
  const containerRef = ref<HTMLElement | null>(null)
  const tocItems = ref<TocItem[]>([])
  const activeIndex = ref<number | null>(null)

  /** 当前命中的标题元素。scrollTo 与高亮判定都持有引用走，不走 getElementById：
      同名标题会撞出重复 id，永远只能跳到第一个 */
  let headingEls: HTMLElement[] = []
  /** 判定线：取首个标题的 scroll-margin-top 计算值，与点击落点共用同一个数 */
  let readingLine = 0

  const collectFromDom = (): boolean => {
    const container = containerRef.value
    if (!container) return false
    beforeCollect?.(container)

    const els = Array.from(container.querySelectorAll<HTMLElement>(HEADING_SELECTOR))
    const first = els[0]
    if (!first) return false

    headingEls = els
    readingLine = Number.parseFloat(getComputedStyle(first).scrollMarginTop) || 0
    tocItems.value = els.map((h, index) => {
      // vditor IR 的 `#` 标记 span 挂在 hN 内部，读文字前剥掉（md-preview 无标记，剥除是空操作）
      const clone = h.cloneNode(true) as HTMLElement
      clone.querySelectorAll('.vditor-ir__marker').forEach((marker) => marker.remove())
      return {
        key: `${h.id || 'toc'}-${index}`,
        id: h.id,
        text: clone.textContent?.trim() ?? '',
        level: Number(h.tagName.slice(1)),
        index,
      }
    })
    return true
  }

  const collect = () => {
    if (!collectFromDom()) {
      headingEls = []
      readingLine = 0
      tocItems.value = deriveTocFromSource(source.value, levelOffset)
    }
    updateActive()
  }

  // ---- 高亮跟随：判定线 + window scroll（编辑器页与详情页的滚动都发生在 window）----
  // 落点按 devicePixelRatio 吸附到半像素（dpr 2 实测偏 0.1px），不留容差会亮在前一条
  const TOC_LINE_TOLERANCE = 1

  /** 目录点击发起的平滑滚动是否还在途中。滚动动画沿途的标题会逐条命中判定线，不锁住高亮就被
      逐帧改写（实测点末条：先亮目标 → 下一帧整条熄灭 → 0…5 扫一遍 → 才落回目标）。
      交还时机用 scrollend，被用户打断后再次停住也算；不支持的浏览器不锁，退回逐帧跟随 */
  const hasScrollEnd = 'onscrollend' in window
  let jumping = false

  const updateActive = () => {
    if (jumping) return
    if (!headingEls.length) {
      activeIndex.value = null
      return
    }
    const line = readingLine + TOC_LINE_TOLERANCE
    let current: number | null = null
    headingEls.forEach((h, i) => {
      if (h.getBoundingClientRect().top <= line) current = i
    })
    activeIndex.value = current
  }

  let spyFrame = 0
  const onSpyScroll = () => {
    if (spyFrame) return
    spyFrame = requestAnimationFrame(() => {
      spyFrame = 0
      updateActive()
    })
  }

  const onScrollEnd = () => {
    jumping = false
  }

  /** 挂载滚动跟随；宿主按需懒挂载（如编辑器大纲 tab 激活才开），不给写作态添常驻开销 */
  const startFollow = () => {
    window.addEventListener('scroll', onSpyScroll, { passive: true })
    if (hasScrollEnd) window.addEventListener('scrollend', onScrollEnd)
    updateActive()
  }

  const stopFollow = () => {
    window.removeEventListener('scroll', onSpyScroll)
    window.removeEventListener('scrollend', onScrollEnd)
    if (spyFrame) {
      cancelAnimationFrame(spyFrame)
      spyFrame = 0
    }
  }

  /** 点击目录项：置高亮并滚到对应标题。推导态没有元素引用才退回 id 定位
      （同名标题会落到第一个，可接受的兜底） */
  const scrollTo = (index: number) => {
    activeIndex.value = index
    const heading = headingEls[index]
    if (heading) {
      // 目标已经停在判定线上时不产生位移，也就等不到 scrollend（实测），这种情况不加锁
      const landsAtLine =
        Math.abs(heading.getBoundingClientRect().top - readingLine) <= TOC_LINE_TOLERANCE
      if (hasScrollEnd && !landsAtLine) jumping = true
      heading.scrollIntoView({ behavior: 'smooth' })
      return
    }
    document.getElementById(tocItems.value[index]?.id ?? '')?.scrollIntoView({ behavior: 'smooth' })
  }

  let trailingTimer: ReturnType<typeof setTimeout> | null = null

  /** 重新收集。渲染链的 DOM 更新可能晚于 source（md-editor-v3 落在下一个宏任务），
      落一拍再收一次兜底；连续输入时旧定时器作废，天然只在停笔后补一拍 */
  const refresh = () => {
    collect()
    if (trailingTimer) clearTimeout(trailingTimer)
    trailingTimer = setTimeout(collect, 300)
  }

  watch(source, () => refresh())

  onBeforeUnmount(() => {
    stopFollow()
    if (trailingTimer) clearTimeout(trailingTimer)
  })

  return { tocItems, activeIndex, containerRef, refresh, scrollTo, startFollow, stopFollow }
}
