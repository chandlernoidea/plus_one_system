<script setup>
import { computed } from 'vue'
import {
  addDays,
  minutesOf,
  startOfWeek,
  toKey,
  WEEK_COLUMNS
} from '../../utils/date'

const props = defineProps({
  data: { type: Object, required: true },
  role: { type: String, default: 'ADMIN' },
  cursor: { type: Date, required: true },
  selectedKey: { type: String, default: '' }
})

const emit = defineEmits(['select', 'open-appointment'])

// 每小时 58px：12 小时约 700px，在 1000px 视口里不用滚动就能看全一天
const HOUR = 58
// 时间轴顶部留白，避免 09:00 的刻度被吸顶表头压住
const TOP_INSET = 16

const weekStart = computed(() => startOfWeek(props.cursor))
const days = computed(() =>
  WEEK_COLUMNS.map((column, index) => {
    const date = addDays(weekStart.value, index)
    const key = toKey(date)
    const summary = props.data.summaryForDay(key)
    const rows = props.data.staffRowsForDay(key)
    const load = props.data.dayLoad(key)
    return {
      key,
      date,
      label: column.label,
      day: date.getDate(),
      isToday: key === props.data.todayKey,
      isSelected: key === props.selectedKey,
      onDuty: load.onDuty,
      onLeave: rows.filter((row) => row.onLeave).length,
      appointments: layout(summary.appointments)
    }
  })
)

/**
 * 时间轴范围：取排班与预约的并集，再按整点向外取整；
 * 最少展示 09:00–20:00，避免只排了半天时时间轴过短、比例失真。
 */
const bounds = computed(() => {
  let min = 9 * 60
  let max = 20 * 60
  for (const day of days.value) {
    for (const appointment of day.appointments) {
      min = Math.min(min, Math.floor(appointment.startMinutes / 60) * 60)
      max = Math.max(max, Math.ceil((appointment.startMinutes + appointment.duration) / 60) * 60)
    }
    for (const row of props.data.staffRowsForDay(day.key)) {
      for (const shift of row.shifts) {
        min = Math.min(min, Math.floor(minutesOf(shift.startTime) / 60) * 60)
        max = Math.max(max, Math.ceil(minutesOf(shift.endTime) / 60) * 60)
      }
    }
  }
  return { min, max }
})

const hours = computed(() => {
  const list = []
  for (let minute = bounds.value.min; minute <= bounds.value.max; minute += 60) {
    list.push(minute)
  }
  return list
})

const bodyHeight = computed(
  () => ((bounds.value.max - bounds.value.min) / 60) * HOUR + TOP_INSET + 12
)

const nowMinutes = computed(() => {
  const now = new Date()
  return now.getHours() * 60 + now.getMinutes()
})

// 当前时刻落在时间轴范围之外时（清晨或深夜）不画「现在」线，
// 否则它会按分钟数算到表格外面，变成页面底部的一条孤立红线
const showNowLine = computed(
  () => nowMinutes.value >= bounds.value.min && nowMinutes.value <= bounds.value.max
)

/**
 * 同一时段并排：把互相重叠的预约分到不同列。
 *
 * 为什么先按簇分组：一天里常有「上午挤、下午空」的分布，
 * 如果按全天最大并发数统一分列，空闲时段也会被压窄。
 * 所以只对时间上连成一片的簇计算列数。
 *
 * 注意：这里复制每个条目再写入 column / columns，
 * 不改动 summaryForDay 返回的共享数据。
 */
function layout(list) {
  const sorted = list
    .map((item) => ({ ...item }))
    .sort((a, b) => a.startMinutes - b.startMinutes || b.duration - a.duration)
  const clusters = []
  let current = null
  for (const item of sorted) {
    if (!current || item.startMinutes >= current.end) {
      current = { items: [], end: 0 }
      clusters.push(current)
    }
    current.items.push(item)
    current.end = Math.max(current.end, item.startMinutes + item.duration)
  }
  for (const cluster of clusters) {
    const columnEnds = []
    for (const item of cluster.items) {
      let column = columnEnds.findIndex((end) => end <= item.startMinutes)
      if (column === -1) {
        columnEnds.push(0)
        column = columnEnds.length - 1
      }
      columnEnds[column] = item.startMinutes + item.duration
      item.column = column
    }
    for (const item of cluster.items) {
      item.columns = columnEnds.length
    }
  }
  return sorted
}

/** 预约块定位：按「当天第几分钟」映射到像素，重叠时按列宽等分 */
function blockStyle(item) {
  const top = TOP_INSET + ((item.startMinutes - bounds.value.min) / 60) * HOUR
  const height = Math.max((item.duration / 60) * HOUR - 3, 24)
  const width = 100 / (item.columns || 1)
  return {
    top: `${top}px`,
    height: `${height}px`,
    left: `calc(${(item.column || 0) * width}% + 3px)`,
    width: `calc(${width}% - 6px)`
  }
}

function staffShort(appointment) {
  return (appointment.staffName || '').replace(/^美甲师/, '') || '—'
}

function hourLabel(minute) {
  return `${String(Math.floor(minute / 60)).padStart(2, '0')}:00`
}

function labelFor(appointment) {
  if (props.role === 'CUSTOMER') {
    return appointment.serviceTypeName || '美甲服务'
  }
  return (appointment.customerName || '顾客').replace(/^(顾客|美甲师)/, '')
}
</script>

<template>
  <div class="week">
    <div class="week-scroll">
      <div class="week-inner">
        <div class="week-head">
          <span class="head-gutter"></span>
          <button
            v-for="day in days"
            :key="day.key"
            type="button"
            class="head-day"
            :class="{ 'is-today': day.isToday, 'is-selected': day.isSelected }"
            @click="emit('select', day.key)"
          >
            <span class="head-label">{{ day.label }}</span>
            <span class="head-num num">{{ day.day }}</span>
            <span class="head-duty mono">
              <template v-if="day.onDuty">{{ day.onDuty }} 在岗</template>
              <template v-else-if="day.onLeave">休假</template>
              <template v-else>未排班</template>
            </span>
          </button>
        </div>

        <div class="week-body" :style="{ height: `${bodyHeight}px` }">
          <div class="gutter">
            <span
              v-for="hour in hours"
              :key="hour"
              class="hour-label mono"
              :style="{ top: `${TOP_INSET + ((hour - bounds.min) / 60) * HOUR}px` }"
            >
              {{ hourLabel(hour) }}
            </span>
          </div>

          <div
            v-for="day in days"
            :key="day.key"
            class="column"
            :class="{ 'is-today': day.isToday, 'is-off': day.onDuty === 0 }"
            @click="emit('select', day.key)"
          >
            <span
              v-for="hour in hours"
              :key="hour"
              class="hour-line"
              :style="{ top: `${TOP_INSET + ((hour - bounds.min) / 60) * HOUR}px` }"
              aria-hidden="true"
            ></span>

            <span v-if="day.onDuty === 0" class="off-note">
              {{ day.onLeave ? '休假' : '未排班' }}
            </span>

            <template v-if="day.isToday && showNowLine">
              <span
                class="now-line"
                :style="{ top: `${TOP_INSET + ((nowMinutes - bounds.min) / 60) * HOUR}px` }"
                aria-hidden="true"
              >
                <i></i>
              </span>
            </template>

            <button
              v-for="appointment in day.appointments"
              :key="appointment.id"
              type="button"
              class="block"
              :class="`tone-${appointment.tone}`"
              :style="blockStyle(appointment)"
              :title="`${appointment.startText} ${labelFor(appointment)} · ${appointment.staffName || ''}`"
              @click.stop="emit('open-appointment', appointment.id)"
            >
              <span class="block-time mono">{{ appointment.startText }}</span>
              <span class="block-name">{{ labelFor(appointment) }}</span>
              <span class="block-staff">{{ staffShort(appointment) }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.week {
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

/* 窄屏才需要周表自己的横向滚动（表头随之横向移动，不再纵向吸顶） */
@media (max-width: 720px) {
  .week {
    overflow-x: auto;
    overflow-y: hidden;
  }

  /* 窄屏下 .week 成了滚动容器，sticky 的基准变成它自己：
     沿用页头高度会把表头顶下去 88px，这里归零。
     选择器要多一层 .week，才能压过后面的基础规则。 */
  .week .week-head {
    top: 0;
  }
}

/* 纵向滚动交给页面：周表内部再套滚动容器时，
   吸顶表头会贴在容器顶边（已被页头盖住），滚动时日期行会钻到页头底下 */
.week-scroll {
  overflow: visible;
}

.week-inner {
  min-width: 720px;
}

/* ---- 表头 ---- */

.week-head {
  position: sticky;
  top: var(--shell-bar-h);
  z-index: 3;
  display: grid;
  grid-template-columns: 56px repeat(7, minmax(0, 1fr));
  background: var(--paper);
  border-bottom: 1px solid var(--line);
}

.head-gutter {
  border-right: 1px solid var(--line-soft);
}

.head-day {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 10px var(--s3) 8px;
  border: 0;
  border-left: 1px solid var(--line-soft);
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
}

.head-day:hover {
  background: var(--paper-raised);
}

.head-label {
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.14em;
}

.head-num {
  font-size: 20px;
  line-height: 1.1;
}

.head-day.is-today .head-num {
  color: var(--lacquer);
}

.head-day.is-selected {
  background: var(--paper-raised);
  box-shadow: inset 0 -1.5px 0 var(--ink);
}

.head-duty {
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.08em;
}

/* ---- 时间轴与列 ---- */

.week-body {
  position: relative;
  display: grid;
  grid-template-columns: 56px repeat(7, minmax(0, 1fr));
}

.gutter {
  position: relative;
  border-right: 1px solid var(--line-soft);
}

.hour-label {
  position: absolute;
  right: 8px;
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.04em;
  transform: translateY(-5px);
}

.column {
  position: relative;
  border-left: 1px solid var(--line-soft);
  cursor: pointer;
}

.column.is-off {
  background: rgba(23, 19, 15, 0.02);
}

.column.is-today {
  background: rgba(171, 58, 43, 0.028);
}

.hour-line {
  position: absolute;
  right: 0;
  left: 0;
  height: 1px;
  background: var(--line-soft);
}

.off-note {
  position: absolute;
  top: 18px;
  left: 50%;
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
  transform: translateX(-50%);
}

.now-line {
  position: absolute;
  right: 0;
  left: 0;
  z-index: 2;
  height: 1px;
  background: var(--lacquer);
}

.now-line i {
  position: absolute;
  top: -2.5px;
  left: -2px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lacquer);
}

/* ---- 预约块 ---- */

.block {
  position: absolute;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow: hidden;
  padding: 3px 5px 3px 6px;
  border: 0;
  border-left: 2px solid var(--tone, var(--ink-3));
  background: color-mix(in srgb, var(--tone, var(--ink-3)) 7%, var(--paper-raised));
  color: var(--ink);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  container-type: inline-size;
  transition: background-color var(--dur-1) var(--ease);
}

/* 并列太窄时只保留时间，颜色仍能说明是哪位美甲师 */
.block-staff {
  display: none;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 9.5px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@container (max-width: 62px) {
  .block-name {
    display: none;
  }

  .block-staff {
    display: block;
  }
}

.block:hover {
  background: color-mix(in srgb, var(--tone, var(--ink-3)) 16%, var(--paper-raised));
}

.block-time {
  color: var(--ink-2);
  font-size: 9.5px;
  letter-spacing: 0.02em;
}

.block-name {
  overflow: hidden;
  color: var(--ink-2);
  font-size: 10.5px;
  line-height: 1.3;
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
</style>
