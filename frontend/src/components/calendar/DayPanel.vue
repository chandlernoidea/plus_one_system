<script setup>
/**
 * 当日明细面板，位于日历右侧（窄屏时下移）。
 *
 * 三端共用同一结构，但语义不同：
 * - 管理员：当天订单 + 每位在岗美甲师的剩余时段；
 * - 美甲师：只看自己的档期与预约，没排班的日子给出「去排班」入口；
 * - 顾客：只看在岗美甲师和可约窗口，点击「预约」带着日期与美甲师跳转。
 *
 * 注意 available / windows 为 null 表示「时段还没加载」，
 * 与 0（确实约满）必须区分开，否则会出现「待载入」被判成「已约满」。
 */
import { computed } from 'vue'
import {
  formatMoney,
  fromKey,
  monthName,
  relativeDayLabel,
  timeText,
  weekdayFull
} from '../../utils/date'
import { statusMeta } from '../../composables/useCalendarData'

const props = defineProps({
  data: { type: Object, required: true },
  role: { type: String, default: 'ADMIN' },
  selectedKey: { type: String, required: true }
})

const emit = defineEmits(['book', 'open-appointment', 'go-appointments', 'create-schedule'])

const date = computed(() => fromKey(props.selectedKey))
const relative = computed(() => relativeDayLabel(props.selectedKey))
const summary = computed(() => props.data.summaryForDay(props.selectedKey))
const rows = computed(() => props.data.staffRowsForDay(props.selectedKey).filter((row) => row.onDuty || row.onLeave))
const isFuture = computed(() => props.selectedKey > props.data.todayKey)

/** 同一块区域在不同角色下的叫法：我的档期 / 可预约美甲师 / 在岗美甲师 */
const staffSectionTitle = computed(() => {
  if (props.role === 'STAFF') {
    return '我的档期'
  }
  return props.role === 'CUSTOMER' ? '可预约美甲师' : '在岗美甲师'
})

const openTotal = computed(() => {
  let total = 0
  for (const row of rows.value) {
    total += (props.role === 'CUSTOMER' ? row.windows : row.available) ?? 0
  }
  return total
})

const headline = computed(() => {
  if (rows.value.length === 0) {
    if (props.role === 'STAFF') {
      return isFuture.value ? '这一天没有排班' : '这一天没有排班'
    }
    return props.role === 'CUSTOMER' ? '这一天没有美甲师在岗' : '这一天全店没有排班'
  }
  if (props.role === 'CUSTOMER') {
    if (rows.value.length === 0) {
      return '这一天还没有美甲师排班'
    }
    return `${rows.value.filter((row) => row.onDuty).length} 位美甲师在岗`
  }
  if (summary.value.count === 0) {
    return '这一天还没有预约'
  }
  return `${summary.value.count} 个预约`
})

function shiftText(row) {
  if (!row.onDuty) {
    return row.onLeave ? '休假' : '未排班'
  }
  return row.shifts
    .map((shift) => `${timeText(shift.startTime)}–${timeText(shift.endTime)}`)
    .join(' / ')
}

function availabilityText(row) {
  if (!row.onDuty) {
    return ''
  }
  if (row.available === null) {
    return '时段待载入'
  }
  if (props.role === 'CUSTOMER') {
    return row.windows > 0 ? `可约 ${row.windows} 个 1 小时` : '已约满'
  }
  return `空闲 ${row.available} 段`
}

function goBook(staffId) {
  emit('book', { key: props.selectedKey, staffId })
}
</script>

<template>
  <aside class="panel">
    <header class="panel-head">
      <div class="panel-date">
        <span class="panel-num num">{{ date.getDate() }}</span>
        <div class="panel-date-meta">
          <span class="eyebrow">{{ relative || `${monthName(date)} · ${weekdayFull(date)}` }}</span>
          <span class="panel-weekday">{{ monthName(date) }} {{ weekdayFull(date) }}</span>
        </div>
      </div>
      <p class="panel-headline">
        {{ headline }}
        <template v-if="role !== 'CUSTOMER' && summary.revenue > 0">
          <span class="panel-sep" aria-hidden="true">/</span>
          <span class="mono">{{ formatMoney(summary.revenue, { sign: true }) }}</span>
        </template>
      </p>
      <p v-if="role === 'CUSTOMER' && openTotal > 0" class="panel-note">
        当天共有 <strong class="num">{{ openTotal }}</strong> 个 1 小时空档
      </p>
    </header>

    <section v-if="rows.length" class="panel-section">
      <div class="section-head">
        <span class="section-title">{{ staffSectionTitle }}</span>
        <span class="section-line" aria-hidden="true"></span>
      </div>

      <ul class="staff-list">
        <li v-for="row in rows" :key="row.staffId" class="staff-row" :class="{ 'is-off': !row.onDuty }">
          <span class="staff-tone" :class="`tone-${row.tone}`" aria-hidden="true"></span>
          <span class="staff-name">
            {{ row.name }}
            <em v-if="row.isSelf" class="staff-self">我</em>
          </span>
          <span class="staff-shift mono">{{ shiftText(row) }}</span>
          <span class="staff-open mono" :class="{ 'is-empty': row.onDuty && !row.available }">
            {{ availabilityText(row) }}
          </span>
          <button
            v-if="role === 'CUSTOMER' && row.onDuty && (row.windows === null || row.windows > 0)"
            type="button"
            class="row-action"
            @click="goBook(row.staffId)"
          >
            预约
          </button>
        </li>
      </ul>

      <p v-if="role === 'ADMIN' && summary.pending" class="panel-hint">
        其中 {{ summary.pending }} 单待确认，尚未占用时段
      </p>
    </section>

    <section class="panel-section">
      <div class="section-head">
        <span class="section-title">{{ role === 'CUSTOMER' ? '我的预约' : '当日预约' }}</span>
        <span class="section-line" aria-hidden="true"></span>
      </div>

      <ul v-if="summary.appointments.length" class="appt-list">
        <li v-for="appointment in summary.appointments" :key="appointment.id" class="appt-row">
          <button type="button" class="appt-main" @click="emit('open-appointment', appointment.id)">
            <span class="appt-time mono">{{ appointment.startText }}</span>
            <span class="appt-body">
              <span class="appt-name">
                {{ role === 'CUSTOMER' ? appointment.serviceTypeName : appointment.customerName }}
              </span>
              <span class="appt-meta">
                <template v-if="role !== 'CUSTOMER'">
                  {{ appointment.staffName || '未指派' }} ·
                </template>
                <template v-if="appointment.estDurationMinutes">
                  {{ appointment.estDurationMinutes }} 分钟 ·
                </template>
                {{ appointment.serviceTypeName || '未选款式' }}
              </span>
            </span>
            <span class="appt-status" :class="`tone-${statusMeta(appointment.status).tone}`">
              {{ statusMeta(appointment.status).label }}
            </span>
          </button>
        </li>
      </ul>

      <p v-else class="empty-note">
        这一天还没有预约
        <em v-if="role === 'STAFF' && isFuture">空档可以留给临时到店的客人。</em>
        <em v-else-if="role === 'CUSTOMER'">可以从月表里挑一个有空的日子，或换个美甲师。</em>
      </p>
    </section>

    <footer class="panel-foot">
      <button
        v-if="role === 'STAFF' && isFuture && rows.length === 0"
        type="button"
        class="panel-action"
        @click="emit('create-schedule', selectedKey)"
      >
        为这一天排班
      </button>
      <button type="button" class="panel-link" @click="emit('go-appointments')">
        {{ role === 'CUSTOMER' ? '查看我的全部预约' : '在预约管理中查看' }}
        <span aria-hidden="true">→</span>
      </button>
    </footer>
  </aside>
</template>

<style scoped>
.panel {
  position: sticky;
  /* 页头高度 + 一点呼吸间距 */
  top: calc(var(--shell-bar-h) + 24px);
  display: flex;
  flex-direction: column;
  gap: var(--s5);
  padding: var(--s5) 0 0;
  border-top: 1px solid var(--ink);
}

/* ---- 抬头 ---- */

.panel-head {
  display: flex;
  flex-direction: column;
  gap: var(--s2);
}

.panel-date {
  display: flex;
  align-items: flex-start;
  gap: var(--s3);
}

.panel-num {
  font-size: 54px;
  line-height: 0.9;
  letter-spacing: -0.03em;
}

.panel-date-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 6px;
}

.panel-weekday {
  color: var(--ink-2);
  font-family: var(--font-display);
  font-size: 14px;
  letter-spacing: 0.04em;
}

.panel-headline {
  color: var(--ink-2);
  font-family: var(--font-display);
  font-size: 15px;
  letter-spacing: 0.02em;
}

.panel-sep {
  margin: 0 4px;
  color: var(--ink-4);
}

.panel-note {
  color: var(--ink-3);
  font-size: 12px;
}

.panel-note strong {
  color: var(--lacquer);
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 500;
}

/* ---- 分区 ---- */

.panel-section {
  display: flex;
  flex-direction: column;
  gap: var(--s2);
}

.panel-section .section-head {
  margin-bottom: 2px;
}

.panel-section .section-title {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--ink-3);
  text-transform: uppercase;
}

/* ---- 美甲师 ---- */

.staff-list,
.appt-list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.staff-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  grid-template-areas:
    "tone name open"
    "tone shift action";
  gap: 1px var(--s2);
  padding: 9px 0;
  border-bottom: 1px solid var(--line-soft);
  align-items: center;
}

.panel-hint {
  margin-top: 6px;
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.staff-row.is-off {
  opacity: 0.55;
}

.staff-tone {
  grid-area: tone;
  width: 3px;
  height: 26px;
  background: var(--tone, var(--ink-4));
}

.staff-name {
  grid-area: name;
  color: var(--ink);
  font-size: 13px;
  letter-spacing: 0.02em;
}

.staff-self {
  margin-left: 4px;
  padding: 0 3px;
  background: var(--lacquer);
  color: var(--paper-raised);
  font-family: var(--font-mono);
  font-size: 8px;
  font-style: normal;
  letter-spacing: 0.1em;
}

.staff-shift {
  grid-area: shift;
  color: var(--ink-4);
  font-size: 9.5px;
  letter-spacing: 0.04em;
}

.staff-open {
  grid-area: open;
  color: var(--moss);
  font-size: 9.5px;
  letter-spacing: 0.04em;
  text-align: right;
}

.staff-open.is-empty {
  color: var(--ink-4);
}

.row-action {
  grid-area: action;
  justify-self: end;
  min-width: 64px;
  padding: 5px 10px;
  border: 1px solid var(--ink);
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-align: center;
  transition: background-color var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.row-action:hover {
  background: var(--ink);
  color: var(--paper-raised);
}

/* ---- 预约行 ---- */

.appt-row {
  border-bottom: 1px solid var(--line-soft);
}

.appt-main {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  gap: var(--s2);
  width: 100%;
  padding: 9px 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
}

.appt-main:hover .appt-name {
  color: var(--lacquer);
}

.appt-time {
  color: var(--ink-2);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.appt-body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.appt-name {
  overflow: hidden;
  color: var(--ink);
  font-size: 13px;
  letter-spacing: 0.02em;
  text-overflow: ellipsis;
  transition: color var(--dur-1) var(--ease);
  white-space: nowrap;
}

.appt-meta {
  overflow: hidden;
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.appt-status {
  align-self: flex-start;
  white-space: nowrap;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.tone-pending {
  color: var(--ochre);
}

.tone-confirmed {
  color: var(--moss);
}

.tone-inservice {
  color: var(--lacquer);
}

.tone-completed {
  color: var(--ink-4);
}

.tone-noshow {
  color: var(--lacquer-deep);
}

.tone-cancelled {
  color: var(--ink-4);
}

/* ---- 页脚 ---- */

.panel-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s3);
  margin-top: auto;
  padding-top: var(--s3);
  border-top: 1px solid var(--line);
}

.panel-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.panel-link:hover {
  color: var(--lacquer);
}

.panel-action {
  padding: 0 0 1px;
  border: 0;
  border-bottom: 1px solid var(--ink);
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  letter-spacing: 0.08em;
}

@media (max-width: 1080px) {
  .panel {
    position: static;
    padding-top: var(--s4);
  }
}
</style>
