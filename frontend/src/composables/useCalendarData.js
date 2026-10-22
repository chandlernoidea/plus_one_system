import { computed, ref } from 'vue'
import { listStaffs } from '../api/staff'
import { listSchedules, listSlots } from '../api/schedule'
import { listMyAppointments, listStaffAppointments } from '../api/appointment'
import { useUserStore } from '../store/user'
import { dateKeyOf, minutesOf, toKey } from '../utils/date'

/** 预约状态在版面里的称谓与色板。只保留颜色语义，不用 Element 的彩色药丸。 */
export const STATUS_META = {
  PENDING: { label: '待确认', tone: 'pending' },
  CONFIRMED: { label: '已确认', tone: 'confirmed' },
  RESCHEDULING: { label: '改约中', tone: 'pending' },
  IN_SERVICE: { label: '服务中', tone: 'inservice' },
  COMPLETED: { label: '已完成', tone: 'completed' },
  NO_SHOW: { label: '爽约', tone: 'noshow' },
  CANCELLED: { label: '已取消', tone: 'cancelled' },
  REJECTED: { label: '已拒绝', tone: 'cancelled' },
  EXPIRED: { label: '已过期', tone: 'cancelled' }
}

/** 连续 N 个 15 分钟空槽算作一个可约窗口，避免把 82 个 15 分钟槽展示给顾客 */
function countWindows(slots, size) {
  let windows = 0
  let run = 0
  for (const slot of slots) {
    if (slot.status === 'AVAILABLE') {
      run += 1
      if (run === size) {
        windows += 1
        run = 0
      }
    } else {
      run = 0
    }
  }
  return windows
}

export function statusMeta(status) {
  return STATUS_META[status] || { label: status || '未知', tone: 'cancelled' }
}

const STAFF_TONES = ['moss', 'ochre', 'slate', 'lacquer']

/** 计入「在岗档期」的状态；取消/拒绝/过期不再占用时间 */
const ON_CALENDAR = new Set([
  'PENDING',
  'CONFIRMED',
  'RESCHEDULING',
  'IN_SERVICE',
  'COMPLETED',
  'NO_SHOW'
])

/**
 * 日历数据层：把三个角色各自的接口拼成同一份「按天索引」的视图模型。
 * 需求方只需要按日期取数据，不必关心角色差异。
 */
export function useCalendarData() {
  const userStore = useUserStore()
  const role = computed(() => userStore.roleCode)

  const staffs = ref([])
  const appointments = ref([])
  const schedules = ref([])
  const daySlots = ref({})

  const loadingStaffs = ref(false)
  const loadingAppointments = ref(false)
  const loadingSchedules = ref(false)
  const loadingSlots = ref(false)

  let scheduleRange = ''
  let staffsLoaded = false
  let appointmentsLoaded = false

  const staffIndexById = computed(() => {
    const map = new Map()
    staffs.value.forEach((staff, index) => {
      map.set(staff.staffId, index)
    })
    return map
  })

  const staffTone = (staffId) => {
    const index = staffIndexById.value.get(staffId)
    return STAFF_TONES[(index ?? 0) % STAFF_TONES.length]
  }

  const myStaffId = computed(() => {
    const userId = userStore.userInfo?.userId
    if (!userId) {
      return null
    }
    const own = staffs.value.find((staff) => staff.userId === userId)
    return own?.staffId ?? null
  })

  /** 美甲师只看得到自己的档期；管理员与顾客看全店 */
  const visibleStaffs = computed(() => {
    if (role.value === 'STAFF' && myStaffId.value) {
      return staffs.value.filter((staff) => staff.staffId === myStaffId.value)
    }
    return staffs.value
  })

  /** 按天索引的预约 */
  const appointmentsByDay = computed(() => {
    const map = new Map()
    for (const appointment of appointments.value) {
      if (!ON_CALENDAR.has(appointment.status)) {
        continue
      }
      const key = dateKeyOf(appointment.confirmedStart || appointment.requestedStart)
      if (!key) {
        continue
      }
      if (!map.has(key)) {
        map.set(key, [])
      }
      map.get(key).push({
        ...appointment,
        startMinutes: minutesOf(appointment.confirmedStart || appointment.requestedStart),
        startText: (appointment.confirmedStart || appointment.requestedStart || '').slice(11, 16),
        duration: appointment.estDurationMinutes || 60,
        tone: staffTone(appointment.staffId)
      })
    }
    for (const list of map.values()) {
      list.sort((a, b) => a.startMinutes - b.startMinutes)
    }
    return map
  })

  /** 按天索引的排班：day → staffId → 班次 */
  const schedulesByDay = computed(() => {
    const map = new Map()
    for (const schedule of schedules.value) {
      if (!schedule.workDate) {
        continue
      }
      if (!map.has(schedule.workDate)) {
        map.set(schedule.workDate, new Map())
      }
      const perStaff = map.get(schedule.workDate)
      if (!perStaff.has(schedule.staffId)) {
        perStaff.set(schedule.staffId, [])
      }
      perStaff.get(schedule.staffId).push(schedule)
    }
    return map
  })

  /** 某天在岗 / 休假的美甲师，附带空闲时段统计（若已加载） */
  function staffRowsForDay(key) {
    const perStaff = schedulesByDay.value.get(key)
    const slots = daySlots.value[key]
    return visibleStaffs.value.map((staff) => {
      const shifts = perStaff?.get(staff.staffId) || []
      const normal = shifts.filter((shift) => shift.scheduleType !== 'LEAVE')
      const leave = shifts.find((shift) => shift.scheduleType === 'LEAVE')
      const slotInfo = slots?.find((item) => item.staffId === staff.staffId)
      return {
        staffId: staff.staffId,
        name: staff.realName || staff.staffNo || `美甲师 ${staff.staffId}`,
        tone: staffTone(staff.staffId),
        isSelf: staff.staffId === myStaffId.value,
        onDuty: normal.length > 0,
        onLeave: Boolean(leave) && normal.length === 0,
        shifts: normal,
        leaveRemark: leave?.remark || '',
        available: slotInfo?.available ?? null,
        windows: slotInfo?.windows ?? null,
        booked: slotInfo?.booked ?? null,
        total: slotInfo?.total ?? null
      }
    })
  }

  /** 某天的汇总：单数、营收、待确认、在岗人数 */
  function summaryForDay(key) {
    const list = appointmentsByDay.value.get(key) || []
    const rows = staffRowsForDay(key)
    const revenue = list.reduce((sum, item) => {
      if (item.status !== 'COMPLETED') {
        return sum
      }
      return sum + (Number(item.totalAmount) || 0)
    }, 0)
    return {
      count: list.length,
      revenue,
      pending: list.filter((item) => item.status === 'PENDING').length,
      onDuty: rows.filter((row) => row.onDuty).length,
      onLeave: rows.filter((row) => row.onLeave).length,
      appointments: list
    }
  }

  /**
   * 某天的档期负荷：用「预约占用分钟 / 排班分钟」估一个 0–1 的占用率。
   * 不额外请求时段接口，足够在月表里画出密度条；精确空闲时段在选中当天后再拉。
   */
  function dayLoad(key) {
    const list = appointmentsByDay.value.get(key) || []
    const rows = staffRowsForDay(key)
    let workMinutes = 0
    for (const row of rows) {
      for (const shift of row.shifts) {
        workMinutes += Math.max(0, minutesOf(shift.endTime) - minutesOf(shift.startTime))
      }
    }
    const bookedMinutes = list.reduce((sum, item) => sum + item.duration, 0)
    return {
      workMinutes,
      bookedMinutes,
      onDuty: rows.filter((row) => row.onDuty).length,
      ratio: workMinutes > 0 ? Math.min(1, bookedMinutes / workMinutes) : null
    }
  }

  async function loadStaffs() {
    if (staffsLoaded || loadingStaffs.value) {
      return
    }
    loadingStaffs.value = true
    try {
      const response = await listStaffs()
      staffs.value = response.data.data || []
      staffsLoaded = true
    } catch (error) {
      staffs.value = []
    } finally {
      loadingStaffs.value = false
    }
  }

  async function loadAppointments() {
    if (appointmentsLoaded || loadingAppointments.value) {
      return
    }
    loadingAppointments.value = true
    try {
      const response =
        role.value === 'CUSTOMER' ? await listMyAppointments() : await listStaffAppointments({})
      appointments.value = response.data.data || []
      appointmentsLoaded = true
    } catch (error) {
      appointments.value = []
    } finally {
      loadingAppointments.value = false
    }
  }

  async function loadSchedules(from, to) {
    const rangeKey = `${from}:${to}`
    if (scheduleRange === rangeKey || loadingSchedules.value) {
      return
    }
    loadingSchedules.value = true
    try {
      const targets =
        role.value === 'STAFF' && myStaffId.value
          ? [myStaffId.value]
          : visibleStaffs.value.map((staff) => staff.staffId)
      const responses = await Promise.all(
        targets.map((staffId) => listSchedules({ staffId, from, to }))
      )
      schedules.value = responses.flatMap((response) => response.data.data || [])
      scheduleRange = rangeKey
    } catch (error) {
      schedules.value = []
    } finally {
      loadingSchedules.value = false
    }
  }

  /** 选中某天时再取精确时段，月表翻页不必为每天付出一次请求 */
  async function loadDaySlots(key) {
    if (daySlots.value[key] || loadingSlots.value) {
      return
    }
    const rows = staffRowsForDay(key).filter((row) => row.onDuty)
    if (rows.length === 0) {
      daySlots.value = { ...daySlots.value, [key]: [] }
      return
    }
    loadingSlots.value = true
    try {
      const responses = await Promise.all(
        rows.map((row) => listSlots({ staffId: row.staffId, date: key }))
      )
      daySlots.value = {
        ...daySlots.value,
        [key]: responses.map((response, index) => {
          const slots = response.data.data || []
          const available = slots.filter((slot) => slot.status === 'AVAILABLE').length
          return {
            staffId: rows[index].staffId,
            total: slots.length,
            available,
            // 顾客看到的粒度：能完整放下 1 小时服务的起点数量
            windows: countWindows(slots, 4),
            booked: slots.length - available
          }
        })
      }
    } catch (error) {
      daySlots.value = { ...daySlots.value, [key]: [] }
    } finally {
      loadingSlots.value = false
    }
  }

  /** 强制刷新（评审数据变化后使用） */
  async function refresh(from, to) {
    appointmentsLoaded = false
    staffsLoaded = false
    scheduleRange = ''
    daySlots.value = {}
    await Promise.all([loadStaffs(), loadAppointments()])
    if (from && to) {
      await loadSchedules(from, to)
    }
  }

  const todayKey = toKey(new Date())

  return {
    role,
    staffs,
    visibleStaffs,
    myStaffId,
    appointments,
    schedules,
    daySlots,
    loading: computed(
      () => loadingStaffs.value || loadingAppointments.value || loadingSchedules.value
    ),
    loadingSlots,
    staffRowsForDay,
    summaryForDay,
    dayLoad,
    appointmentsByDay,
    schedulesByDay,
    staffTone,
    todayKey,
    loadStaffs,
    loadAppointments,
    loadSchedules,
    loadDaySlots,
    refresh
  }
}
