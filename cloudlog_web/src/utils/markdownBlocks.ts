import MarkdownIt from 'markdown-it'

const md = new MarkdownIt()

/** 一行内可能有多个 `<br>`，也可能整段只有一行却塞了两个 `<br>` */
const HAS_BREAK = /\n|<br\s*\/?>/i
/** 数学块在基础解析器眼里就是普通段落，插空行会把公式劈坏 */
const MATH_FENCE = /^\s*(\$\$|\\\[|\\\])\s*$/m

/**
 * 把「一块塞多行」的正文规整成一空行一块。
 *
 * Vditor IR 的标题命令只认所在块（ir/process.ts 的 processHeading 往块首插 `## `），
 * 而 Markdown 的单换行、行尾硬换行、`<br>` 都仍属同一个块 —— 于是给其中一行设标题，
 * 会把整块的首行变成标题，看起来像凭空多出一个换行。
 * 用与 MdPreview 同一套解析器判定块，规整后「视觉行 = 块」在编辑器与正文两侧同时成立。
 *
 * 只动顶层段落块：列表项、引用块内部的续行不动（拆开会改变紧凑/松散的渲染），
 * 代码块、表格、标题等由解析器分给别的 token，天然不受影响。
 */
export const splitParagraphLines = (source: string): string => {
  if (!source) return source

  const lines = source.split('\n')
  const ranges: [number, number][] = []
  for (const token of md.parse(source, {})) {
    const span = token.map
    if (token.type !== 'paragraph_open' || token.level !== 0 || !span) continue
    const body = lines.slice(span[0], span[1]).join('\n')
    if (!HAS_BREAK.test(body) || MATH_FENCE.test(body)) continue
    ranges.push([span[0], span[1]])
  }
  if (!ranges.length) return source

  const out: string[] = []
  let cursor = 0
  // ranges 按 token 顺序收集，起点天然递增，可顺序缝合
  for (const [start, end] of ranges) {
    if (start > cursor) out.push(lines.slice(cursor, start).join('\n'))
    out.push(
      lines
        .slice(start, end)
        .flatMap((line) => line.split(/<br\s*\/?>/i))
        // 行尾反斜杠也是硬换行标记；拆开后块边界即换行，留着会多出一个字符
        .map((segment) => segment.replace(/^\s+|[\s\\]+$/g, ''))
        .filter(Boolean)
        .join('\n\n'),
    )
    cursor = end
  }
  if (cursor < lines.length) out.push(lines.slice(cursor).join('\n'))
  return out.join('\n')
}
