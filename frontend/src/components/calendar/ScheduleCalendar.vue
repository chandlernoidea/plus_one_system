<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { fromKey, monthName, pad2 } from '../../utils/date'
import MonthView from './MonthView.vue'
import WeekView from './WeekView.vue'
import DayPanel from './DayPanel.vue'

const props = defineProps({
  data: { type: Object, required: true },
  view: { type: Object, required: true },
  role: { type: String, default: 'ADMIN' }
})

const emit = defineEmits(['book', 'open-appointment', 'go-appointments', 'create-schedule'])

const ready = ref(false)

const mode = computed(() => props.view.mode.value)
const cursor = computed(() => props.view.cursor.value)
const selectedKey = computed(() => props.view.selectedKey.value)
const range = computed(() => props.view.range.value)

const title = computed(() => {
  if (mode.value === 'month') {
    return monthName(cursor.value)
  }
  const from = fromKey(range.value.from)
  const to = fromKey(range.value.to)
  return `${pad2(from.getMonth() + 1)}.${pad2(from.getDate())} – ${pad2(to.getMonth() + 1)}.${pad2(to.getDate())}`
})

const subTitle = computed(() => {
  if (mode.value === 'month') {
    return `${cursor.value.getFullYear()} 年 · ${props.data.todayKey === selectedKey.value ? '今天' : '本月档期'}`
  }
  return `${fromKey(range.value.from).getFullYear()} 年 · 周档期`
})

const legend = computed(() => {
  if (props.role === 'STAFF') {
    return []
  }
  return props.data.visibleStaffs.value.map((staff) => ({
    id: staff.staffId,
    name: staff.realName || staff.staffNo,
    tone: props.data.staffTone(staff.staffId)
  }))
})

onMounted(async () => {
  await Promise.all([props.data.loadStaffs(), props.data.loadAppointments()])
  ready.value = true
  await props.data.loadSchedules(range.value.from, range.value.to)
  await props.data.loadDaySlots(selectedKey.value)
})

watch(
  () => range.value,
  async (next) => {
    if (!ready.value) {
      return
    }
    await props.data.loadSchedules(next.from, next.to)
  }
)

watch(
  () => selectedKey.value,
  async (key) => {
    if (!ready.value) {
      return
    }
    await props.data.loadDaySlots(key)
  }
)

function handleSelect(key) {
  props.view.selectDay(key)
  if (key < range.value.from || key > range.value.to) {
    props.view.cursor.value = fromKey(key)
  }
}

function handleMode(next) {
  props.view.setMode(next)
}
</script>

<template>
  <section class="calendar" aria-label="档期日历">
    <header class="cal-bar">
      <div class="cal-title">
        <span class="eyebrow">{{ mode === 'month' ? 'MONTH VIEW' : 'WEEK VIEW' }}</span>
        <div class="cal-title-row">
          <h2 class="cal-label display">{{ title }}</h2>
          <span class="cal-sub">{{ subTitle }}</span>
        </div>
      </div>

      <div class="cal-controls">
        <button type="button" class="today-btn" @click="view.goToday()">今天</button>
        <div class="stepper">
          <button type="button" class="step" aria-label="上一页" @click="view.goPrev()">‹</button>
          <button type="button" class="step" aria-label="下一页" @click="view.goNext()">›</button>
        </div>
        <div class="seg" role="group" aria-label="视图切换">
          <button
            type="button"
            class="seg-item"
            :class="{ 'is-active': mode === 'month' }"
            :aria-pressed="mode === 'month'"
            @click="handleMode('month')"
          >
            月
          </button>
          <button
            type="button"
            class="seg-item"
            :class="{ 'is-active': mode === 'week' }"
            :aria-pressed="mode === 'week'"
            @click="handleMode('week')"
          >
            周
          </button>
        </div>
      </div>
    </header>

    <div v-if="legend.length" class="cal-legend">
      <span class="legend-item" v-for="item in legend" :key="item.id">
        <i :class="`tone-${item.tone}`" aria-hidden="true"></i>{{ item.name }}
      </span>
      <span class="legend-tip">竖线颜色对应美甲师 · 格底横条为当天占用率</span>
    </div>

    <div class="cal-body">
      <div class="cal-grid">
        <MonthView
          v-if="mode === 'month'"
          :data="data"
          :role="role"
          :cursor="cursor"
          :selected-key="selectedKey"
          @select="handleSelect"
        />
        <WeekView
          v-else
          :data="data"
          :role="role"
          :cursor="cursor"
          :selected-key="selectedKey"
          @select="handleSelect"
          @open-appointment="emit('open-appointment', $event)"
        />
      </div>

      <DayPanel
        :data="data"
        :role="role"
        :selected-key="selectedKey"
        @book="emit('book', $event)"
        @open-appointment="emit('open-appointment', $event)"
        @go-appointments="emit('go-appointments')"
        @create-schedule="emit('create-schedule', $event)"
      />
    </div>
  </section>
</template>

<style scoped>
.calendar {
  display: flex;
  flex-direction: column;
  gap: var(--s4);
}

/* ---- 工具条 ---- */

.cal-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--s5);
  padding-bottom: var(--s3);
  border-bottom: 1px solid var(--line);
}

.cal-title {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cal-title-row {
  display: flex;
  align-items: baseline;
  gap: var(--s3);
}

.cal-label {
  font-size: 30px;
  letter-spacing: -0.01em;
}

.cal-sub {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
}

.cal-controls {
  display: flex;
  align-items: center;
  gap: var(--s3);
}

.today-btn {
  padding: 0 0 2px;
  border: 0;
  border-bottom: 1px solid var(--ink);
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.stepper {
  display: flex;
  gap: 2px;
}

.step {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
  transition: border-color var(--dur-1) var(--ease), background-color var(--dur-1) var(--ease);
}

.step:hover {
  border-color: var(--ink);
  background: var(--paper-raised);
}

.seg {
  display: flex;
  gap: var(--s3);
  padding-left: var(--s3);
  border-left: 1px solid var(--line);
}

.seg-item {
  position: relative;
  padding: 0 0 2px;
  border: 0;
  background: transparent;
  color: var(--ink-4);
  cursor: pointer;
  font-family: var(--font-display);
  font-size: 14px;
  letter-spacing: 0.08em;
  transition: color var(--dur-1) var(--ease);
}

.seg-item:hover {
  color: var(--ink-2);
}

.seg-item.is-active {
  color: var(--ink);
}

.seg-item.is-active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 1.5px;
  background: var(--lacquer);
  content: "";
}

/* ---- 图例 ---- */

.cal-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--s4);
  margin-top: calc(var(--s2) * -1);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-3);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.legend-item i {
  display: block;
  width: 10px;
  height: 2px;
  background: var(--ink-3);
}

.legend-tip {
  margin-left: auto;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
}

/* ---- 主体 ---- */

.cal-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 318px;
  gap: var(--s7);
  align-items: start;
}

.cal-grid {
  min-width: 0;
}

.tone-lacquer {
  --tone: var(--lacquer);
}

.legend-item .tone-lacquer {
  background: var(--lacquer);
}

.legend-item .tone-moss {
  background: var(--moss);
}

.legend-item .tone-ochre {
  background: var(--ochre);
}

.legend-item .tone-slate {
  background: var(--slate);
}

@media (max-width: 1080px) {
  .cal-body {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--s5);
  }
}

@media (max-width: 720px) {
  .cal-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--s3);
  }

  .cal-label {
    font-size: 24px;
  }

  .cal-controls {
    width: 100%;
    justify-content: space-between;
  }

  .legend-tip {
    display: none;
  }
}
</style>
