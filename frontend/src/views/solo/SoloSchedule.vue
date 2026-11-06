<script setup>
/**
 * 周排期（P4）。
 *
 * 390px 下不做「7 列等比时间轴」——每列只剩 50px，画出来什么都看不清。
 * 这一页的形态是：上面 7 列看整周密度，下面只展开选中那一天的
 * 空档（可插队）与预约（可作顺延起点），右上角进当天作业单。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SoloLayout from './SoloLayout.vue'
import BookingCreateSheet from '../../components/solo/BookingCreateSheet.vue'
import ShiftSheet from '../../components/solo/ShiftSheet.vue'
import * as api from '../../api/solo'
import { addDays, fromKey, startOfWeek, timeText, toKey } from '../../utils/date'

const WEEK_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const OCCUPY = ['BOOKED', 'ARRIVED', 'IN_SERVICE']
const STATUS_LABEL = {
  WAIT_DEPOSIT: '待定金',
  BOOKED: '已定档',
  ARRIVED: '已到店',
  IN_SERVICE: '服务中',
  COMPLETED: '已完成',
  NO_SHOW: '爽约'
}
const CHANGE_LABEL = { SHIFT: '顺延', RESCHEDULE: '改期', JUMP: '插队' }

const router = useRouter()

const weekStart = ref(toKey(startOfWeek(new Date())))
const week = ref(null)
const styles = ref([])
const pending = ref([])
const selectedDate = ref(toKey(new Date()))
const loading = ref(false)

const jumpVisible = ref(false)
const jumpDate = ref('')
const jumpTime = ref('')
const shiftVisible = ref(false)
const shiftTarget = ref(null)

async function load() {
  loading.value = true
  try {
    const [weekResponse, styleResponse, pendingResponse] = await Promise.all([
      api.fetchWeekSchedule(weekStart.value),
      api.fetchStyles({ enabled: 1 }),
      api.fetchPendingChanges()
    ])
    week.value = weekResponse.data.data
    styles.value = styleResponse.data.data || []
    pending.value = pendingResponse.data.data || []

    const dates = (week.value?.days || []).map((day) => day.date)
    if (!dates.includes(selectedDate.value)) {
      selectedDate.value = dates.includes(toKey(new Date())) ? toKey(new Date()) : dates[0]
    }
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    loading.value = false
  }
}

onMounted(load)

const days = computed(() =>
  (week.value?.days || []).map((day, index) => {
    const active = (day.bookings || []).filter((booking) => booking.status !== 'CANCELLED')
    return {
      ...day,
      dow: WEEK_LABELS[index],
      dayNum: Number(day.date.slice(8)),
      isToday: day.date === toKey(new Date()),
      active,
      count: active.length,
      minutes: day.stats?.plannedMinutes || 0,
      gaps: day.gaps || []
    }
  })
)

const selectedDay = computed(() => days.value.find((day) => day.date === selectedDate.value) || null)

const weekTotals = computed(() => ({
  count: days.value.reduce((sum, day) => sum + day.count, 0),
  minutes: days.value.reduce((sum, day) => sum + day.minutes, 0),
  gaps: days.value.reduce((sum, day) => sum + day.gaps.length, 0)
}))

const hoursText = (minutes) => (minutes / 60).toFixed(minutes % 60 ? 1 : 0)

const weekLabel = computed(() => {
  if (!days.value.length) {
    return ''
  }
  const first = days.value[0].date
  const last = days.value[days.value.length - 1].date
  return `${Number(first.slice(5, 7))}.${first.slice(8)} – ${Number(last.slice(5, 7))}.${last.slice(8)}`
})

const selectedLabel = computed(() => {
  if (!selectedDay.value) {
    return ''
  }
  const day = selectedDay.value
  return `${Number(day.date.slice(5, 7))} 月 ${day.dayNum} 日 ${day.dow}${day.isToday ? ' · 今天' : ''}`
})

/** 选中那天的空档与预约，按时间交错。 */
const segments = computed(() => {
  if (!selectedDay.value) {
    return []
  }
  const gaps = selectedDay.value.gaps.map((gap) => ({
    type: 'gap',
    start: gap.start,
    end: gap.end,
    minutes: gap.minutes
  }))
  const bookings = selectedDay.value.active.map((booking) => ({
    type: 'booking',
    start: booking.startAt,
    end: booking.endAt,
    booking
  }))
  return [...gaps, ...bookings].sort((a, b) => (a.start < b.start ? -1 : 1))
})

function toneOf(status) {
  if (status === 'WAIT_DEPOSIT') {
    return 'wait'
  }
  if (status === 'BOOKED') {
    return 'booked'
  }
  if (status === 'ARRIVED') {
    return 'arrived'
  }
  if (status === 'IN_SERVICE') {
    return 'service'
  }
  return 'done'
}

function selectDay(date) {
  selectedDate.value = date
}

function openDay(date) {
  router.push({ path: '/solo/today', query: { date } })
}

function shiftWeek(weeks) {
  weekStart.value = toKey(addDays(fromKey(weekStart.value), weeks * 7))
  load()
}

function backToThisWeek() {
  weekStart.value = toKey(startOfWeek(new Date()))
  load()
}

function openJump(start) {
  jumpDate.value = selectedDate.value
  jumpTime.value = timeText(start)
  jumpVisible.value = true
}

function openShift(booking) {
  shiftTarget.value = booking
  shiftVisible.value = true
}

async function handleShifted(result) {
  ElMessage.success(`已顺延 ${result.count} 单，生成待通知清单`)
  shiftTarget.value = null
  await load()
}

async function markNotified(change) {
  try {
    await api.markChangeNotified(change.id)
    pending.value = pending.value.filter((item) => item.id !== change.id)
    ElMessage.success('已标记')
  } catch (error) {
    // 提示已由拦截器统一给出
  }
}

function changeText(change) {
  const from = timeText(change.oldStart)
  const to = timeText(change.newStart)
  return `Hi ${change.clientNickname}，${CHANGE_LABEL[change.type] || '时间调整'}：${from} 改到 ${to}，给你添麻烦了，你看这样可以吗？`
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
  } catch (error) {
    const area = document.createElement('textarea')
    area.value = text
    document.body.appendChild(area)
    area.select()
    document.execCommand('copy')
    document.body.removeChild(area)
  }
  ElMessage.success('话术已复制')
}

async function handleJumped() {
  ElMessage.success('插队成功')
  await load()
}
</script>

<template>
  <SoloLayout>
    <header class="lede">
      <span class="eyebrow">SOLO · WEEK</span>
      <h1 class="lede-title">本周 {{ weekTotals.count }} 单</h1>
      <p class="lede-note">
        {{ weekLabel }} · 占用 {{ hoursText(weekTotals.minutes) }} 小时 · 空档 {{ weekTotals.gaps }} 段
      </p>
    </header>

    <div class="week-nav">
      <button type="button" class="nav-btn" @click="shiftWeek(-1)">‹ 上周</button>
      <button type="button" class="nav-btn" @click="backToThisWeek">本周</button>
      <button type="button" class="nav-btn" @click="shiftWeek(1)">下周 ›</button>
      <span v-if="loading" class="nav-loading mono">读取中…</span>
    </div>

    <!-- 周视图：7 列看密度 -->
    <section class="week" aria-label="本周排期">
      <button
        v-for="day in days"
        :key="day.date"
        type="button"
        class="day-col"
        :class="{ 'is-selected': day.date === selectedDate, 'is-today': day.isToday }"
        @click="selectDay(day.date)"
      >
        <span class="dow">{{ day.dow }}</span>
        <span class="dnum mono">{{ day.dayNum }}</span>
        <span class="dcount mono">{{ day.count || '·' }}</span>
        <span class="mini">
          <span
            v-for="booking in day.active.slice(0, 3)"
            :key="booking.id"
            class="mini-block"
            :class="`tone-${toneOf(booking.status)}`"
          >
            {{ timeText(booking.startAt) }}
          </span>
          <span v-if="day.active.length > 3" class="mini-more mono">+{{ day.active.length - 3 }}</span>
        </span>
      </button>
    </section>

    <!-- 选中那天的空档与预约 -->
    <section class="detail" v-if="selectedDay">
      <header class="detail-head">
        <div class="detail-titles">
          <h2 class="detail-title">{{ selectedLabel }}</h2>
          <p class="detail-note">
            {{ selectedDay.count }} 单 · 占用 {{ hoursText(selectedDay.minutes) }} 小时 ·
            空档 {{ selectedDay.gaps.length }} 段
          </p>
        </div>
        <button type="button" class="enter" @click="openDay(selectedDay.date)">作业单 ↗</button>
      </header>

      <p v-if="!segments.length" class="empty-line">
        这天还没排单。
        <em>点右上角「作业单」可以直接建单。</em>
      </p>

      <ul class="slots" v-else>
        <li v-for="segment in segments" :key="`${segment.type}-${segment.start}`" class="slot">
          <span class="slot-time mono">
            {{ timeText(segment.start) }}
            <em>{{ timeText(segment.end) }}</em>
          </span>

          <template v-if="segment.type === 'gap'">
            <span class="slot-main gap-main">
              <span class="gap-label">空档</span>
              <span class="gap-value">可塞 {{ segment.minutes }} 分钟</span>
            </span>
            <button type="button" class="slot-action" @click="openJump(segment.start)">插一单</button>
          </template>

          <template v-else>
            <span class="slot-main">
              <span class="slot-client">
                <span class="mono slot-code">{{ segment.booking.clientCode }}</span>
                {{ segment.booking.clientNickname }}
              </span>
              <span class="slot-status" :class="`tone-${toneOf(segment.booking.status)}`">
                {{ STATUS_LABEL[segment.booking.status] || segment.booking.status }}
              </span>
            </span>
            <button
              v-if="OCCUPY.includes(segment.booking.status)"
              type="button"
              class="slot-action"
              @click="openShift(segment.booking)"
            >
              顺延
            </button>
          </template>
        </li>
      </ul>
    </section>

    <!-- 待通知清单 -->
    <section class="pending">
      <div class="pending-head">
        <span class="pending-title">待通知清单</span>
        <span class="pending-count mono">{{ pending.length }}</span>
        <span class="pending-line"></span>
      </div>

      <p v-if="!pending.length" class="empty-line">
        没有待通知的变更。
        <em>顺延之后，这里会出现要发微信的客户。</em>
      </p>

      <article v-for="change in pending" :key="change.id" class="pending-item">
        <div class="pending-row">
          <span class="pending-name">
            <span class="mono">{{ change.clientCode }}</span> {{ change.clientNickname }}
          </span>
          <span class="pending-type">{{ CHANGE_LABEL[change.type] || change.type }}</span>
        </div>
        <p class="pending-time mono">
          {{ timeText(change.oldStart) }} → {{ timeText(change.newStart) }}
        </p>
        <div class="pending-actions">
          <button type="button" class="slot-action" @click="copyText(changeText(change))">复制话术</button>
          <button type="button" class="slot-action" @click="markNotified(change)">已发微信</button>
        </div>
      </article>
    </section>

    <BookingCreateSheet
      :visible="jumpVisible"
      mode="jump"
      :date="jumpDate"
      :preset-time="jumpTime"
      :styles="styles"
      @update:visible="jumpVisible = $event"
      @created="handleJumped"
    />

    <ShiftSheet
      :visible="shiftVisible"
      :date="selectedDate"
      :from-time="shiftTarget?.startAt || ''"
      :booking-label="shiftTarget ? `${shiftTarget.clientCode} ${shiftTarget.clientNickname}` : ''"
      @update:visible="shiftVisible = $event"
      @shifted="handleShifted"
    />
  </SoloLayout>
</template>

<style scoped>
.lede {
  margin-bottom: var(--s3);
}

.lede-title {
  margin-top: 2px;
  font-family: var(--font-display);
  font-size: 23px;
  font-weight: 400;
  letter-spacing: 0.01em;
  line-height: 1.25;
}

.lede-note {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 11px;
}

.week-nav {
  display: flex;
  align-items: center;
  gap: var(--s2);
  margin-bottom: var(--s3);
}

.nav-btn {
  padding: 3px 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
}

.nav-btn:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.nav-loading {
  margin-left: auto;
  color: var(--ink-4);
  font-size: 10px;
}

/* ---- 7 列 ---- */

.week {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 3px;
  margin-bottom: var(--s4);
}

.day-col {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: stretch;
  gap: 1px;
  padding: 5px 3px 6px;
  border: 1px solid var(--line);
  border-top-width: 2px;
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-ui);
  text-align: center;
}

.day-col.is-today {
  border-top-color: var(--lacquer);
}

.day-col.is-selected {
  border-color: var(--ink);
  background: var(--paper-raised);
}

.dow {
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.04em;
}

.dnum {
  font-family: var(--font-display);
  font-size: 15px;
  line-height: 1.2;
}

.dcount {
  color: var(--ink-4);
  font-size: 8px;
  line-height: 1.2;
}

.mini {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 2px;
  margin-top: 3px;
}

.mini-block {
  width: 100%;
  min-width: 0;
  overflow: hidden;
  padding: 1px 0 1px 2px;
  border-left: 2px solid var(--ink-4);
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 7.5px;
  line-height: 1.35;
  text-overflow: clip;
  white-space: nowrap;
}

.mini-more {
  color: var(--ink-4);
  font-size: 7.5px;
}

.tone-wait {
  border-left-color: var(--ochre);
}

.tone-booked {
  border-left-color: var(--moss);
}

.tone-arrived {
  border-left-color: var(--slate);
}

.tone-service {
  border-left-color: var(--lacquer);
}

.tone-done {
  border-left-color: var(--ink-4);
}

/* ---- 选中日详情 ---- */

.detail {
  margin-bottom: var(--s5);
}

.detail-head {
  display: flex;
  align-items: flex-start;
  gap: var(--s2);
  padding-bottom: var(--s2);
  border-bottom: 1px solid var(--line);
}

.detail-titles {
  min-width: 0;
}

.detail-title {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

.detail-note {
  color: var(--ink-4);
  font-size: 10px;
}

.enter {
  flex: none;
  margin-left: auto;
  padding: 4px 10px;
  border: 1px solid var(--ink);
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  white-space: nowrap;
}

.enter:hover {
  background: var(--ink);
  color: var(--paper-raised);
}

.slots {
  margin: 0;
  padding: 0;
  list-style: none;
}

.slot {
  display: flex;
  align-items: center;
  gap: var(--s2);
  padding: 7px 0;
  border-bottom: 1px solid var(--line-soft);
}

.slot-time {
  flex: none;
  width: 34px;
  color: var(--ink-2);
  font-size: 10px;
  line-height: 1.3;
}

.slot-time em {
  display: block;
  color: var(--ink-4);
  font-size: 8px;
  font-style: normal;
}

.slot-main {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  align-items: baseline;
  gap: var(--s2);
}

.gap-main {
  color: var(--ink-3);
}

.gap-label {
  font-family: var(--font-display);
  font-size: 12px;
}

.gap-value {
  font-size: 10px;
}

.slot-client {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slot-code {
  color: var(--ink-4);
  font-size: 9px;
}

.slot-status {
  flex: none;
  font-size: 9px;
  letter-spacing: 0.04em;
}

.slot-status.tone-booked {
  color: var(--moss);
}

.slot-status.tone-wait {
  color: var(--ochre);
}

.slot-status.tone-service {
  color: var(--lacquer);
}

.slot-status.tone-arrived {
  color: var(--slate);
}

.slot-status.tone-done {
  color: var(--ink-4);
}

.slot-action {
  flex: none;
  padding: 2px 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 10px;
  white-space: nowrap;
}

.slot-action:hover {
  border-color: var(--ink);
  color: var(--ink);
}

/* ---- 待通知 ---- */

.pending {
  border-top: 1px solid var(--line);
  padding-top: var(--s3);
}

.pending-head {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  margin-bottom: var(--s2);
}

.pending-title {
  font-family: var(--font-display);
  font-size: 15px;
  letter-spacing: 0.04em;
}

.pending-count {
  color: var(--lacquer);
  font-size: 11px;
}

.pending-line {
  flex: 1 1 auto;
  height: 1px;
  background: var(--line);
}

.pending-item {
  padding: var(--s2) 0;
  border-bottom: 1px solid var(--line-soft);
}

.pending-row {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
}

.pending-name {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pending-type {
  flex: none;
  color: var(--lacquer);
  font-size: 9px;
  letter-spacing: 0.06em;
}

.pending-time {
  margin-top: 1px;
  color: var(--ink-3);
  font-size: 11px;
}

.pending-actions {
  display: flex;
  gap: var(--s2);
  margin-top: var(--s2);
}

.empty-line {
  padding: var(--s3) 0;
  color: var(--ink-3);
  font-size: 11px;
}

.empty-line em {
  display: block;
  margin-top: 2px;
  color: var(--ink-4);
  font-size: 10px;
  font-style: normal;
}

.mono {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 360px) {
  .mini-block {
    font-size: 7px;
  }

  .dnum {
    font-size: 13px;
  }
}
</style>
