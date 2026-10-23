/**
 * 日期工具。
 *
 * 约定：
 * 1. 全部按浏览器本地时区计算，不引入 UTC，避免中国时区下 `toISOString()` 少一天；
 * 2. 后端返回的时间是 `yyyy-MM-dd HH:mm:ss` / `HH:mm:ss`，只做字符串切片，
 *    不构造 Date 再格式化，避免时区与夏令时问题；
 * 3. 「日」以 `YYYY-MM-DD` 字符串为唯一键，字符串大小比较即日期先后比较。
 */

const WEEKDAY_FULL = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const MONTH_NAMES = [
  '一月', '二月', '三月', '四月', '五月', '六月',
  '七月', '八月', '九月', '十月', '十一月', '十二月'
]

/** 周视图列：周一开头，`index` 对应 Date#getDay()（0 = 周日） */
export const WEEK_COLUMNS = [
  { index: 1, short: '一', label: '周一' },
  { index: 2, short: '二', label: '周二' },
  { index: 3, short: '三', label: '周三' },
  { index: 4, short: '四', label: '周四' },
  { index: 5, short: '五', label: '周五' },
  { index: 6, short: '六', label: '周六' },
  { index: 0, short: '日', label: '周日' }
]

/** 数字补零到两位，用于拼日期与时间字符串 */
export function pad2(value) {
  return String(value).padStart(2, '0')
}

/**
 * Date → 'YYYY-MM-DD'（本地日期，不经过 UTC）
 * @param {Date} date
 */
export function toKey(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

/**
 * 'YYYY-MM-DD' → 本地零点的 Date
 * @param {string} key
 */
export function fromKey(key) {
  const [year, month, day] = key.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/**
 * 日期加减天数，返回新 Date（不修改入参）
 * @param {Date} date
 * @param {number} amount
 */
export function addDays(date, amount) {
  const next = new Date(date)
  next.setDate(next.getDate() + amount)
  return next
}

/**
 * 月份加减，日号超过目标月天数时收敛到月末（1/31 + 1 月 → 2/28）
 * @param {Date} date
 * @param {number} amount
 */
export function addMonths(date, amount) {
  const next = new Date(date.getFullYear(), date.getMonth() + amount, 1)
  next.setDate(Math.min(date.getDate(), endOfMonth(next).getDate()))
  return next
}

/** 当月 1 号零点 */
export function startOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

/** 当月最后一天零点 */
export function endOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0)
}

/**
 * 所在周的周一零点。
 * JS 的 getDay() 周日是 0，这里换算成「周一为第 1 天」。
 */
export function startOfWeek(date) {
  const day = date.getDay()
  const offset = day === 0 ? -6 : 1 - day
  return addDays(date, offset)
}

/** 所在周的周日零点 */
export function endOfWeek(date) {
  return addDays(startOfWeek(date), 6)
}

/** 是否今天（按本地日历日比较） */
export function isToday(date) {
  return toKey(date) === toKey(new Date())
}

/** 是否周六 / 周日，用于月表列底色 */
export function isWeekend(date) {
  const day = date.getDay()
  return day === 0 || day === 6
}

/** 中文月份名，如「十月」 */
export function monthName(date) {
  return MONTH_NAMES[date.getMonth()]
}

/** 中文星期名，如「周五」 */
export function weekdayFull(date) {
  return WEEKDAY_FULL[date.getDay()]
}

/**
 * 月视图矩阵：从当月 1 号所在的周一铺起，最多 6 行 × 7 列。
 * 末尾整行都不属于当月时会裁掉，避免月表下方多出一条空行。
 *
 * 每个单元格返回：
 * - date / key：日期对象与 'YYYY-MM-DD'
 * - inMonth：是否属于当前月（跨月补齐的格子为 false）
 * - isToday / isWeekend：用于今天高亮与列底色
 *
 * @param {Date} viewDate 当前查看的月份（任意一天即可）
 * @returns {Array<Array<object>>} 每周一行
 */
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

  while (weeks.length > 1 && weeks[weeks.length - 1].every((day) => !day.inMonth)) {
    weeks.pop()
  }
  return weeks
}

/**
 * 从任意时间字符串里取 'HH:mm'。
 * 支持 'YYYY-MM-DD HH:mm:ss'、'HH:mm:ss'、'HH:mm'。
 * @param {string} value
 */
export function timeText(value) {
  if (!value) {
    return ''
  }
  const match = String(value).match(/(\d{2}):(\d{2})/)
  return match ? `${match[1]}:${match[2]}` : ''
}

/**
 * 时间字符串 → 当天第几分钟，用于周视图的纵向定位。
 * 解析失败返回 0。
 * @param {string} value
 */
export function minutesOf(value) {
  const text = timeText(value)
  if (!text) {
    return 0
  }
  const [hours, minutes] = text.split(':').map(Number)
  return hours * 60 + minutes
}

/**
 * 从任意时间字符串里取 'YYYY-MM-DD'。
 * 预约的 `confirmedStart` 可能为空（待确认），调用方通常传
 * `confirmedStart || requestedStart`。
 * @param {string} value
 */
export function dateKeyOf(value) {
  if (!value) {
    return ''
  }
  const match = String(value).match(/\d{4}-\d{2}-\d{2}/)
  return match ? match[0] : ''
}

/**
 * 相对今天的口头标签：今天 / 明天 / 昨天，其余返回空串。
 * @param {string} key 'YYYY-MM-DD'
 */
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

/**
 * 金额格式化：整数不带小数，非整数保留两位；空值统一显示「—」。
 * @param {number|string|null} value
 * @param {{ sign?: boolean }} options sign 为 true 时加 ¥ 前缀
 */
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
