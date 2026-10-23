/**
 * 按「每 N 天一段」把列表切成可点选的日期块。
 * 以今天所在的块为锚点向两侧展开，保证「今天」永远落在某个块里，
 * 各块之间不重叠也不留空档。
 */
import { toKey } from './date'

const DAY_MS = 86400000

function dayStart(dateKey) {
  return new Date(`${dateKey}T00:00:00`).getTime()
}

function keyOfTime(time) {
  const date = new Date(time)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function fmt(dateKey) {
  return `${dateKey.slice(5, 7)}.${dateKey.slice(8, 10)}`
}

/** 同一月份内简写成 10.23–25，跨月写成 10.31–11.02 */
export function blockLabel(from, to) {
  if (from.slice(0, 7) === to.slice(0, 7)) {
    return `${fmt(from)}–${to.slice(8, 10)}`
  }
  return `${fmt(from)}–${fmt(to)}`
}

/**
 * @param {Array} items 原始列表
 * @param {(item: any) => string} getKey 取出 'YYYY-MM-DD'
 * @param {{ size?: number, anchorKey?: string }} options
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

export function filterByBlock(items, block, getKey) {
  if (!block) {
    return items
  }
  return items.filter((item) => {
    const key = getKey(item)
    return key >= block.from && key <= block.to
  })
}

/** 段内按天计数，用于「10.23 · 4 单 / 10.24 · 3 单」这样的说明 */
export function countByDay(items, getKey) {
  const map = new Map()
  for (const item of items) {
    const key = getKey(item)
    if (!key) {
      continue
    }
    map.set(key, (map.get(key) || 0) + 1)
  }
  return map
}
