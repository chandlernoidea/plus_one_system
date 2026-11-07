<script setup>
/**
 * 排期日历：月视图 + 周视图 + 选中那天的明细。
 *
 * 为什么不做「7 列等比时间轴」：390px 手机每列只剩 50px，画出来什么都看不清。
 * 所以格子里的预约统一用彩色横条表示（颜色＝状态），点横条看详情，点格子看当天明细。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SoloLayout from './SoloLayout.vue'
import SoloSheet from '../../components/solo/SoloSheet.vue'
import SoloCalendar from '../../components/solo/SoloCalendar.vue'
import BookingCreateSheet from '../../components/solo/BookingCreateSheet.vue'
import ShiftSheet from '../../components/solo/ShiftSheet.vue'
import * as api from '../../api/solo'
import { addDays, addMonths, fromKey, startOfMonth, startOfWeek, timeText, toKey } from '../../utils/date'

const WEEK_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const OCCUPY = ['BOOKED', 'ARRIVED', 'IN_SERVICE']
const STATUS_LABEL = {
  WAIT_DEPOSIT: '待定金',
  BOOKED: '已定档',
  ARRIVED: '已到店',
  IN_SERVICE: '服务中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  NO_SHOW: '爽约'
}
const CHANGE_LABEL = { SHIFT: '顺延', RESCHEDULE: '改期', JUMP: '插队' }
const DEPOSIT_LABEL = { NONE: '未收', COLLECTED: '已收', REFUNDED: '已退', FORFEITED: '已没收' }

const router = useRouter()
const todayKey = toKey(new Date())

const mode = ref('month')
const anchor = ref(todayKey)
const calendar = ref({})
const selectedDate = ref(todayKey)
const dayDetail = ref(null)
const styles = ref([])
const pending = ref([])
const loading = ref(false)

const jumpVisible = ref(false)
const jumpDate = ref('')
const jumpTime = ref('')
const shiftVisible = ref(false)
const shiftTarget = ref(null)

const detailVisible = ref(false)
const detailBooking = ref(null)

/** 网格首日：月视图从当月 1 号所在周的周一，周视图从本周周一 */
const gridStart = computed(() => {
  const base = mode.value === 'month' ? startOfMonth(fromKey(anchor.value)) : fromKey(anchor.value)
  return toKey(mode.value === 'month' ? startOfWeek(base) : startOfWeek(base))
})

const gridDays = computed(() => {
  const start = fromKey(gridStart.value)
  const count = mode.value === 'month' ? 42 : 7
  const month = fromKey(anchor.value).getMonth()

  return Array.from({ length: count }, (_, index) => {
    const date = addDays(start, index)
    const key = toKey(date)
    return {
      date: key,
      dayNum: date.getDate(),
      dow: WEEK_LABELS[(date.getDay() + 6) % 7],
      isToday: key === todayKey,
      inMonth: date.getMonth() === month,
      bookings: calendar.value[key] || []
    }
  })
})

/** 月表最下面整周都不属于当月就裁掉，别留一条空行 */
const visibleDays = computed(() => {
  if (mode.value !== 'month') {
    return gridDays.value
  }
  const rows = []
  for (let i = 0; i < gridDays.value.length; i += 7) {
    rows.push(gridDays.value.slice(i, i + 7))
  }
  while (rows.length > 1 && rows[rows.length - 1].every((day) => !day.inMonth)) {
    rows.pop()
  }
  return rows.flat()
})

const rangeTotals = computed(() => {
  const list = visibleDays.value.flatMap((day) => day.bookings)
  const active = list.filter((booking) => booking.status !== 'CANCELLED')
  return {
    count: active.length,
    minutes: active.reduce((sum, booking) => sum + (booking.plannedMinutes || 0), 0)
  }
})

/** 现在看的是不是当前周期（月视图比年月，周视图比周一） */
const isCurrentPeriod = computed(() => {
  const anchorDate = fromKey(anchor.value)
  const now = new Date()
  if (mode.value === 'month') {
    return anchorDate.getFullYear() === now.getFullYear() && anchorDate.getMonth() === now.getMonth()
  }
  return toKey(startOfWeek(anchorDate)) === toKey(startOfWeek(now))
})

const title = computed(() => {
  if (mode.value === 'month') {
    const date = fromKey(anchor.value)
    return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月`
  }
  const first = fromKey(gridStart.value)
  const last = addDays(first, 6)
  return `${first.getMonth() + 1}.${first.getDate()} – ${last.getMonth() + 1}.${last.getDate()}`
})

const subtitle = computed(() =>
  `${rangeTotals.value.count} 单 · 约 ${hoursText(rangeTotals.value.minutes)} 小时`
)

function hoursText(minutes) {
  return (minutes / 60).toFixed(minutes % 60 ? 1 : 0)
}

const selectedDay = computed(() => {
  if (!dayDetail.value) {
    return null
  }
  const date = fromKey(dayDetail.value.date)
  return {
    ...dayDetail.value,
    dow: WEEK_LABELS[(date.getDay() + 6) % 7],
    dayNum: date.getDate(),
    isToday: dayDetail.value.date === todayKey,
    active: (dayDetail.value.bookings || []).filter((booking) => booking.status !== 'CANCELLED'),
    minutes: dayDetail.value.stats?.plannedMinutes || 0
  }
})

const selectedLabel = computed(() => {
  if (!selectedDay.value) {
    return ''
  }
  const day = selectedDay.value
  return `${Number(day.date.slice(5, 7))} 月 ${day.dayNum} 日 ${day.dow}${day.isToday ? ' · 今天' : ''}`
})

/** 选中那天的空档与预约，按时间交错 */
const segments = computed(() => {
  if (!selectedDay.value) {
    return []
  }
  const gaps = (selectedDay.value.gaps || []).map((gap) => ({
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

async function loadCalendar() {
  loading.value = true
  try {
    const days = visibleDays.value
    const from = days.length ? days[0].date : gridStart.value
    const to = days.length ? days[days.length - 1].date : gridStart.value
    const response = await api.fetchCalendar(from, to)
    const map = {}
    for (const day of response.data.data.days || []) {
      map[day.date] = day.bookings || []
    }
    calendar.value = map
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    loading.value = false
  }
}

async function loadDay(date) {
  try {
    const response = await api.fetchDaySchedule(date)
    dayDetail.value = response.data.data
  } catch (error) {
    dayDetail.value = null
  }
}

async function loadPending() {
  try {
    const response = await api.fetchPendingChanges()
    pending.value = response.data.data || []
  } catch (error) {
    pending.value = []
  }
}

async function refresh() {
  await Promise.all([loadCalendar(), loadDay(selectedDate.value), loadPending()])
}

onMounted(async () => {
  try {
    const response = await api.fetchStyles({ enabled: 1 })
    styles.value = response.data.data || []
  } catch (error) {
    styles.value = []
  }
  await loadCalendar()
  await loadDay(selectedDate.value)
  await loadPending()
})

function selectDate(date) {
  selectedDate.value = date
  loadDay(date)
}

/**
 * 翻页 / 切视图之后，选中的那一天可能已经不在当前范围里了（明细还停在上一页）。
 * 这里把它拉回来：优先「今天」，其次月视图取 1 号、周视图取周一。
 */
async function ensureSelectedInRange() {
  const days = visibleDays.value.map((day) => day.date)
  if (days.includes(selectedDate.value)) {
    return
  }
  const today = days.includes(todayKey) ? todayKey : null
  const first = mode.value === 'month'
    ? days.find((date) => date.slice(8) === '01') || days[0]
    : days[0]
  const target = today || first
  if (target) {
    selectedDate.value = target
    await loadDay(target)
  }
}

async function shiftRange(direction) {
  const base = fromKey(anchor.value)
  anchor.value = toKey(mode.value === 'month' ? addMonths(base, direction) : addDays(base, direction * 7))
  await loadCalendar()
  await ensureSelectedInRange()
}

function goToday() {
  anchor.value = todayKey
  selectDate(todayKey)
  loadCalendar()
}

async function switchMode(next) {
  mode.value = next
  await loadCalendar()
  await ensureSelectedInRange()
}

function openBooking(booking) {
  detailBooking.value = booking
  detailVisible.value = true
}

function openDay(date) {
  router.push({ path: '/solo/today', query: { date } })
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
  await refresh()
}

async function handleJumped() {
  ElMessage.success('插队成功')
  await refresh()
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

function stylesOf(booking) {
  return (booking?.items || []).map((item) => item.styleName).join(' + ')
}

function amountOf(booking) {
  return (booking?.items || []).reduce((sum, item) => sum + Number(item.price || 0), 0)
}

function toneOf(status) {
  if (status === 'WAIT_DEPOSIT') return 'wait'
  if (status === 'ARRIVED') return 'arrived'
  if (status === 'IN_SERVICE') return 'service'
  if (status === 'NO_SHOW') return 'noshow'
  if (status === 'CANCELLED') return 'void'
  if (status === 'COMPLETED') return 'done'
  return 'booked'
}
</script>

<template>
  <SoloLayout>
    <header class="lede">
      <span class="eyebrow">SOLO · CALENDAR</span>
      <h1 class="lede-title">{{ title }}</h1>
      <p class="lede-note">{{ subtitle }}<span v-if="loading" class="mono"> · 读取中…</span></p>
    </header>

    <div class="toolbar">
      <div class="switch" role="tablist">
        <button type="button" class="switch-btn" :class="{ 'is-active': mode === 'month' }" @click="switchMode('month')">月</button>
        <button type="button" class="switch-btn" :class="{ 'is-active': mode === 'week' }" @click="switchMode('week')">周</button>
      </div>

      <div class="navs">
        <button type="button" class="nav-btn" @click="shiftRange(-1)">‹</button>
        <button
          type="button"
          class="nav-btn nav-btn--now"
          :class="{ 'is-current': isCurrentPeriod }"
          :disabled="isCurrentPeriod"
          @click="goToday"
        >
          {{ mode === 'month' ? '本月' : '本周' }}
        </button>
        <button type="button" class="nav-btn" @click="shiftRange(1)">›</button>
      </div>
    </div>

    <SoloCalendar
      :mode="mode"
      :days="visibleDays"
      :selected="selectedDate"
      @select="selectDate"
      @open="openBooking"
    />

    <!-- 选中那天的空档与预约 -->
    <section class="detail" v-if="selectedDay">
      <header class="detail-head">
        <div class="detail-titles">
          <h2 class="detail-title">{{ selectedLabel }}</h2>
          <p class="detail-note">
            {{ selectedDay.active.length }} 单 · 占用 {{ hoursText(selectedDay.minutes) }} 小时 ·
            空档 {{ (selectedDay.gaps || []).length }} 段
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
            <button type="button" class="slot-main slot-main--btn" @click="openBooking(segment.booking)">
              <span class="slot-client">
                <span class="mono slot-code">{{ segment.booking.clientCode }}</span>
                {{ segment.booking.clientNickname }}
              </span>
              <span class="slot-status" :class="`tone-${toneOf(segment.booking.status)}`">
                {{ STATUS_LABEL[segment.booking.status] || segment.booking.status }}
              </span>
            </button>
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

    <!-- 点横条看详情 -->
    <SoloSheet
      :visible="detailVisible"
      title="这一单"
      note="点「打开作业单」可以到当天页面处理"
      @update:visible="detailVisible = $event"
    >
      <template v-if="detailBooking">
        <div class="d-head">
          <span class="d-code mono">{{ detailBooking.clientCode }}</span>
          <span class="d-name">{{ detailBooking.clientNickname }}</span>
          <span class="d-status" :class="`tone-${toneOf(detailBooking.status)}`">
            {{ STATUS_LABEL[detailBooking.status] || detailBooking.status }}
          </span>
        </div>

        <dl class="d-list">
          <div class="d-row">
            <dt>时间</dt>
            <dd class="mono">
              {{ timeText(detailBooking.startAt) }} – {{ timeText(detailBooking.endAt) }}
              <em>（{{ detailBooking.plannedMinutes }} 分钟 + 缓冲 {{ detailBooking.bufferMinutes }}）</em>
            </dd>
          </div>
          <div class="d-row">
            <dt>项目</dt>
            <dd>{{ stylesOf(detailBooking) || '—' }}</dd>
          </div>
          <div class="d-row">
            <dt>金额</dt>
            <dd>¥{{ amountOf(detailBooking) }}</dd>
          </div>
          <div class="d-row">
            <dt>定金</dt>
            <dd>{{ DEPOSIT_LABEL[detailBooking.depositState] || '未收' }}</dd>
          </div>
          <div class="d-row" v-if="detailBooking.remark">
            <dt>备注</dt>
            <dd>{{ detailBooking.remark }}</dd>
          </div>
        </dl>
      </template>

      <template #footer>
        <button type="button" class="btn" @click="detailVisible = false">关闭</button>
        <button
          v-if="detailBooking"
          type="button"
          class="btn btn-primary wide"
          @click="openDay(detailBooking.startAt.slice(0, 10))"
        >
          打开作业单
        </button>
      </template>
    </SoloSheet>

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

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--s2);
  margin-bottom: var(--s3);
}

.switch {
  display: inline-flex;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 2px;
}

.switch-btn {
  padding: 4px 14px;
  border: 0;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
}

.switch-btn.is-active {
  background: var(--ink);
  color: var(--paper-raised);
}

.navs {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.nav-btn {
  min-width: 34px;
  padding: 4px 9px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
}

.nav-btn:hover:not(:disabled) {
  border-color: var(--ink);
}

.nav-btn--now {
  min-width: 46px;
}

.nav-btn.is-current,
.nav-btn:disabled {
  border-color: var(--line-soft);
  color: var(--ink-4);
  cursor: default;
}

.detail {
  margin-bottom: var(--s4);
}

.detail-head {
  display: flex;
  align-items: flex-start;
  gap: var(--s2);
  margin-bottom: var(--s2);
}

.detail-titles {
  min-width: 0;
}

.detail-title {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 400;
}

.detail-note {
  color: var(--ink-3);
  font-size: 11px;
}

.enter {
  flex: none;
  margin-left: auto;
  padding: 4px 10px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
}

.empty-line {
  padding: var(--s3) 0;
  color: var(--ink-4);
  font-size: 12px;
}

.empty-line em {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  font-style: normal;
}

.slots {
  list-style: none;
}

.slot {
  display: flex;
  align-items: center;
  gap: var(--s2);
  padding: 6px 0;
  border-bottom: 1px solid var(--line-soft);
}

.slot-time {
  flex: none;
  width: 44px;
  color: var(--ink-3);
  font-size: 10px;
  line-height: 1.5;
}

.slot-time em {
  display: block;
  color: var(--ink-4);
  font-style: normal;
}

.slot-main {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  gap: var(--s2);
  min-width: 0;
}

.slot-main--btn {
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: inherit;
  font-size: inherit;
  text-align: left;
}

.slot-client {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slot-code {
  margin-right: 4px;
  color: var(--ink-4);
  font-size: 10px;
}

.slot-status {
  flex: none;
  color: var(--ink-3);
  font-size: 10px;
}

.slot-status.tone-wait { color: var(--ochre); }
.slot-status.tone-booked { color: #3a5a7a; }
.slot-status.tone-arrived { color: #2f6b6b; }
.slot-status.tone-service { color: var(--moss); }
.slot-status.tone-done { color: var(--ink-4); }
.slot-status.tone-noshow { color: var(--lacquer); }

.slot-action {
  flex: none;
  padding: 3px 9px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
}

.gap-main {
  color: var(--ink-3);
  font-size: 11px;
}

.gap-label {
  letter-spacing: 0.1em;
}

.pending {
  margin-bottom: var(--s4);
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
  justify-content: space-between;
  gap: var(--s2);
}

.pending-name {
  font-size: 13px;
}

.pending-type {
  color: var(--ink-4);
  font-size: 10px;
}

.pending-time {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 11px;
}

.pending-actions {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

/* ---------- 详情抽屉 ---------- */
.d-head {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  margin-bottom: var(--s3);
}

.d-code {
  color: var(--ink-4);
  font-size: 11px;
}

.d-name {
  font-family: var(--font-display);
  font-size: 19px;
}

.d-status {
  margin-left: auto;
  font-size: 11px;
}

.d-status.tone-wait { color: var(--ochre); }
.d-status.tone-booked { color: #3a5a7a; }
.d-status.tone-arrived { color: #2f6b6b; }
.d-status.tone-service { color: var(--moss); }
.d-status.tone-done { color: var(--ink-4); }
.d-status.tone-noshow { color: var(--lacquer); }

.d-list {
  margin: 0;
}

.d-row {
  display: flex;
  gap: var(--s2);
  padding: 6px 0;
  border-bottom: 1px solid var(--line-soft);
}

.d-row dt {
  flex: none;
  width: 42px;
  color: var(--ink-4);
  font-size: 11px;
}

.d-row dd {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}

.d-row dd em {
  color: var(--ink-4);
  font-size: 10px;
  font-style: normal;
}

.btn {
  padding: 6px 14px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
}

.btn-primary {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
}

.wide {
  flex: 1 1 auto;
}

@media (min-width: 900px) {
  .detail,
  .pending {
    max-width: 820px;
  }
}
</style>
