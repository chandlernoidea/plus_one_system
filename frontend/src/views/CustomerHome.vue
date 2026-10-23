<script setup>
/**
 * 顾客工作台。
 *
 * 第一屏回答两个问题：今天还有没有空档、哪几位美甲师能约。
 * 月表里只显示自己的预约；点某一天，右栏列出在岗美甲师与可约的 1 小时窗口，
 * 点「预约」直接跳到预约页并把日期、美甲师预填好。
 */
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
import { countActiveAppointments, findNextAppointment } from '../utils/appointments'

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

// 下一单：只算自己的在途预约
const nextAppointment = computed(() =>
  findNextAppointment(calendar.appointments.value, calendar.todayKey)
)

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
    value: countActiveAppointments(calendar.appointments.value),
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
