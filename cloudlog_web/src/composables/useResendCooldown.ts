import { ref, watch, onUnmounted } from 'vue'

/** 重发窗口秒数，与后端 VerificationCodeService 那道 60 秒窗口同数 */
export const RESEND_SECONDS = 60

const keyOf = (email: string) => `cloudlog:code-cooldown:${email}`

/**
 * 验证码重发倒计时，按邮箱记到期时刻。
 *
 * 原先倒计时只活在内存里：刷新一次就归零，而服务端那 60 秒不会跟着重来，
 * 归零的那一下只是让人多点一次去撞「发送过于频繁」。
 * 记在邮箱而不是记一个全局数，是因为换邮箱该拿到一次全新的窗口。
 *
 * @param currentEmail 取当前邮箱的 getter
 */
export function useResendCooldown(currentEmail: () => string) {
  const countdown = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  const stop = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  const start = (seconds = RESEND_SECONDS) => {
    stop()
    countdown.value = seconds
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) stop()
    }, 1000)
  }

  /** 发成功一次就记下到期时刻，并立刻进入倒计时 */
  const arm = () => {
    const email = currentEmail()
    if (email) localStorage.setItem(keyOf(email), String(Date.now() + RESEND_SECONDS * 1000))
    start()
  }

  /** 换邮箱即换窗口：上一个邮箱剩的秒数不该挡这一个 */
  const sync = () => {
    const email = currentEmail()
    const until = Number(localStorage.getItem(keyOf(email)) || 0)
    const left = Math.ceil((until - Date.now()) / 1000)
    if (left > 0) {
      start(left)
      return
    }
    if (email) localStorage.removeItem(keyOf(email))
    stop()
    countdown.value = 0
  }

  watch(currentEmail, sync)
  sync()
  onUnmounted(stop)

  return { countdown, start, arm }
}
