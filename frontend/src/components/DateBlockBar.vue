<script setup>
/**
 * 日期分段导航条。
 *
 * 列表按每 3 天切段后可能有很多段，默认只渲染「当前段附近」的
 * collapsedCount 段，其余收进「更多 N 段」；展开后换行铺开。
 * 这样无论数据增长到多少个月，工具条高度都是可控的。
 *
 * 可滚动轨道与折叠开关是两个元素：开关在遮罩之外，始终清晰可点。
 */
import { computed, ref } from 'vue'

const props = defineProps({
  blocks: { type: Array, default: () => [] },
  modelValue: { type: String, default: null },
  total: { type: Number, default: 0 },
  collapsedCount: { type: Number, default: 5 }
})

const emit = defineEmits(['update:modelValue'])

const expanded = ref(false)

const canCollapse = computed(() => props.blocks.length > props.collapsedCount)

const activeIndex = computed(() =>
  props.blocks.findIndex((block) => block.key === props.modelValue)
)

/** 折叠态只保留「当前段」附近的一段窗口，避免日期多了把工具条撑爆 */
const visibleBlocks = computed(() => {
  if (!canCollapse.value || expanded.value) {
    return props.blocks
  }
  const size = props.collapsedCount
  const center = activeIndex.value >= 0 ? activeIndex.value : 0
  const start = Math.min(
    Math.max(center - Math.floor(size / 2), 0),
    Math.max(props.blocks.length - size, 0)
  )
  return props.blocks.slice(start, start + size)
})

const hiddenCount = computed(() => props.blocks.length - visibleBlocks.value.length)

function pick(block) {
  emit('update:modelValue', block ? block.key : null)
}
</script>

<template>
  <nav class="block-bar" :class="{ 'is-expanded': expanded }" aria-label="按日期分段">
    <div class="block-track">
      <button type="button" class="blk" :class="{ 'is-active': !modelValue }" @click="pick(null)">
        <span class="blk-label">全部</span>
        <span class="blk-count mono">{{ total }}</span>
      </button>

      <button
        v-for="block in visibleBlocks"
        :key="block.key"
        type="button"
        class="blk"
        :class="{
          'is-active': modelValue === block.key,
          'is-current': block.isCurrent,
          'is-empty': block.count === 0
        }"
        @click="pick(block)"
      >
        <span class="blk-label">{{ block.label }}</span>
        <span v-if="block.isCurrent" class="blk-dot" aria-hidden="true"></span>
        <span class="blk-count mono">{{ block.count }}</span>
      </button>
    </div>

    <button
      v-if="canCollapse"
      type="button"
      class="blk-toggle"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      {{ expanded ? '收起' : `更多 ${hiddenCount} 段` }}
      <span class="blk-caret" aria-hidden="true">{{ expanded ? '▴' : '▾' }}</span>
    </button>
  </nav>
</template>

<style scoped>
.block-bar {
  display: flex;
  align-items: center;
  gap: var(--s3);
  margin: calc(var(--s2) * -1) 0 var(--s4);
  padding-bottom: var(--s3);
  border-bottom: 1px solid var(--line);
}

/* 只有片段轨道自己滚动，右侧折叠开关始终可见 */
.block-track {
  display: flex;
  flex: 1 1 auto;
  gap: 2px;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
  mask-image: linear-gradient(to right, #000 94%, transparent 100%);
}

.block-track::-webkit-scrollbar {
  display: none;
}

/* 展开后换行铺开，不再横向滚动 */
.block-bar.is-expanded .block-track {
  flex-wrap: wrap;
  overflow-x: visible;
  mask-image: none;
}

.blk {
  display: inline-flex;
  flex: none;
  align-items: baseline;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  white-space: nowrap;
  transition: background-color var(--dur-1) var(--ease), color var(--dur-1) var(--ease),
    border-color var(--dur-1) var(--ease);
}

.blk:hover {
  border-color: var(--line-strong);
  color: var(--ink);
}

.blk.is-active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
}

.blk.is-empty:not(.is-active) {
  color: var(--ink-4);
}

.blk-label {
  font-size: 12px;
  letter-spacing: 0.04em;
}

.blk-count {
  color: inherit;
  font-size: 10px;
  opacity: 0.75;
}

.blk.is-current .blk-label {
  font-weight: 500;
}

.blk-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--lacquer);
  transform: translateY(-2px);
}

.blk.is-active .blk-dot {
  background: var(--paper-raised);
}

/* 折叠开关：弱化成文字按钮，不跟日期段抢注意力 */
.blk-toggle {
  flex: none;
  padding: 6px 10px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  letter-spacing: 0.06em;
  white-space: nowrap;
  transition: border-color var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.blk-toggle:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.blk-caret {
  font-size: 9px;
}

@media (max-width: 720px) {
  .blk {
    padding: 9px 10px;
  }

  .blk-toggle {
    padding: 9px 10px;
  }
}
</style>
