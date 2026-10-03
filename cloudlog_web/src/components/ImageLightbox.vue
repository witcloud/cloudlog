<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{ modelValue: boolean; src: string; alt?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const MIN_SCALE = 1
const MAX_SCALE = 5
const WHEEL_STEP = 1.25
const DBLCLICK_SCALE = 2.5

const stageRef = ref<HTMLElement>()
const imgRef = ref<HTMLImageElement>()
const scale = ref(1)
const panX = ref(0)
const panY = ref(0)
const panning = ref(false)

const origin = ref({ x: 0, y: 0 })

const transform = computed(() => `translate3d(${panX.value}px, ${panY.value}px, 0) scale(${scale.value})`)

const close = () => emit('update:modelValue', false)

const reset = () => {
  scale.value = 1
  panX.value = 0
  panY.value = 0
  panning.value = false
}

/** 以指针为不动点缩放：变换先平移后放大，指针到舞台中心的位移 d = pan + s·q，
    换到新的 s' 仍要落在同一个 d，于是 pan' = d - (s'/s)·(d - pan)。 */
const zoomAt = (clientX: number, clientY: number, next: number) => {
  const stage = stageRef.value
  const previous = scale.value
  const target = Math.min(Math.max(next, MIN_SCALE), MAX_SCALE)
  if (!stage || target === previous) return

  const rect = stage.getBoundingClientRect()
  const dx = clientX - rect.left - rect.width / 2
  const dy = clientY - rect.top - rect.height / 2
  const ratio = target / previous

  scale.value = target
  panX.value = dx - ratio * (dx - panX.value)
  panY.value = dy - ratio * (dy - panY.value)
  clampPan()
}

/** 图放大后允许拖走的部分只有溢出舞台的那一节，超出就把指针钉在边缘，
    否则图能整张拖出视野，只剩一片暗面。 */
const clampPan = () => {
  const img = imgRef.value
  const stage = stageRef.value
  if (!img || !stage) return

  const maxX = Math.max(0, (img.offsetWidth * scale.value - stage.clientWidth) / 2)
  const maxY = Math.max(0, (img.offsetHeight * scale.value - stage.clientHeight) / 2)
  panX.value = Math.min(maxX, Math.max(-maxX, panX.value))
  panY.value = Math.min(maxY, Math.max(-maxY, panY.value))
}

const onWheel = (event: WheelEvent) => {
  event.preventDefault()
  zoomAt(event.clientX, event.clientY, scale.value * (event.deltaY < 0 ? WHEEL_STEP : 1 / WHEEL_STEP))
}

const onDblclick = (event: MouseEvent) => {
  zoomAt(event.clientX, event.clientY, scale.value > MIN_SCALE ? MIN_SCALE : DBLCLICK_SCALE)
}

const onPointerDown = (event: PointerEvent) => {
  if (scale.value <= MIN_SCALE) return
  panning.value = true
  origin.value = { x: event.clientX - panX.value, y: event.clientY - panY.value }
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

const onPointerMove = (event: PointerEvent) => {
  if (!panning.value) return
  panX.value = event.clientX - origin.value.x
  panY.value = event.clientY - origin.value.y
  clampPan()
}

const stopPan = () => {
  panning.value = false
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') close()
}

watch(
  () => [props.modelValue, props.src] as const,
  ([open]) => {
    reset()
    if (open) {
      document.body.style.overflow = 'hidden'
      window.addEventListener('keydown', onKeydown)
    } else {
      document.body.style.overflow = ''
      window.removeEventListener('keydown', onKeydown)
    }
  },
)

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="lightbox">
      <div
        v-if="modelValue"
        ref="stageRef"
        class="lightbox"
        role="dialog"
        aria-modal="true"
        aria-label="查看图片"
        @click.self="close"
        @wheel="onWheel"
      >
        <img
          ref="imgRef"
          class="lightbox__img"
          :class="{ 'is-pannable': scale > MIN_SCALE, 'is-panning': panning }"
          :src="src"
          :alt="alt || ''"
          :style="{ transform }"
          draggable="false"
          @dblclick="onDblclick"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="stopPan"
          @pointercancel="stopPan"
        >
        <button class="lightbox__close" type="button" aria-label="关闭" @click="close">
          <svg viewBox="0 0 16 16" width="18" height="18" fill="none" aria-hidden="true">
            <path
              d="M3.5 3.5 12.5 12.5M12.5 3.5 3.5 12.5"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
            />
          </svg>
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* 灯箱：全站唯一一枚暗面，控件只有关闭一处，缩放与平移都由手势承担（P3 最少字段）。
   不引第二配色、不加阴影，暗面与图上文字都走 variables.css 的主题不变量 Token。 */
.lightbox {
  position: fixed;
  inset: 0;
  z-index: var(--z-lightbox);
  display: grid;
  place-items: center;
  padding: var(--space-2xl) var(--space-xl) var(--space-xl);
  background-color: var(--color-stage);
}

.lightbox__img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  cursor: zoom-in;
  touch-action: none;
  user-select: none;
}

.lightbox__img.is-pannable {
  cursor: grab;
}

.lightbox__img.is-panning {
  cursor: grabbing;
}

.lightbox__close {
  position: absolute;
  top: var(--space-lg);
  right: var(--space-lg);
  display: grid;
  place-items: center;
  width: 2.5rem;
  height: 2.5rem;
  color: var(--color-stage-ink);
  border: 1px solid color-mix(in oklab, var(--color-stage-ink) 28%, transparent);
  border-radius: var(--radius-sm);
  transition: border-color var(--dur-micro) var(--ease-out);
}

.lightbox__close:hover {
  border-color: var(--color-stage-ink);
}

.lightbox-enter-active,
.lightbox-leave-active {
  transition: opacity var(--dur-short) var(--ease-out);
}

.lightbox-enter-from,
.lightbox-leave-to {
  opacity: 0;
}
</style>
