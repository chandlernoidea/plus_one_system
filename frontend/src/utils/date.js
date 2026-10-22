/**
 * 日期工具。
 * 统一使用本地时区的「日」为单位做比较；接口返回的时间字符串是
 * `yyyy-MM-dd HH:mm:ss` 或 `HH:mm:ss`，这里只做字符串切片，避免时区漂移。
 */

const WEEKDAY_SHORT = ['日', '一', '二', '三', '四', '五', '六']
const WEEKDAY_FULL = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const MONTH_NAMES = [
  '一月', '二月', '三月', '四月', '五月', '六月',
  '七月', '八月', '九月', '十月', '十一月', '十二月'
]

export const WEEK_COLUMNS = [
  { index: 1, short: '一', label: '周一' },
  { index: 2, short: '二', label: '周二' },
  { index: 3, short: '三', label: '周三' },
  { index: 4, short: '四', label: '周四' },
  { index: 5, short: '五', label: '周五' },
  { index: 6, short: '六', label: '周六' },
  { index: 0, short: '日', label: '周日' }
]

export function pad2(value) {
  return String(value).padStart(2, '0')
}

/** Date → 'YYYY-MM-DD' */
export function toKey(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

/** 'YYYY-MM-DD' → 本地零点的 Date */
export function fromKey(key) {
  const [year, month, day] = key.split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function addDays(date, amount) {
  const next = new Date(date)
  next.setDate(next.getDate() + amount)
  return next
}

export function addMonths(date, amount) {
  const next = new Date(date.getFullYear(), date.getMonth() + amount, 1)
  next.setDate(Math.min(date.getDate(), endOfMonth(next).getDate()))
  return next
}

export function startOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

export function endOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0)
}

/** 周一为一周起点 */
export function startOfWeek(date) {
  const day = date.getDay()
  const offset = day === 0 ? -6 : 1 - day
  return addDays(date, offset)
}

export function endOfWeek(date) {
  return addDays(startOfWeek(date), 6)
}

export function isSameDay(a, b) {
  return toKey(a) === toKey(b)
}

export function isToday(date) {
  return isSameDay(date, new Date())
}

export function isWeekend(date) {
  const day = date.getDay()
  return day === 0 || day === 6
}

export function monthName(date) {
  return MONTH_NAMES[date.getMonth()]
}

export function monthTitle(date) {
  return `${date.getFullYear()} · ${monthName(date)}`
}

export function weekdayShort(date) {
  return WEEKDAY_SHORT[date.getDay()]
}

export function weekdayFull(date) {
  return WEEKDAY_FULL[date.getDay()]
}

/** 周序号（以周一为起点，用于版面右上角的「第 N 周」） */
export function weekOfYear(date) {
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const dayNumber = (target.getDay() + 6) % 7
  target.setDate(target.getDate() - dayNumber + 3)
  const firstThursday = new Date(target.getFullYear(), 0, 4)
  const firstDayNumber = (firstThursday.getDay() + 6) % 7
  firstThursday.setDate(firstThursday.getDate() - firstDayNumber + 3)
  return 1 + Math.round((target - firstThursday) / (7 * 24 * 3600 * 1000))
}

/** 月视图矩阵：6 行 × 7 列，周一开头 */
export function buildMonthGrid(viewDate) {
  const first = startOfMonth(viewDate)
  const gridStart = startOfWeek(first)
  const todayKey = toKey(new Date())
  const weeks = []

  for (let row = 0; row < 6; row += 1) {
    const days = []
    for (let column = 0; column < 7; column += 1) {
      const date = addDays(gridStart, row * 7 + column)
      const key = toKey(date)
      days.push({
        date,
        key,
        day: date.getDate(),
        inMonth: date.getMonth() === viewDate.getMonth(),
        isToday: key === todayKey,
        isWeekend: isWeekend(date)
      })
    }
    weeks.push(days)
  }

  // 去掉整行都不属于本月的尾行，避免月表下方留一条空表格
  while (weeks.length > 1 && weeks[weeks.length - 1].every((day) => !day.inMonth)) {
    weeks.pop()
  }
  return weeks
}

/** 'YYYY-MM-DD HH:mm:ss' | 'HH:mm:ss' | 'HH:mm' → 'HH:mm' */
export function timeText(value) {
  if (!value) {
    return ''
  }
  const match = String(value).match(/(\d{2}):(\d{2})/)
  return match ? `${match[1]}:${match[2]}` : ''
}

/** 时间字符串 → 当天第几分钟 */
export function minutesOf(value) {
  const text = timeText(value)
  if (!text) {
    return 0
  }
  const [hours, minutes] = text.split(':').map(Number)
  return hours * 60 + minutes
}

/** 任意时间字符串 → 'YYYY-MM-DD' */
export function dateKeyOf(value) {
  if (!value) {
    return ''
  }
  const match = String(value).match(/\d{4}-\d{2}-\d{2}/)
  return match ? match[0] : ''
}

/** 相对今天的口头标签 */
export function relativeDayLabel(key) {
  const today = new Date()
  if (key === toKey(today)) {
    return '今天'
  }
  if (key === toKey(addDays(today, 1))) {
    return '明天'
  }
  if (key === toKey(addDays(today, -1))) {
    return '昨天'
  }
  return ''
}

export function formatMoney(value, { sign = false } = {}) {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  const amount = Number(value)
  if (Number.isNaN(amount)) {
    return '—'
  }
  const text = amount.toLocaleString('zh-CN', {
    minimumFractionDigits: amount % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2
  })
  return `${sign ? '¥' : ''}${text}`
}
