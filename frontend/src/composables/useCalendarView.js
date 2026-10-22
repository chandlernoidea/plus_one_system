import { computed, ref } from 'vue'
import {
  addDays,
  addMonths,
  endOfMonth,
  endOfWeek,
  startOfMonth,
  startOfWeek,
  toKey
} from '../utils/date'

/**
 * 日历的浏览状态：当前看向哪一天、月视图还是周视图、选中的是哪一天。
 * 三个角色共用同一套浏览逻辑，差异只体现在数据口径上。
 */
export function useCalendarView() {
  const mode = ref('month')
  const cursor = ref(new Date())
  const selectedKey = ref(toKey(new Date()))

  const range = computed(() => {
    if (mode.value === 'month') {
      return {
        from: toKey(startOfMonth(cursor.value)),
        to: toKey(endOfMonth(cursor.value))
      }
    }
    return {
      from: toKey(startOfWeek(cursor.value)),
      to: toKey(endOfWeek(cursor.value))
    }
  })

  function selectDay(key) {
    selectedKey.value = key
  }

  function goToday() {
    const today = new Date()
    cursor.value = today
    selectedKey.value = toKey(today)
  }

  function shift(amount) {
    if (mode.value === 'month') {
      const next = addMonths(cursor.value, amount)
      cursor.value = next
      selectedKey.value = toKey(startOfMonth(next))
      return
    }
    const next = addDays(cursor.value, amount * 7)
    cursor.value = next
    selectedKey.value = toKey(next)
  }

  function goPrev() {
    shift(-1)
  }

  function goNext() {
    shift(1)
  }

  function setMode(next) {
    if (next === mode.value) {
      return
    }
    mode.value = next
    cursor.value = selectedKey.value ? new Date(`${selectedKey.value}T00:00:00`) : new Date()
  }

  return {
    mode,
    cursor,
    selectedKey,
    range,
    selectDay,
    goToday,
    goPrev,
    goNext,
    setMode
  }
}
