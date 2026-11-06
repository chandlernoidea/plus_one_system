<script setup>
/**
 * 底部抽屉：单人版所有「多步但轻量」的操作（建单、改期、取消）都走它，
 * 不用 el-dialog，是为了在 390px 下贴底、可滚动、不吃掉整屏。
 */
import { onBeforeUnmount, onMounted, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '' },
  note: { type: String, default: '' }
})

const emit = defineEmits(['update:visible'])

function close() {
  emit('update:visible', false)
}

function handleKeydown(event) {
  if (event.key === 'Escape' && props.visible) {
    close()
  }
}

watch(
  () => props.visible,
  (visible) => {
    document.body.style.overflow = visible ? 'hidden' : ''
  }
)

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="sheet">
      <div v-if="visible" class="sheet-wrap">
        <div class="sheet-mask" @click="close"></div>
        <section class="sheet" role="dialog" aria-modal="true" :aria-label="title">
          <header class="sheet-head">
            <div class="sheet-titles">
              <h2 class="sheet-title">{{ title }}</h2>
              <p v-if="note" class="sheet-note">{{ note }}</p>
            </div>
            <button type="button" class="sheet-close" aria-label="关闭" @click="close">×</button>
          </header>
          <div class="sheet-body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="sheet-foot">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.sheet-wrap {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.sheet-mask {
  position: absolute;
  inset: 0;
  background: rgba(23, 19, 15, 0.32);
}

.sheet {
  position: relative;
  display: flex;
  width: 100%;
  max-width: 560px;
  max-height: 88vh;
  flex-direction: column;
  background: var(--paper-raised);
  border-top: 1px solid var(--ink);
}

.sheet-head {
  display: flex;
  align-items: flex-start;
  gap: var(--s3);
  padding: var(--s4) var(--s4) var(--s3);
  border-bottom: 1px solid var(--line);
}

.sheet-titles {
  min-width: 0;
}

.sheet-title {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 500;
  letter-spacing: 0.03em;
}

.sheet-note {
  margin-top: var(--s1);
  color: var(--ink-3);
  font-size: 11px;
  line-height: 1.5;
}

.sheet-close {
  flex: none;
  width: 28px;
  height: 28px;
  margin-left: auto;
  padding: 0;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}

.sheet-body {
  flex: 1 1 auto;
  padding: var(--s4);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.sheet-foot {
  display: flex;
  gap: var(--s2);
  padding: var(--s3) var(--s4) calc(var(--s4) + env(safe-area-inset-bottom));
  border-top: 1px solid var(--line);
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity var(--dur-2) var(--ease);
}

.sheet-enter-active .sheet,
.sheet-leave-active .sheet {
  transition: transform var(--dur-2) var(--ease);
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}

.sheet-enter-from .sheet,
.sheet-leave-to .sheet {
  transform: translateY(16px);
}
</style>
