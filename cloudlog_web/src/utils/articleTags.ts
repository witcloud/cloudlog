import { getTags } from '@/api/article'
import { createTag } from '@/api/admin'
import type { Tag } from '@/api/types/article'

/**
 * 编辑器标签选择的值既含已有标签 id（number），也含 allow-create 输入的新名（string）。
 * 落库前必须把名字解析成 id：后端只收 tagIds，直接过滤字符串会把用户输入的标签静默丢掉——
 * 界面上显示已选中，保存后却不存在。按名（trim、大小写不敏感）匹配现有标签，
 * 匹配不到的调后台创建接口建出来再取回 id
 */
export const resolveTagIds = async (tags: Array<number | string>): Promise<number[]> => {
  const resolved = tags.filter((t): t is number => typeof t === 'number')
  const names = tags
    .filter((t): t is string => typeof t === 'string')
    .map((t) => t.trim())
    .filter(Boolean)
  if (names.length === 0) {
    return [...new Set(resolved)]
  }

  const nameToId = new Map<string, number>()
  const loadIndex = async () => {
    const res = await getTags()
    for (const tag of (res.data ?? []) as Tag[]) {
      nameToId.set(tag.name.trim().toLowerCase(), tag.id)
    }
  }
  await loadIndex()

  for (const name of names) {
    let id = nameToId.get(name.toLowerCase())
    if (id == null) {
      // 后端创建接口不回传 id，建完重拉一次索引；重名冲突（并发下他人已建）由后端 400 提示
      await createTag(name)
      await loadIndex()
      id = nameToId.get(name.toLowerCase())
    }
    if (id != null) {
      resolved.push(id)
    }
  }
  return [...new Set(resolved)]
}
