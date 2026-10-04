import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * 把一张筛选表镜像到地址栏，并在进页时按地址栏还原。
 *
 * 后台的条件此前只活在组件内存里：顶栏「返回博客」走一趟回来条件全丢，
 * 刷新也是；分类管理那句「需先迁移这些文章」甚至给不出一个带 categoryId 的落点。
 * 值一律以字符串写进 query，空值不占位，所以 `?status=draft` 这种链接能直接分享。
 *
 * 只写自己那一份、不反向读 route.query（还原只发生在挂载时一次），因此不存在回环。
 *
 * @param state 组件里的 reactive 筛选对象，键名即 query 参数名
 */
export function useUrlFilters<T extends object>(state: T) {
  const route = useRoute()
  const router = useRouter()
  // 调用方传的是 interface 形状的 reactive 对象（没有索引签名），按键读写时统一走这张视图
  const fields = state as unknown as Record<string, unknown>
  const names = Object.keys(fields)

  for (const name of names) {
    const raw = route.query[name]
    if (typeof raw !== 'string' || raw === '') continue
    // 数字字段（页码、id）按原类型转回；转不出数字的（?page=abc）直接忽略，不拿 NaN 去发请求
    if (typeof fields[name] === 'number') {
      const num = Number(raw)
      if (Number.isFinite(num)) fields[name] = num
      continue
    }
    fields[name] = raw
  }

  watch(
    () => names.map((name) => fields[name]),
    () => {
      const query: Record<string, string> = {}
      for (const name of names) {
        const value = fields[name]
        if (value !== null && value !== undefined && value !== '') query[name] = String(value)
      }
      // replace 而不是 push：改一次筛选不该在历史里留一条要后退两次才出得去的记录
      router.replace({ query })
    },
  )
}
