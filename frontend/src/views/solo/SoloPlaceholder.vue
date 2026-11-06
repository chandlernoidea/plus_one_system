<script setup>
/**
 * 未落地页的占位。
 *
 * 不摆假数据：写清这一页会在哪个阶段做、做哪几件事，
 * 免得点进来像坏了，也免得自己回头忘了排期。
 */
import SoloLayout from './SoloLayout.vue'

defineProps({
  eyebrow: { type: String, default: '' },
  title: { type: String, default: '' },
  phase: { type: String, default: '' },
  items: { type: Array, default: () => [] },
  note: { type: String, default: '' }
})
</script>

<template>
  <SoloLayout>
    <header class="lede">
      <span class="eyebrow">{{ eyebrow }}</span>
      <h1 class="lede-title">{{ title }}</h1>
      <p class="lede-note">{{ phase }}</p>
    </header>

    <ol class="plans">
      <li v-for="(item, index) in items" :key="item" class="plan">
        <span class="plan-index mono">{{ String(index + 1).padStart(2, '0') }}</span>
        <span class="plan-text">{{ item }}</span>
      </li>
    </ol>

    <p v-if="note" class="note">{{ note }}</p>
  </SoloLayout>
</template>

<style scoped>
.lede {
  margin-bottom: var(--s4);
}

.lede-title {
  margin-top: var(--s1);
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 400;
  letter-spacing: 0.01em;
  line-height: 1.25;
}

.lede-note {
  margin-top: var(--s1);
  color: var(--ink-3);
  font-size: 11px;
}

.plans {
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--line);
}

.plan {
  display: flex;
  align-items: baseline;
  gap: var(--s3);
  padding: var(--s3) 0;
  border-bottom: 1px solid var(--line-soft);
}

.plan-index {
  flex: none;
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.plan-text {
  overflow-wrap: anywhere;
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.6;
}

.note {
  margin-top: var(--s4);
  color: var(--ink-4);
  font-size: 11px;
  line-height: 1.7;
}
</style>
