<script setup>
/**
 * 美甲师工作台。
 *
 * 只呈现「我」的视角：自己的档期（在岗 / 休假）、自己的预约、自己的工资。
 * 日历数据层已经按角色过滤（useCalendarData.visibleStaffs），
 * 这里不需要再判断归属。
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import AppShell from '../components/AppShell.vue'
import AppLede from '../components/AppLede.vue'
import ScheduleCalendar from '../components/calendar/ScheduleCalendar.vue'
import StaffSchedule from './staff/StaffSchedule.vue'
import StaffAppointments from './staff/StaffAppointments.vue'
import StaffSettlement from './staff/StaffSettlement.vue'
import { useCalendarData } from '../composables/useCalendarData'
import { useCalendarView } from '../composables/useCalendarView'
import { endOfWeek, startOfWeek, toKey } from '../utils/date'
import { findNextAppointment } from '../utils/appointments'

const router = useRouter()
const userStore = useUserStore()
const calendar = useCalendarData()
const calView = useCalendarView()

const activeView = ref('calendar')
const scheduleDate = ref('')
const scheduleToken = ref(0)

const nav = [
  { key: 'calendar', label: '档期' },
  { key: 'schedule', label: '我的排班' },
  { key: 'appointments', label: '预约管理' },
  { key: 'settlement', label: '我的工资' }
]

const today = computed(() => calendar.summaryForDay(calendar.todayKey))

const weekDutyDays = computed(() => {
  const from = toKey(startOfWeek(new Date()))
  const to = toKey(endOfWeek(new Date()))
  const days = new Set()
  for (const schedule of calendar.schedules.value) {
    if (schedule.scheduleType === 'LEAVE') {
      continue
    }
    if (schedule.workDate >= from && schedule.workDate <= to) {
      days.add(schedule.workDate)
    }
  }
  return days.size
})

// 下一单：在途预约里时间最近的一条（同一天要比较时分）
const nextAppointment = computed(() =>
  findNextAppointment(calendar.appointments.value, calendar.todayKey)
)

const ledeTitle = computed(() =>
  today.value.count ? `今天有 ${today.value.count} 个预约` : '今天没有预约，可以留给自己'
)

const ledeNote = computed(() => {
  const parts = [`本周在岗 ${weekDutyDays.value} 天`]
  if (nextAppointment.value) {
    parts.push(
      `下一单 ${nextAppointment.value.key.slice(5).replace('-', '.')} ${nextAppointment.value.time}`
    )
  }
  if (today.value.pending) {
    parts.push(`${today.value.pending} 单待确认`)
  }
  return parts.join(' · ')
})

const stats = computed(() => [
  { label: '今日预约', value: today.value.count, unit: '单' },
  { label: '本周在岗', value: weekDutyDays.value, unit: '天' },
  { label: '待确认', value: today.value.pending, unit: '单' }
])

function switchView(key) {
  activeView.value = key
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleCreateSchedule(key) {
  scheduleDate.value = key
  scheduleToken.value += 1
  switchView('schedule')
}

function handleLogout() {
  userStore.logout()
  router.replace('/login')
}
</script>

<template>
  <AppShell
    :nav="nav"
    :active="activeView"
    role-label="美甲师"
    @update:active="switchView"
    @logout="handleLogout"
  >
    <template v-if="activeView === 'calendar'">
      <AppLede eyebrow="STAFF · MY SCHEDULE" :title="ledeTitle" :note="ledeNote" :stats="stats" />

      <ScheduleCalendar
        :data="calendar"
        :view="calView"
        role="STAFF"
        @go-appointments="switchView('appointments')"
        @open-appointment="switchView('appointments')"
        @create-schedule="handleCreateSchedule"
      />
    </template>

    <div v-else class="module">
      <StaffSchedule
        v-if="activeView === 'schedule'"
        :initial-date="scheduleDate"
        :request-token="scheduleToken"
      />
      <StaffAppointments v-else-if="activeView === 'appointments'" />
      <StaffSettlement v-else-if="activeView === 'settlement'" />
    </div>
  </AppShell>
</template>
