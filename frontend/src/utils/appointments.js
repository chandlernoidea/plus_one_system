/**
 * 预约列表的派生逻辑。
 *
 * 美甲师首页与顾客首页都要回答同一个问题：「我的下一单是什么」。
 * 两边原本各写了一份完全相同的 25 行实现，这里合并，
 * 同时把「哪些状态算进行中的单」收口成一处常量。
 */
import { dateKeyOf } from './date'

/** 仍然有效的预约状态：待确认、已确认、改约中（服务中/已完成不再算「下一单」） */
const ACTIVE_STATUSES = new Set(['PENDING', 'CONFIRMED', 'RESCHEDULING'])

/** 是否是需要关注的在途预约 */
export function isActiveAppointment(appointment) {
  return ACTIVE_STATUSES.has(appointment.status)
}

/** 在途预约数量，用于首页统计 */
export function countActiveAppointments(appointments) {
  return (appointments || []).filter(isActiveAppointment).length
}

/**
 * 找到「下一单」：状态在途、开始时间不早于当前时刻，按时间升序取第一条。
 *
 * 待确认单没有 confirmedStart，用 requestedStart 兜底；
 * 同一天的单还要比较时分，已过去的时段跳过。
 *
 * @param {Array} appointments 预约列表
 * @param {string} todayKey 今天的 'YYYY-MM-DD'
 * @param {Date} now 当前时间（测试时可注入）
 * @returns {object|null} 原预约字段 + key（日期）+ time（HH:mm），没有则 null
 */
export function findNextAppointment(appointments, todayKey, now = new Date()) {
  const nowMinutes = now.getHours() * 60 + now.getMinutes()
  const candidates = (appointments || [])
    .filter(isActiveAppointment)
    .map((item) => {
      const start = item.confirmedStart || item.requestedStart || ''
      return {
        ...item,
        key: dateKeyOf(start),
        time: start.slice(11, 16)
      }
    })
    .filter((item) => item.key && item.key >= todayKey)
    .filter((item) => {
      if (item.key !== todayKey) {
        return true
      }
      const [hours, minutes] = item.time.split(':').map(Number)
      return hours * 60 + minutes >= nowMinutes
    })
    .sort((a, b) => `${a.key} ${a.time}`.localeCompare(`${b.key} ${b.time}`))

  return candidates[0] || null
}
