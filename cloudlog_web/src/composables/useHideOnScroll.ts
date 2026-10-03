import { onMounted, onUnmounted, ref } from 'vue'

/* 单向累计：换向即清零重算，靠增量而不是瞬时速度，避免触控板小幅抖动误判。
   500 约五格滚轮——阈值若低于一格（Chrome 默认约 100px），就成了「一下滑就收起」 */
const HIDE_AFTER = 500
const SHOW_AFTER = 8
const TOP_ZONE = 100

/** 下滑收起、上滑唤回。locked 为真（抽屉展开、顶栏下拉菜单展开）时保持可见并丢弃累计量。 */
export function useHideOnScroll(locked: () => boolean = () => false) {
  const hidden = ref(false)
  let lastY = 0
  let scrolled = 0
  let goingDown = false

  const onScroll = () => {
    const y = window.scrollY
    const delta = y - lastY
    lastY = y
    if (delta === 0) return

    const down = delta > 0
    if (down !== goingDown) {
      goingDown = down
      scrolled = 0
    }

    // 一帧走过一整屏只可能是拖滚动条、锚点跳转或代码滚动：不是阅读流的滚，就亮出来并重新起算
    if (Math.abs(delta) >= window.innerHeight) {
      hidden.value = false
      scrolled = 0
      return
    }

    if (locked()) {
      hidden.value = false
      scrolled = 0
      return
    }

    scrolled += Math.abs(delta)

    if (y <= TOP_ZONE) {
      hidden.value = false
      scrolled = 0
      return
    }

    if (down) {
      if (scrolled >= HIDE_AFTER) hidden.value = true
    } else if (scrolled >= SHOW_AFTER) hidden.value = false
  }

  /** 跟随滚动位置恢复一起调用：新页面进入时顶栏必定可见 */
  const reset = () => {
    lastY = window.scrollY
    scrolled = 0
    goingDown = false
    hidden.value = false
  }

  onMounted(() => {
    reset()
    window.addEventListener('scroll', onScroll, { passive: true })
  })

  onUnmounted(() => {
    window.removeEventListener('scroll', onScroll)
  })

  return { hidden, reset }
}
