<script setup>
/**
 * 页面抬头：一句话结论 + 若干关键数字。
 *
 * 数字区由调用方算好传入，组件只负责排版；
 * 窄屏隐藏单位（避免 390px 下「个 1 小时」换行），只保留数字与标签。
 */
defineProps({
  eyebrow: { type: String, default: '' },
  title: { type: String, default: '' },
  note: { type: String, default: '' },
  stats: { type: Array, default: () => [] }
})
</script>

<template>
  <section class="lede">
    <div class="lede-main">
      <span class="eyebrow">{{ eyebrow }}</span>
      <h1 class="lede-title">{{ title }}</h1>
      <p v-if="note" class="lede-note">{{ note }}</p>
    </div>

    <dl v-if="stats.length" class="lede-stats">
      <div v-for="stat in stats" :key="stat.label" class="stat">
        <dt class="stat-label">{{ stat.label }}</dt>
        <dd class="stat-value">
          <span class="num">{{ stat.value }}</span>
          <span v-if="stat.unit" class="stat-unit">{{ stat.unit }}</span>
        </dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.lede {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--s6);
  align-items: end;
  padding-bottom: var(--s6);
}

.lede-main {
  display: flex;
  flex-direction: column;
  gap: var(--s3);
}

.lede-title {
  max-width: 22ch;
  font-family: var(--font-display);
  font-size: 38px;
  font-weight: 400;
  line-height: 1.18;
  letter-spacing: -0.015em;
}

.lede-note {
  color: var(--ink-3);
  font-size: 13px;
  letter-spacing: 0.02em;
}

.lede-note .mono {
  color: var(--ink-2);
  font-size: 12px;
}

/* ---- 数据条 ---- */

.lede-stats {
  display: flex;
  align-items: flex-end;
  gap: 0;
  margin: 0;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0 var(--s5);
  border-left: 1px solid var(--line);
}

.stat:first-child {
  border-left: 0;
  padding-left: 0;
}

.stat:last-child {
  padding-right: 0;
}

.stat-label {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 3px;
  margin: 0;
  color: var(--ink);
  line-height: 1;
}

.stat-value .num {
  font-size: 26px;
  letter-spacing: -0.02em;
}

.stat-unit {
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.08em;
}

@media (max-width: 960px) {
  .lede {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--s4);
    padding-bottom: var(--s4);
  }

  .lede-title {
    font-size: 28px;
  }

  .lede-stats {
    flex-wrap: wrap;
    gap: var(--s4) 0;
  }

  .stat {
    padding: 0 var(--s4);
  }

  .stat-unit {
    display: none;
  }
}
</style>
