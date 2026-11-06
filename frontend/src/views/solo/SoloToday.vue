<script setup>
/**
 * 今日作业单（单人版首页）。
 *
 * 版面顺序按一天的实际动作排：抬头看总量 → 时间轴看顺序 → 提醒看还要发谁微信。
 * 时间轴不是按比例画的长条，而是「时间 + 卡片」的紧凑流水：
 * 390px 下一屏能看完当天所有单，空档只占一行。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import SoloLayout from './SoloLayout.vue'
import SoloSheet from '../../components/solo/SoloSheet.vue'
import BookingCard from '../../components/solo/BookingCard.vue'
import BookingCreateSheet from '../../components/solo/BookingCreateSheet.vue'
import RescheduleSheet from '../../components/solo/RescheduleSheet.vue'
import * as api from '../../api/solo'
import { addDays, formatMoney, minutesOf, timeText, toKey } from '../../utils/date'

const route = useRoute()

/** 从周视图点进来时带着 ?date=YYYY-MM-DD，直接看那一天。 */
function initialDate() {
  const query = route.query.date
  return typeof query === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(query) ? query : toKey(new Date())
}

const date = ref(initialDate())
const loading = ref(false)
const schedule = ref(null)
const styles = ref([])
const styleMap = ref({})
const usageMap = ref({})
const tomorrow = ref(null)

const createVisible = ref(false)
const presetTime = ref('')

const rescheduleVisible = ref(false)
const rescheduleTarget = ref(null)

const actionVisible = ref(false)
const actionMode = ref('cancel')
const actionTarget = ref(null)
const actionReason = ref('')
const actionDecision = ref('REFUND')

const remindersOpen = ref(false)
const acting = ref(false)

// ------------------------------------------------------------------ 数据

async function load() {
  loading.value = true
  try {
    const [dayResponse, styleResponse] = await Promise.all([
      api.fetchDaySchedule(date.value),
      api.fetchStyles({ enabled: 1 })
    ])
    schedule.value = dayResponse.data.data
    styles.value = styleResponse.data.data || []
    styleMap.value = Object.fromEntries(styles.value.map((style) => [style.id, style]))
    await loadUsage()
  } catch (error) {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
  loadTomorrow()
}

/** 每单领用了几件料。单量小，逐单取比新增聚合接口更省事。 */
async function loadUsage() {
  const bookings = schedule.value?.bookings || []
  const entries = await Promise.all(
    bookings.map(async (booking) => {
      try {
        const response = await api.fetchBookingMaterials(booking.id)
        return [booking.id, (response.data.data || []).length]
      } catch (error) {
        return [booking.id, 0]
      }
    })
  )
  usageMap.value = Object.fromEntries(entries)
}

async function loadTomorrow() {
  try {
    const response = await api.fetchDaySchedule(toKey(addDays(new Date(), 1)))
    tomorrow.value = response.data.data
  } catch (error) {
    tomorrow.value = null
  }
}

onMounted(load)

watch(date, load)

// ------------------------------------------------------------------ 派生

const activeBookings = computed(() =>
  (schedule.value?.bookings || []).filter((booking) => booking.status !== 'CANCELLED')
)

const cancelledCount = computed(
  () => (schedule.value?.bookings || []).filter((booking) => booking.status === 'CANCELLED').length
)

const stats = computed(() => schedule.value?.stats || {})

/**
 * 强排撞档的检测：占用状态的单两两比时间，落在同一段里的都标出来。
 * 单量小（一天十几单顶天），O(n²) 足够。
 */
const conflictIds = computed(() => {
  const occupying = activeBookings.value.filter((booking) =>
    ['BOOKED', 'ARRIVED', 'IN_SERVICE'].includes(booking.status)
  )
  const ids = new Set()
  for (let i = 0; i < occupying.length; i += 1) {
    for (let j = i + 1; j < occupying.length; j += 1) {
      const a = occupying[i]
      const b = occupying[j]
      if (minutesOf(a.startAt) < minutesOf(b.endAt) && minutesOf(b.startAt) < minutesOf(a.endAt)) {
        ids.add(a.id)
        ids.add(b.id)
      }
    }
  }
  return ids
})

const windowLabel = computed(() => {
  const windows = schedule.value?.openWindows || []
  if (!windows.length) {
    return '08:30–22:30'
  }
  const first = windows[0]
  const last = windows[windows.length - 1]
  return `${timeText(first.start)}–${timeText(last.end)}`
})

const hoursLabel = computed(() => {
  const minutes = stats.value.plannedMinutes || 0
  if (!minutes) {
    return '0'
  }
  return (minutes / 60).toFixed(minutes % 60 === 0 ? 0 : 1)
})

const isToday = computed(() => date.value === toKey(new Date()))

/** 时间轴：空档与预约按开始时间交错排列。 */
const segments = computed(() => {
  if (!schedule.value) {
    return []
  }
  const gaps = (schedule.value.gaps || []).map((gap) => ({
    type: 'gap',
    start: gap.start,
    end: gap.end,
    minutes: gap.minutes
  }))
  const bookings = activeBookings.value.map((booking) => ({
    type: 'booking',
    start: booking.startAt,
    end: booking.endAt,
    booking
  }))
  return [...gaps, ...bookings].sort((a, b) => minutesOf(a.start) - minutesOf(b.start))
})

function coverOf(booking) {
  const first = (booking.items || []).find((item) => item.styleId)
  const style = first ? styleMap.value[first.styleId] : null
  return style?.coverUrl || ''
}

// ------------------------------------------------------------------ 提醒

/**
 * 「今天要发的提醒」：今日未到店 + 明日预约，每条配一句可直接发的话术。
 * 昨日未确认那部分依赖 P7 的提醒接口，这里先不做。
 */
const reminders = computed(() => {
  const list = []
  const now = new Date()
  const nowMinutes = now.getHours() * 60 + now.getMinutes()

  const push = (booking, when) => {
    const styleNames = (booking.items || []).map((item) => item.styleName).join(' + ')
    const what = styleNames ? `到店做${styleNames}` : '到店'
    const time = timeText(booking.startAt)
    const name = booking.clientNickname
    const text =
      booking.status === 'WAIT_DEPOSIT'
        ? `Hi ${name}，${when} ${time} 的档期先给你留着啦，定金 30 微信转我就锁档～`
        : `Hi ${name}，提醒一下${when} ${time} ${what}，我这边把档期留着，有变化提前说一声～`
    list.push({ key: `${when}-${booking.id}`, when, time, code: booking.clientCode, name, text })
  }

  // 今天只提醒还没开始的单，已经过点的不再发
  activeBookings.value
    .filter((booking) => ['BOOKED', 'WAIT_DEPOSIT'].includes(booking.status))
    .filter((booking) => minutesOf(booking.startAt) >= nowMinutes)
    .forEach((booking) => push(booking, '今天'))

  ;(tomorrow.value?.bookings || [])
    .filter((booking) => ['BOOKED', 'WAIT_DEPOSIT'].includes(booking.status))
    .forEach((booking) => push(booking, '明天'))

  return list
})

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

// ------------------------------------------------------------------ 操作

function openCreate(time = '') {
  presetTime.value = time
  createVisible.value = true
}

function openReschedule(booking) {
  rescheduleTarget.value = booking
  rescheduleVisible.value = true
}

function openAction(booking, mode) {
  actionTarget.value = booking
  actionMode.value = mode
  actionReason.value = ''
  actionDecision.value = 'REFUND'
  actionVisible.value = true
}

async function runAction(booking, key) {
  if (acting.value) {
    return
  }
  const handlers = {
    arrive: { call: () => api.arriveBooking(booking.id), message: '已到店' },
    start: { call: () => api.startBooking(booking.id), message: '开始服务' },
    complete: { call: () => api.completeBooking(booking.id), message: '已完成' },
    deposit: { call: () => api.collectDeposit(booking.id, { type: 'COLLECT' }), message: '定金已记' }
  }
  const handler = handlers[key]
  if (!handler) {
    return
  }
  acting.value = true
  try {
    await handler.call()
    ElMessage.success(handler.message)
    await load()
  } catch (error) {
    // 提示已由拦截器处理
  } finally {
    acting.value = false
  }
}

function handleCardAction(booking, key) {
  if (key === 'reschedule') {
    openReschedule(booking)
    return
  }
  if (key === 'cancel') {
    openAction(booking, 'cancel')
    return
  }
  if (key === 'no-show') {
    openAction(booking, 'no-show')
    return
  }
  runAction(booking, key)
}

async function confirmAction() {
  if (!actionTarget.value || acting.value) {
    return
  }
  acting.value = true
  try {
    if (actionMode.value === 'no-show') {
      await api.noShowBooking(actionTarget.value.id)
      ElMessage.success('已记爽约')
    } else {
      await api.cancelBooking(actionTarget.value.id, {
        reason: actionReason.value.trim() || undefined,
        depositDecision: actionDecision.value
      })
      ElMessage.success('已取消')
    }
    actionVisible.value = false
    await load()
  } catch (error) {
    // 提示已由拦截器处理
  } finally {
    acting.value = false
  }
}

function shiftDay(amount) {
  date.value = toKey(addDays(new Date(`${date.value}T00:00:00`), amount))
}

function goToday() {
  date.value = toKey(new Date())
}

async function handleCreated() {
  ElMessage.success('建单完成')
  presetTime.value = ''
  await load()
}

async function handleRescheduled() {
  ElMessage.success('改期完成')
  await load()
}

const dateTitle = computed(() => {
  const [, month, day] = date.value.split('-')
  return `${Number(month)} 月 ${Number(day)} 日`
})

const weekdayText = computed(() => {
  const weekday = ['日', '一', '二', '三', '四', '五', '六'][
    new Date(`${date.value}T00:00:00`).getDay()
  ]
  return `周${weekday}`
})
</script>

<template>
  <SoloLayout>
    <!-- 抬头 -->
    <header class="lede">
      <span class="eyebrow">SOLO · TODAY</span>
      <h1 class="lede-title">
        <template v-if="loading">正在读排期…</template>
        <template v-else-if="stats.count">
          {{ isToday ? '今天' : dateTitle }} {{ stats.count }} 单
        </template>
        <template v-else>{{ isToday ? '今天' : dateTitle }}还没有单</template>
      </h1>
      <p class="lede-note">
        {{ weekdayText }} · 营业 {{ windowLabel }}
        <template v-if="stats.count"> · 占用 {{ hoursLabel }} 小时</template>
      </p>
      <p v-if="conflictIds.size" class="conflict-badge">
        ⚠ {{ conflictIds.size }} 单时段冲突，已标红
      </p>

      <dl class="stats">
        <div class="stat">
          <dt>单量</dt>
          <dd class="num">{{ stats.count || 0 }}</dd>
        </div>
        <div class="stat">
          <dt>占用</dt>
          <dd class="num">{{ hoursLabel }}<small>h</small></dd>
        </div>
        <div class="stat">
          <dt>定金</dt>
          <dd class="num">
            <small class="yuan">¥</small>{{ formatMoney(stats.depositCollected || 0) }}
          </dd>
        </div>
        <div class="stat">
          <dt>待采购</dt>
          <dd class="num">{{ stats.purchasePending || 0 }}</dd>
        </div>
      </dl>
    </header>

    <!-- 日期与建单 -->
    <div class="toolbar">
      <button type="button" class="date-step" @click="shiftDay(-1)">‹ 前一天</button>
      <button type="button" class="date-now" :class="{ 'is-off': !isToday }" @click="goToday">
        {{ isToday ? '今天' : '回今天' }}
      </button>
      <button type="button" class="date-step" @click="shiftDay(1)">后一天 ›</button>
      <button type="button" class="create-btn" @click="openCreate('')">＋ 建一单</button>
    </div>

    <!-- 时间轴 -->
    <section class="timeline" aria-label="当天排期">
      <p v-if="loading" class="empty-note">正在加载…</p>

      <div v-else-if="!segments.length" class="empty-note">
        这一天是空的。
        <em>点「建一单」把第一单排进去，占用会自动算上缓冲。</em>
      </div>

      <div v-for="segment in segments" :key="`${segment.type}-${segment.start}`" class="seg">
        <div class="gut">
          <span class="gut-time mono">{{ timeText(segment.start) }}</span>
          <span class="gut-end mono">{{ timeText(segment.end) }}</span>
        </div>
        <div class="rail" aria-hidden="true"><span class="rail-dot"></span></div>

        <div class="seg-body">
          <button v-if="segment.type === 'gap'" type="button" class="gap" @click="openCreate(timeText(segment.start))">
            <span class="gap-label">空档</span>
            <span class="gap-value">可塞 {{ segment.minutes }} 分钟</span>
            <span class="gap-add">插一单 ↗</span>
          </button>

          <BookingCard
            v-else
            :booking="segment.booking"
            :cover="coverOf(segment.booking)"
            :usage-count="usageMap[segment.booking.id] || 0"
            :conflicting="conflictIds.has(segment.booking.id)"
            @arrive="handleCardAction(segment.booking, 'arrive')"
            @start="handleCardAction(segment.booking, 'start')"
            @complete="handleCardAction(segment.booking, 'complete')"
            @deposit="handleCardAction(segment.booking, 'deposit')"
            @reschedule="handleCardAction(segment.booking, 'reschedule')"
            @cancel="handleCardAction(segment.booking, 'cancel')"
            @no-show="handleCardAction(segment.booking, 'no-show')"
          />
        </div>
      </div>

      <p v-if="cancelledCount" class="void-note">
        另有 {{ cancelledCount }} 单已取消，未占档期。
      </p>
    </section>

    <!-- 提醒 -->
    <section class="reminders">
      <button type="button" class="fold" @click="remindersOpen = !remindersOpen">
        <span class="fold-title">今天要发的提醒</span>
        <span class="fold-count mono">{{ reminders.length }}</span>
        <span class="fold-arrow" :class="{ 'is-open': remindersOpen }">⌄</span>
      </button>

      <div v-if="remindersOpen" class="fold-body">
        <p v-if="!reminders.length" class="empty-line">今天没有要发的微信，安心做指甲。</p>
        <article v-for="item in reminders" :key="item.key" class="reminder">
          <div class="reminder-head">
            <span class="reminder-when">{{ item.when }} {{ item.time }}</span>
            <span class="reminder-name">{{ item.code }} {{ item.name }}</span>
          </div>
          <p class="reminder-text">{{ item.text }}</p>
          <button type="button" class="copy-btn" @click="copyText(item.text)">复制话术</button>
        </article>
      </div>
    </section>

    <!-- 抽屉 -->
    <BookingCreateSheet
      :visible="createVisible"
      :date="date"
      :preset-time="presetTime"
      :styles="styles"
      @update:visible="createVisible = $event"
      @created="handleCreated"
    />

    <RescheduleSheet
      :visible="rescheduleVisible"
      :booking="rescheduleTarget"
      @update:visible="rescheduleVisible = $event"
      @updated="handleRescheduled"
    />

    <SoloSheet
      :visible="actionVisible"
      :title="actionMode === 'no-show' ? '记爽约' : '取消这一单'"
      :note="
        actionTarget
          ? `${actionTarget.clientCode} ${actionTarget.clientNickname} · ${timeText(actionTarget.startAt)} 开始`
          : ''
      "
      @update:visible="actionVisible = $event"
    >
      <div class="field">
        <div class="field-head"><span class="field-label">原因</span></div>
        <input v-model="actionReason" class="input" type="text" placeholder="客户原因 / 店铺原因 / 纠纷" />
      </div>

      <div v-if="actionMode === 'cancel'" class="field">
        <div class="field-head">
          <span class="field-label">定金怎么处理</span>
          <span class="field-hint">已收 {{ formatMoney(actionTarget?.depositCollected || 0) }} 元</span>
        </div>
        <div class="decisions">
          <label class="radio">
            <input v-model="actionDecision" type="radio" value="REFUND" />
            <span>退定</span>
          </label>
          <label class="radio">
            <input v-model="actionDecision" type="radio" value="FORFEIT" />
            <span>没收</span>
          </label>
          <label class="radio">
            <input v-model="actionDecision" type="radio" value="NONE" />
            <span>先不动</span>
          </label>
        </div>
      </div>

      <template #footer>
        <button type="button" class="btn" @click="actionVisible = false">再想想</button>
        <button type="button" class="btn btn-primary wide" :disabled="acting" @click="confirmAction">
          {{ acting ? '处理中…' : actionMode === 'no-show' ? '确认爽约' : '确认取消' }}
        </button>
      </template>
    </SoloSheet>
  </SoloLayout>
</template>

<style scoped>
/* ---- 抬头 ---- */

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
  margin-top: var(--s1);
  color: var(--ink-3);
  font-size: 11px;
  letter-spacing: 0.02em;
}

.conflict-badge {
  display: inline-block;
  margin-top: var(--s2);
  padding: 2px 8px;
  border: 1px solid var(--lacquer);
  color: var(--lacquer-deep);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--s2);
  margin: var(--s3) 0 0;
  padding: var(--s2) 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.stat {
  min-width: 0;
}

.stat dt {
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.14em;
}

.stat dd {
  margin: 1px 0 0;
  font-size: 17px;
  line-height: 1.2;
}

.yuan {
  color: var(--ink-4);
  font-size: 10px;
}

.stat dd small {
  margin-left: 1px;
  color: var(--ink-4);
  font-size: 10px;
}

/* ---- 工具条 ---- */

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--s2);
  margin-bottom: var(--s3);
}

.date-step,
.date-now {
  padding: 4px 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.date-now.is-off {
  color: var(--lacquer);
  border-color: var(--lacquer);
}

.create-btn {
  margin-left: auto;
  padding: 5px 12px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
  letter-spacing: 0.08em;
}

/* ---- 时间轴 ---- */

.timeline {
  margin-bottom: var(--s5);
}

.seg {
  display: flex;
  gap: var(--s2);
  padding-bottom: var(--s2);
}

.gut {
  flex: none;
  width: 40px;
  padding-top: 2px;
  text-align: right;
}

.gut-time {
  display: block;
  font-size: 11px;
  line-height: 1.3;
}

.gut-end {
  display: block;
  color: var(--ink-4);
  font-size: 9px;
  line-height: 1.3;
}

.rail {
  position: relative;
  flex: none;
  width: 9px;
}

.rail::before {
  position: absolute;
  top: 8px;
  bottom: -10px;
  left: 50%;
  width: 1px;
  background: var(--line);
  content: "";
  transform: translateX(-50%);
}

.seg:last-of-type .rail::before {
  bottom: 0;
}

.rail-dot {
  position: absolute;
  top: 6px;
  left: 50%;
  width: 5px;
  height: 5px;
  background: var(--paper);
  border: 1px solid var(--ink-3);
  border-radius: 50%;
  transform: translateX(-50%);
}

.seg-body {
  min-width: 0;
  flex: 1 1 auto;
}

/* 空档：一行虚线，不抢视觉 */
.gap {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  width: 100%;
  padding: 4px var(--s3);
  border: 0;
  border-top: 1px dashed var(--line);
  border-bottom: 1px dashed var(--line);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  text-align: left;
  transition: color var(--dur-1) var(--ease), border-color var(--dur-1) var(--ease);
}

.gap:hover {
  border-color: var(--ink-4);
  color: var(--ink);
}

.gap-label {
  font-family: var(--font-display);
  font-size: 12px;
  letter-spacing: 0.06em;
}

.gap-value {
  font-size: 11px;
}

.gap-add {
  margin-left: auto;
  font-size: 10px;
  letter-spacing: 0.06em;
  opacity: 0.8;
}

.void-note {
  margin-top: var(--s2);
  padding-left: 51px;
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.02em;
}

/* ---- 提醒 ---- */

.reminders {
  border-top: 1px solid var(--line);
}

.fold {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  width: 100%;
  padding: var(--s3) 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  text-align: left;
}

.fold-title {
  font-family: var(--font-display);
  font-size: 15px;
  letter-spacing: 0.04em;
}

.fold-count {
  color: var(--lacquer);
  font-size: 11px;
}

.fold-arrow {
  margin-left: auto;
  color: var(--ink-4);
  font-size: 12px;
  transition: transform var(--dur-2) var(--ease);
}

.fold-arrow.is-open {
  transform: rotate(180deg);
}

.fold-body {
  padding-bottom: var(--s4);
}

.reminder {
  padding: var(--s3) 0;
  border-top: 1px solid var(--line-soft);
}

.reminder-head {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
}

.reminder-when {
  color: var(--lacquer);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.reminder-name {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reminder-text {
  margin-top: var(--s1);
  color: var(--ink-3);
  font-size: 11px;
  line-height: 1.7;
}

.copy-btn {
  margin-top: var(--s2);
  padding: 3px 10px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  letter-spacing: 0.06em;
}

.copy-btn:hover {
  border-color: var(--ink);
}

.empty-line {
  color: var(--ink-3);
  font-size: 11px;
}

/* ---- 抽屉内表单 ---- */

.field {
  margin-bottom: var(--s4);
}

.field-head {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  margin-bottom: var(--s2);
}

.field-label {
  font-family: var(--font-display);
  font-size: 14px;
  letter-spacing: 0.06em;
}

.field-hint {
  color: var(--ink-4);
  font-size: 10px;
}

.input {
  width: 100%;
  min-width: 0;
  padding: 8px 0;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font-family: var(--font-ui);
  font-size: 14px;
  outline: none;
}

.input:focus {
  border-bottom-color: var(--ink);
}

.decisions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s4);
}

.radio {
  display: flex;
  align-items: center;
  gap: var(--s1);
  color: var(--ink-2);
  font-size: 12px;
  cursor: pointer;
}

.btn {
  padding: 6px 14px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
  letter-spacing: 0.06em;
}

.btn-primary {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.wide {
  flex: 1 1 auto;
}

.mono {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 360px) {
  .stat dd {
    font-size: 16px;
  }

  .gut {
    width: 34px;
  }
}
</style>
