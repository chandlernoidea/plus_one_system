<script setup>
defineProps({
  blocks: { type: Array, default: () => [] },
  modelValue: { type: String, default: null },
  total: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue'])

function pick(block) {
  emit('update:modelValue', block ? block.key : null)
}
</script>

<template>
  <nav class="block-bar" aria-label="按日期分段">
    <button
      type="button"
      class="blk"
      :class="{ 'is-active': !modelValue }"
      @click="pick(null)"
    >
      <span class="blk-label">全部</span>
      <span class="blk-count mono">{{ total }}</span>
    </button>

    <button
      v-for="block in blocks"
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
  </nav>
</template>

<style scoped>
.block-bar {
  display: flex;
  align-items: stretch;
  gap: 2px;
  margin: calc(var(--s2) * -1) 0 var(--s4);
  padding-bottom: var(--s3);
  border-bottom: 1px solid var(--line);
  overflow-x: auto;
  scrollbar-width: none;
  mask-image: linear-gradient(to right, #000 94%, transparent 100%);
}

.block-bar::-webkit-scrollbar {
  display: none;
}

@media (max-width: 720px) {
  .blk {
    padding: 9px 10px;
  }
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
</style>
