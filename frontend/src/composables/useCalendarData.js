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

  // 已加载的排班范围（'from:to'），命中时跳过重复请求
  let scheduleRange = ''
  // 每次排班请求自增，用于丢弃过期响应
  let scheduleRequestToken = 0
  // 正在请求空闲时段的日期，避免同一天重复打接口
  const pendingSlotKeys = new Set()
  let staffsLoaded = false
  let appointmentsLoaded = false

  const staffIndexById = computed(() => {
    const map = new Map()
    staffs.value.forEach((staff, index) => {
      map.set(staff.staffId, index)
    })
    return map
  })

  /**
   * 美甲师对应的线色。用 index 取模而不是随机，
   * 保证同一人在月表、周表、图例里颜色一致。
   */
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

  /**
   * 某天的美甲师档期行（按 visibleStaffs 顺序，一人一行）。
   *
   * 返回值同时承担两种语义：
   * - onDuty / onLeave：是否上班、是否整天调休（由排班决定，先于时段可用）；
   * - available / windows：空闲时段统计，仅在 loadDaySlots 拉过当天后才有值，
   *   未加载时为 null，视图需要区分「0 个空档」与「还没加载」。
   *
   * @param {string} key 'YYYY-MM-DD'
   */
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

  /**
   * 某天的汇总：预约数、营收、待确认数、在岗 / 休假人数。
   *
   * 营收只统计 COMPLETED 且已录入金额的单；待确认单不计入营收，
   * 也不占用时段（时间槽由后端在审批通过时才标 BOOKED）。
   *
   * @param {string} key 'YYYY-MM-DD'
   */
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

  /** 拉取全店在岗美甲师；只拉一次，失败允许下次重试 */
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

  /**
   * 按角色拉取预约：
   * - 顾客：/appointments/my（只看自己的）
   * - 美甲师 / 管理员：/appointments/staff（后端按角色过滤）
   */
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

  /**
   * 拉取一个日期范围内的排班。
   *
   * 快速翻月时会出现多个并发请求，这里用递增的 token 做「最新请求获胜」：
   * 每次发起请求自增 token，回来时只有 token 仍是最新的才写回状态，
   * 避免旧月份的结果覆盖新月份。
   */
  async function loadSchedules(from, to) {
    const rangeKey = `${from}:${to}`
    if (scheduleRange === rangeKey) {
      return
    }
    // 美甲师只能看自己的排班；staffId 还没解析出来时先不请求，
    // 避免 staffs 尚未加载时退化成「拉全店」
    if (role.value === 'STAFF' && !myStaffId.value) {
      return
    }

    const token = ++scheduleRequestToken
    loadingSchedules.value = true
    try {
      const targets =
        role.value === 'STAFF'
          ? [myStaffId.value]
          : visibleStaffs.value.map((staff) => staff.staffId)
      const responses = await Promise.all(
        targets.map((staffId) => listSchedules({ staffId, from, to }))
      )
      if (token !== scheduleRequestToken) {
        return
      }
      schedules.value = responses.flatMap((response) => response.data.data || [])
      scheduleRange = rangeKey
    } catch (error) {
      if (token === scheduleRequestToken) {
        schedules.value = []
      }
    } finally {
      if (token === scheduleRequestToken) {
        loadingSchedules.value = false
      }
    }
  }

  /**
   * 选中某天时再取精确时段，月表翻页不必为每天付出一次请求。
   * 结果按日期缓存；同一天重复点击不会重复请求，不同天可以并发。
   */
  async function loadDaySlots(key) {
    if (daySlots.value[key] || pendingSlotKeys.has(key)) {
      return
    }
    const rows = staffRowsForDay(key).filter((row) => row.onDuty)
    if (rows.length === 0) {
      daySlots.value = { ...daySlots.value, [key]: [] }
      return
    }
    pendingSlotKeys.add(key)
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
      pendingSlotKeys.delete(key)
      loadingSlots.value = pendingSlotKeys.size > 0
    }
  }

  const todayKey = toKey(new Date())

  // 只暴露视图真正用到的成员：内部索引（appointmentsByDay 等）不对外，避免调用方绕过派生逻辑
  return {
    visibleStaffs,
    appointments,
    schedules,
    todayKey,
    staffRowsForDay,
    summaryForDay,
    dayLoad,
    staffTone,
    loadStaffs,
    loadAppointments,
    loadSchedules,
    loadDaySlots
  }
}
