<script setup>
/**
 * 排班日历：月视图与周视图共用一套格子。
 *
 * 设计取舍（390px 手机）：
 * - 不做等比时间轴——7 列只剩 50px，画出来什么都看不清；
 * - 一格里的预约用「彩色横条」表示，颜色＝状态，条数≤3，多的收进「+N」；
 * - 周视图横条上带开始时间，月视图只留颜色，靠点开看详情。
 */
import { computed } from 'vue'

const props = defineProps({
  mode: { type: String, default: 'month' },
  /** [{ date, dayNum, dow, isToday, inMonth, bookings }] */
  days: { type: Array, default: () => [] },
  selected: { type: String, default: '' }
})

const emit = defineEmits(['select', 'open'])

const WEEK_HEADS = ['一', '二', '三', '四', '五', '六', '日']

const TONE = {
  WAIT_DEPOSIT: 'wait',
  BOOKED: 'booked',
  ARRIVED: 'arrived',
  IN_SERVICE: 'service',
  COMPLETED: 'done',
  CANCELLED: 'void',
  NO_SHOW: 'noshow'
}

const maxBars = computed(() => (props.mode === 'week' ? 12 : 3))

function barsOf(day) {
  // 已取消的排到最后，别把真正要做的单挤出可视区
  const list = [...(day.bookings || [])]
  list.sort((a, b) => {
    const av = a.status === 'CANCELLED' ? 1 : 0
    const bv = b.status === 'CANCELLED' ? 1 : 0
    return av - bv || a.startAt.localeCompare(b.startAt)
  })
  return list.slice(0, maxBars.value)
}

function moreOf(day) {
  return Math.max(0, (day.bookings || []).length - maxBars.value)
}

function timeOf(value) {
  return value ? value.slice(11, 16) : ''
}

function toneOf(booking) {
  return `tone-${TONE[booking.status] || 'booked'}`
}

function barTitle(booking) {
  return `${timeOf(booking.startAt)} ${booking.clientNickname} · ${(booking.items || []).map((i) => i.styleName).join(' + ')}`
}
</script>

<template>
  <div class="cal" :class="`is-${mode}`">
    <div class="head">
      <span v-for="head in WEEK_HEADS" :key="head" class="dow">{{ head }}</span>
    </div>

    <div class="grid">
      <button
        v-for="day in days"
        :key="day.date"
        type="button"
        class="cell"
        :class="{
          'is-selected': day.date === selected,
          'is-today': day.isToday,
          'is-out': mode === 'month' && !day.inMonth
        }"
        @click="emit('select', day.date)"
      >
        <span class="num mono">{{ day.dayNum }}</span>

        <span class="bars">
          <span
            v-for="booking in barsOf(day)"
            :key="booking.id"
            class="bar"
            :class="[toneOf(booking), { 'is-multi': (booking.items || []).length > 1 }]"
            :title="barTitle(booking)"
            @click.stop="emit('open', booking)"
          >
            <em v-if="mode === 'week'" class="bar-time">{{ timeOf(booking.startAt) }}</em>
          </span>
          <span v-if="moreOf(day)" class="more mono">+{{ moreOf(day) }}</span>
        </span>
      </button>
    </div>

    <div class="legend">
      <span class="legend-item"><i class="dot tone-wait"></i>待定金</span>
      <span class="legend-item"><i class="dot tone-booked"></i>已定档</span>
      <span class="legend-item"><i class="dot tone-arrived"></i>已到店</span>
      <span class="legend-item"><i class="dot tone-service"></i>服务中</span>
      <span class="legend-item"><i class="dot tone-done"></i>已完成</span>
      <span class="legend-item"><i class="dot tone-noshow"></i>爽约</span>
    </div>
  </div>
</template>

<style scoped>
.cal {
  margin-bottom: var(--s3);
}

.head {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  margin-bottom: 4px;
}

.dow {
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.1em;
  text-align: center;
}

.grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 2px;
}

.cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  padding: 4px 3px;
  border: 1px solid transparent;
  border-radius: 3px;
  background: var(--paper-raised);
  color: var(--ink);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: border-color 0.15s ease;
}

.cell:hover {
  border-color: var(--line-strong);
}

.cell.is-out {
  background: transparent;
  color: var(--ink-4);
}

.cell.is-today .num {
  color: var(--lacquer);
  font-weight: 600;
}

.cell.is-selected {
  border-color: var(--ink);
  background: var(--paper);
}

.num {
  font-size: 11px;
  line-height: 1.1;
}

.bars {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.bar {
  display: block;
  width: 100%;
  min-width: 0;
  height: 4px;
  padding: 0;
  border: 0;
  border-radius: 1px;
  cursor: pointer;
  opacity: 0.85;
}

.bar:hover {
  opacity: 1;
}

.bar.is-multi {
  height: 6px;
}

.more {
  color: var(--ink-4);
  font-size: 9px;
  line-height: 1;
}

/* ---------- 颜色：一张日历上能分清的六种状态 ---------- */
.tone-wait { background: var(--ochre); }
.tone-booked { background: #3a5a7a; }
.tone-arrived { background: #2f6b6b; }
.tone-service { background: var(--moss); }
.tone-done { background: #9a9084; }
.tone-void {
  background: repeating-linear-gradient(45deg, #bdb3a4, #bdb3a4 2px, transparent 2px, transparent 4px);
}
.tone-noshow { background: var(--lacquer); }

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: 8px;
  color: var(--ink-4);
  font-size: 10px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 3px;
  border-radius: 1px;
}

/* ---------- 周视图：格子高一些，横条上带时间 ---------- */
.is-week .cell {
  min-height: 132px;
}

.is-week .bar {
  display: flex;
  align-items: center;
  height: 15px;
  padding: 0 2px;
  border-radius: 2px;
}

.is-week .bar.is-multi {
  height: 17px;
}

.bar-time {
  color: var(--paper-raised);
  font-size: 8px;
  font-style: normal;
  letter-spacing: -0.02em;
  opacity: 0.95;
}

/* ---------- 月视图：格子矮，只留颜色 ---------- */
.is-month .cell {
  min-height: 54px;
}

@media (min-width: 900px) {
  .is-month .cell {
    min-height: 82px;
    padding: 6px 5px;
  }

  .is-month .bar {
    height: 5px;
  }

  .num {
    font-size: 12px;
  }

  .is-week .cell {
    min-height: 220px;
  }

  .is-week .bar {
    height: 18px;
  }

  .bar-time {
    font-size: 9px;
  }
}
</style>
