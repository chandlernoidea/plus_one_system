<script setup>
import { computed, nextTick } from 'vue'
import { addDays, buildMonthGrid, fromKey, relativeDayLabel, toKey, WEEK_COLUMNS } from '../../utils/date'

const props = defineProps({
  data: { type: Object, required: true },
  role: { type: String, default: 'ADMIN' },
  cursor: { type: Date, required: true },
  selectedKey: { type: String, default: '' }
})

const emit = defineEmits(['select'])

const weeks = computed(() => buildMonthGrid(props.cursor))
const todayKey = computed(() => props.data.todayKey)

function labelFor(appointment) {
  const short = (name) => (name || '').replace(/^(顾客|美甲师)/, '')
  if (props.role === 'CUSTOMER') {
    return appointment.serviceTypeName || '美甲服务'
  }
  return short(appointment.customerName) || '顾客'
}

const cells = computed(() =>
  weeks.value.flat().map((day) => {
    if (!day.inMonth) {
      return { ...day, empty: true }
    }
    const summary = props.data.summaryForDay(day.key)
    const rows = props.data.staffRowsForDay(day.key)
    const load = props.data.dayLoad(day.key)
    const isPast = day.key < todayKey.value
    const items = summary.appointments.slice(0, 3).map((appointment) => ({
      id: appointment.id,
      time: appointment.startText,
      label: labelFor(appointment),
      tone: appointment.tone
    }))
    return {
      ...day,
      empty: false,
      count: summary.count,
      pending: summary.pending,
      onDuty: load.onDuty,
      onLeave: rows.filter((row) => row.onLeave).length,
      ratio: load.ratio,
      isPast,
      items,
      rest: Math.max(0, summary.appointments.length - items.length),
      relative: relativeDayLabel(day.key)
    }
  })
)

function handleSelect(day) {
  if (!day.inMonth) {
    return
  }
  emit('select', day.key)
}

function handleKeydown(event) {
  const offsets = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }
  if (!(event.key in offsets)) {
    return
  }
  event.preventDefault()
  const next = toKey(addDays(fromKey(props.selectedKey || todayKey.value), offsets[event.key]))
  emit('select', next)
  nextTick(() => {
    const node = event.currentTarget.querySelector(`[data-day="${next}"]`)
    node?.focus()
  })
}
</script>

<template>
  <div
    class="month"
    role="grid"
    :aria-label="`${cursor.getFullYear()} 年 ${cursor.getMonth() + 1} 月档期`"
    @keydown="handleKeydown"
  >
    <div class="month-head" role="row">
      <span
        v-for="column in WEEK_COLUMNS"
        :key="column.index"
        class="head-cell"
        :class="{ 'is-weekend': column.index === 0 || column.index === 6 }"
        role="columnheader"
      >
        <span class="head-long">{{ column.label }}</span>
        <span class="head-short">{{ column.short }}</span>
      </span>
    </div>

    <div
      :key="`${cursor.getFullYear()}-${cursor.getMonth()}`"
      class="month-grid"
      role="rowgroup"
    >
      <button
        v-for="(cell, index) in cells"
        :key="cell.key"
        type="button"
        role="gridcell"
        class="day"
        :class="{
          'is-outside': !cell.inMonth,
          'is-today': cell.isToday,
          'is-selected': cell.key === selectedKey,
          'is-weekend': cell.isWeekend && cell.inMonth,
          'is-empty': cell.empty
        }"
        :data-day="cell.key"
        :tabindex="cell.key === selectedKey ? 0 : -1"
        :aria-selected="cell.key === selectedKey"
        :aria-label="`${cell.key}${cell.count ? `，${cell.count} 个预约` : ''}`"
        :style="{ '--stagger': `${Math.min(index, 41) * 6}ms` }"
        @click="handleSelect(cell)"
      >
        <header class="day-top">
          <span class="day-num num">{{ cell.day }}</span>
          <template v-if="!cell.empty">
            <span v-if="cell.relative" class="day-flag">{{ cell.relative }}</span>
            <span v-else-if="cell.pending" class="day-pending mono">{{ cell.pending }} 待定</span>
          </template>
        </header>

        <template v-if="!cell.empty">
          <div class="day-body">
            <div v-if="role !== 'CUSTOMER' && cell.onLeave && cell.onDuty === 0" class="day-leave">
              <span class="leave-rule" aria-hidden="true"></span>
              休假
            </div>
            <ul v-if="cell.items.length" class="day-items">
              <li
                v-for="item in cell.items"
                :key="item.id"
                class="day-item"
                :class="`tone-${item.tone}`"
              >
                <span class="mono item-time">{{ item.time }}</span>
                <span class="item-name">{{ item.label }}</span>
              </li>
            </ul>
            <span v-if="cell.rest" class="day-rest mono">+{{ cell.rest }}</span>
          </div>

          <footer v-if="cell.onDuty || cell.onLeave" class="day-foot">
            <template v-if="role === 'CUSTOMER'">
              <span v-if="cell.onDuty" class="foot-note foot-open">可约</span>
              <span v-else class="foot-note">休假</span>
            </template>
            <template v-else>
              <span class="foot-note">{{ cell.onDuty }} 在岗</span>
              <span v-if="cell.ratio !== null" class="foot-bar" aria-hidden="true">
                <i :style="{ width: `${Math.round(cell.ratio * 100)}%` }"></i>
              </span>
            </template>
          </footer>
        </template>
      </button>
    </div>
  </div>
</template>

<style scoped>
.month {
  --cell-pad: 10px 11px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.month-head {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 1px;
}

.head-cell {
  padding: 9px var(--s3) 7px;
  border-bottom: 1px solid var(--line);
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  text-align: left;
}

.head-short {
  display: none;
}

.head-cell.is-weekend {
  color: var(--ink-4);
}

.month-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 1px;
  background: var(--line-soft);
}

.day {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--s2);
  min-height: 118px;
  padding: var(--cell-pad);
  border: 0;
  background: var(--paper);
  color: var(--ink);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: background-color var(--dur-1) var(--ease);
  animation: rise var(--dur-2) var(--ease) both;
  animation-delay: var(--stagger);
}

.day:hover {
  background: var(--paper-raised);
}

.day.is-weekend {
  background: #efe9dd;
}

.day.is-weekend:hover {
  background: var(--paper-raised);
}

.day.is-selected {
  z-index: 1;
  background: var(--paper-raised);
  box-shadow: inset 0 0 0 1px var(--ink);
}

.day.is-outside {
  background: var(--paper);
  cursor: default;
}

.day.is-outside .day-num {
  color: var(--ink-4);
  opacity: 0.55;
}

/* ---- 日期数字 ---- */

.day-top {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-height: 20px;
}

.day-num {
  font-size: 19px;
  line-height: 1;
  letter-spacing: 0.01em;
}

.day.is-today .day-num {
  color: var(--lacquer);
}

.day.is-selected .day-num {
  font-weight: 500;
}

.day-flag {
  color: var(--lacquer);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.day-pending {
  margin-left: auto;
  color: var(--ochre);
  font-size: 9px;
  letter-spacing: 0.06em;
}

/* ---- 档期条目 ---- */

.day-body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 3px;
  min-height: 0;
}

.day-items {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.day-item {
  display: flex;
  align-items: baseline;
  gap: 5px;
  padding-left: 5px;
  border-left: 1.5px solid var(--tone, var(--ink-3));
  line-height: 1.35;
  min-width: 0;
}

.item-time {
  flex: none;
  color: var(--ink-3);
  font-size: 9.5px;
  letter-spacing: 0.02em;
}

.item-name {
  overflow: hidden;
  color: var(--ink-2);
  font-size: 11px;
  letter-spacing: 0.01em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tone-lacquer {
  --tone: var(--lacquer);
}

.tone-moss {
  --tone: var(--moss);
}

.tone-ochre {
  --tone: var(--ochre);
}

.tone-slate {
  --tone: var(--slate);
}

.day-rest {
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.08em;
}

.day-leave {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  align-self: flex-start;
  padding: 1px 5px;
  border: 1px solid var(--ink-4);
  color: var(--ink-2);
  font-size: 9.5px;
  letter-spacing: 0.12em;
}

.leave-rule {
  display: block;
  width: 6px;
  height: 1px;
  background: var(--ink-4);
}

/* ---- 底部：在岗与占用率 ---- */

.day-foot {
  display: flex;
  align-items: center;
  gap: var(--s2);
  margin-top: auto;
}

.foot-note {
  flex: none;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
}

.foot-open {
  color: var(--moss);
}

.foot-bar {
  position: relative;
  display: block;
  flex: 1 1 auto;
  height: 2px;
  background: var(--line-soft);
}

.foot-bar i {
  position: absolute;
  inset: 0 auto 0 0;
  display: block;
  background: var(--ink-3);
  transition: width var(--dur-3) var(--ease);
}

@media (max-width: 720px) {
  .head-cell {
    padding: 7px 4px 6px;
    font-size: 9px;
    letter-spacing: 0.02em;
    text-align: center;
  }

  .head-long {
    display: none;
  }

  .head-short {
    display: inline;
  }

  .day {
    gap: 4px;
    min-height: 92px;
    padding: 6px 5px;
  }

  .day-num {
    font-size: 15px;
  }

  .day-flag,
  .day-pending,
  .day-foot {
    display: none;
  }

  .day-item:nth-child(n + 3) {
    display: none;
  }

  .item-name {
    display: none;
  }

  .day-item {
    padding-left: 3px;
    border-left-width: 1px;
  }

  .item-time {
    font-size: 9px;
  }

  .day-rest {
    font-size: 8px;
  }

  .day-foot {
    gap: 3px;
  }

  .foot-note {
    font-size: 8px;
    letter-spacing: 0;
  }

  .day-leave {
    font-size: 9px;
  }
}

</style>
