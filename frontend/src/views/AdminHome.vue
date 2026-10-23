<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import AppShell from '../components/AppShell.vue'
import AppLede from '../components/AppLede.vue'
import ScheduleCalendar from '../components/calendar/ScheduleCalendar.vue'
import { useCalendarData } from '../composables/useCalendarData'
import { useCalendarView } from '../composables/useCalendarView'
import { formatMoney } from '../utils/date'
import AdminPrepList from './admin/AdminPrepList.vue'
import AdminReviewList from './admin/AdminReviewList.vue'
import AdminAppointments from './admin/AdminAppointments.vue'
import AdminCompensationRule from './admin/AdminCompensationRule.vue'
import AdminSettlement from './admin/AdminSettlement.vue'

const router = useRouter()
const userStore = useUserStore()
const calendar = useCalendarData()
const calView = useCalendarView()

const activeView = ref('calendar')

const nav = [
  { key: 'calendar', label: '档期' },
  { key: 'prep', label: '物料准备' },
  { key: 'review', label: '评价' },
  { key: 'appointments', label: '预约管理' },
  { key: 'rule', label: '薪酬规则' },
  { key: 'settlement', label: '月度结算' }
]

const today = computed(() => calendar.summaryForDay(calendar.todayKey))

const ledeTitle = computed(() =>
  today.value.count ? `今天有 ${today.value.count} 个预约` : '今天还没有预约'
)

const ledeNote = computed(() => {
  const parts = [`${today.value.onDuty} 位美甲师在岗`]
  const first = today.value.appointments[0]
  if (first) {
    parts.push(`最早 ${first.startText} 开始`)
  }
  if (today.value.pending) {
    parts.push(`${today.value.pending} 单待确认`)
  }
  return parts.join(' · ')
})

const stats = computed(() => [
  { label: '今日预约', value: today.value.count, unit: '单' },
  { label: '今日营收', value: formatMoney(today.value.revenue), unit: '元' },
  { label: '待确认', value: today.value.pending, unit: '单' },
  { label: '在岗', value: today.value.onDuty, unit: '位' }
])

function switchView(key) {
  activeView.value = key
  window.scrollTo({ top: 0, behavior: 'smooth' })
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
    role-label="管理员"
    @update:active="switchView"
    @logout="handleLogout"
  >
    <template v-if="activeView === 'calendar'">
      <AppLede eyebrow="ADMIN · SCHEDULE" :title="ledeTitle" :note="ledeNote" :stats="stats" />

      <ScheduleCalendar
        :data="calendar"
        :view="calView"
        role="ADMIN"
        @go-appointments="switchView('appointments')"
        @open-appointment="switchView('appointments')"
      />
    </template>

    <div v-else class="module">
      <AdminPrepList v-if="activeView === 'prep'" />
      <AdminReviewList v-else-if="activeView === 'review'" />
      <AdminAppointments v-else-if="activeView === 'appointments'" />
      <AdminCompensationRule v-else-if="activeView === 'rule'" />
      <AdminSettlement v-else-if="activeView === 'settlement'" />
    </div>
  </AppShell>
</template>
