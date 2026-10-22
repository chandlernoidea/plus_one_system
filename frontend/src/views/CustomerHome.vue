<script setup>
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import AppShell from '../components/AppShell.vue'
import AppLede from '../components/AppLede.vue'
import ScheduleCalendar from '../components/calendar/ScheduleCalendar.vue'
import CustomerSlotView from './customer/CustomerSlotView.vue'
import CustomerAppointments from './customer/CustomerAppointments.vue'
import { useCalendarData } from '../composables/useCalendarData'
import { useCalendarView } from '../composables/useCalendarView'
import { dateKeyOf } from '../utils/date'

const router = useRouter()
const userStore = useUserStore()
const calendar = useCalendarData()
const calView = useCalendarView()

const activeView = ref('calendar')
const appointmentsRef = ref(null)
const bookingDate = ref('')
const bookingStaffId = ref(null)

const nav = [
  { key: 'calendar', label: '档期' },
  { key: 'booking', label: '预约美甲' },
  { key: 'appointments', label: '我的预约' }
]

const today = computed(() => calendar.summaryForDay(calendar.todayKey))

const todayRows = computed(() =>
  calendar.staffRowsForDay(calendar.todayKey).filter((row) => row.onDuty)
)

const todayOpen = computed(() => {
  if (todayRows.value.length === 0) {
    return 0
  }
  const loaded = todayRows.value.every((row) => row.available !== null)
  if (!loaded) {
    return null
  }
  return todayRows.value.reduce((sum, row) => sum + (row.windows || 0), 0)
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

const ledeTitle = computed(() => {
  if (todayOpen.value === null) {
    return '正在读取今天的空档'
  }
  if (todayRows.value.length === 0) {
    return '今天美甲师都休息'
  }
  if (todayOpen.value === 0) {
    return '今天已经约满'
  }
  return `今天还有 ${todayOpen.value} 个 1 小时空档`
})

const ledeNote = computed(() => {
  const names = todayRows.value.map((row) => row.name).slice(0, 3).join('、')
  const parts = []
  if (names) {
    parts.push(`${names} 在岗`)
  }
  if (nextAppointment.value) {
    parts.push(
      `下一次 ${nextAppointment.value.key.slice(5).replace('-', '.')} ${nextAppointment.value.time}`
    )
  } else {
    parts.push('还没有预约，从档期里挑一天开始')
  }
  return parts.join(' · ')
})

const stats = computed(() => [
  { label: '今日可约', value: todayOpen.value === null ? '—' : todayOpen.value, unit: '个 1 小时' },
  { label: '在岗美甲师', value: todayRows.value.length, unit: '位' },
  {
    label: '我的预约',
    value: calendar.appointments.value.filter((item) =>
      ['PENDING', 'CONFIRMED', 'RESCHEDULING'].includes(item.status)
    ).length,
    unit: '单'
  }
])

async function switchView(key) {
  activeView.value = key
  window.scrollTo({ top: 0, behavior: 'smooth' })
  if (key === 'appointments') {
    await nextTick()
    appointmentsRef.value?.refresh()
  }
}

function handleBook({ key, staffId }) {
  bookingDate.value = key
  bookingStaffId.value = staffId
  switchView('booking')
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
    role-label="顾客"
    @update:active="switchView"
    @logout="handleLogout"
  >
    <template v-if="activeView === 'calendar'">
      <AppLede eyebrow="CUSTOMER · BOOKING" :title="ledeTitle" :note="ledeNote" :stats="stats" />

      <ScheduleCalendar
        :data="calendar"
        :view="calView"
        role="CUSTOMER"
        @book="handleBook"
        @go-appointments="switchView('appointments')"
      />
    </template>

    <div v-else class="module">
      <CustomerSlotView
        v-if="activeView === 'booking'"
        :initial-date="bookingDate"
        :initial-staff-id="bookingStaffId"
        @appointment-created="switchView('appointments')"
      />
      <CustomerAppointments v-else-if="activeView === 'appointments'" ref="appointmentsRef" />
    </div>
  </AppShell>
</template>
