/**
 * 把列表按「每 N 天一段」切成可点选的日期块。
 *
 * 为什么以「今天」为锚点： salon 的日常操作几乎都围绕最近几天，
 * 锚定今天能保证当前段永远包含今天；段边界会随日期滚动，
 * 但每一段始终是整齐的 3 天，不会出现 1 天或 5 天的碎片段。
 *
 * 所有块之间不重叠、不留空档，块的条数由传入列表实时统计。
 */
import { toKey } from './date'

const DAY_MS = 86400000

/** 'YYYY-MM-DD' → 当天零点的毫秒数 */
function dayStart(dateKey) {
  return new Date(`${dateKey}T00:00:00`).getTime()
}

/**
 * 毫秒数 → 'YYYY-MM-DD'。
 * 这里必须用本地日期字段拼，不能用 toISOString()：
 * 东八区凌晨的本地零点转成 UTC 会退到前一天。
 */
function keyOfTime(time) {
  const date = new Date(time)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** 'YYYY-MM-DD' → 'MM.DD' */
function fmt(dateKey) {
  return `${dateKey.slice(5, 7)}.${dateKey.slice(8, 10)}`
}

/** 同一月份内简写成 10.23–25，跨月写成 10.31–11.02 */
function blockLabel(from, to) {
  if (from.slice(0, 7) === to.slice(0, 7)) {
    return `${fmt(from)}–${to.slice(8, 10)}`
  }
  return `${fmt(from)}–${fmt(to)}`
}

/**
 * 生成日期分段。
 *
 * 算法：
 * 1. 取列表里最早/最晚的日期，算出「今天」相对最早日期的天序号；
 * 2. 把今天的序号向下取整到 size 的倍数，得到锚点段的起点；
 * 3. 从锚点段向前补齐到最早日期、向后铺到最晚日期；
 * 4. 逐段统计落入该段的条目数，并标记哪一段包含今天。
 *
 * @param {Array} items 原始列表（不会修改）
 * @param {(item: any) => string} getKey 取出条目所属的 'YYYY-MM-DD'
 * @param {{ size?: number, anchorKey?: string }} options size 默认 3，anchorKey 默认今天
 * @returns {Array<{ key: string, from: string, to: string, count: number,
 *   label: string, isCurrent: boolean }>}
 */
export function buildDateBlocks(items, getKey, options = {}) {
  const size = options.size || 3
  const anchorKey = options.anchorKey || toKey(new Date())
  const list = (items || []).filter(Boolean)
  if (list.length === 0) {
    return []
  }

  const keys = list.map(getKey).filter(Boolean).sort()
  if (keys.length === 0) {
    return []
  }
  const minT = dayStart(keys[0])
  const maxT = dayStart(keys[keys.length - 1])
  const anchorIdx = Math.round((dayStart(anchorKey) - minT) / DAY_MS)

  // 锚点段起点：把今天的序号压到 size 的整数倍；负数用 +size 修正到正区间
  let startIdx = anchorIdx - (((anchorIdx % size) + size) % size)
  while (startIdx > 0) {
    startIdx -= size
  }

  const blocks = []
  for (let index = startIdx; ; index += size) {
    const fromT = minT + index * DAY_MS
    if (fromT > maxT) {
      break
    }
    const toT = fromT + (size - 1) * DAY_MS
    const from = keyOfTime(fromT)
    const to = keyOfTime(toT)
    const count = list.filter((item) => {
      const key = getKey(item)
      return key >= from && key <= to
    }).length
    blocks.push({
      key: `${from}_${to}`,
      from,
      to,
      count,
      label: blockLabel(from, to),
      isCurrent: anchorKey >= from && anchorKey <= to
    })
  }
  return blocks
}

/**
 * 按选中段过滤列表；block 为 null 时表示「全部」，原样返回。
 * @param {Array} items
 * @param {{ from: string, to: string } | null} block
 * @param {(item: any) => string} getKey
 */
export function filterByBlock(items, block, getKey) {
  if (!block) {
    return items
  }
  return items.filter((item) => {
    const key = getKey(item)
    return key >= block.from && key <= block.to
  })
}
