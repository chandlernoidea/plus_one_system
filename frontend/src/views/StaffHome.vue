<script setup>
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
import { dateKeyOf, endOfWeek, startOfWeek, toKey } from '../utils/date'

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

const nextAppointment = computed(() => {
  const nowMinutes = new Date().getHours() * 60 + new Date().getMinutes()
  const candidates = calendar.appointments.value
    .filter((item) => ['PENDING', 'CONFIRMED', 'RESCHEDULING'].includes(item.status))
    .map((item) => ({
      ...item,
      key: dateKeyOf(item.confirmedStart || item.requestedStart),
      time: (item.confirmedStart || item.requestedStart || '').slice(11, 16)
    }))
    .filter((item) => item.key && item.key >= calendar.todayKey)
    .filter((item) => {
      if (item.key !== calendar.todayKey) {
        return true
      }
      const [hours, minutes] = item.time.split(':').map(Number)
      return hours * 60 + minutes >= nowMinutes
    })
    .sort((a, b) => `${a.key} ${a.time}`.localeCompare(`${b.key} ${b.time}`))
  return candidates[0] || null
})

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
