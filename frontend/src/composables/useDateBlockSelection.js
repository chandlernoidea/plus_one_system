/**
 * 日期分段选择：把「列表 + 每 3 天一段」的交互收敛成一个组合式函数。
 *
 * 管理员预约管理、物料准备、美甲师预约管理三处原本各写了一份
 * 「生成分段 / 记住选中 / 默认落到今天 / 过滤列表」的逻辑，这里收口，
 * 保证三处的默认行为一致，也避免以后改分段规则时改三个地方。
 *
 * 使用方式：
 *   const { blockKey, blocks, activeBlock, visible, pick, applyDefault }
 *     = useDateBlockSelection(appointments, dayOf)
 * 数据加载完成后调用一次 applyDefault()。
 */
import { computed, ref } from 'vue'
import { buildDateBlocks, filterByBlock } from '../utils/dateBlocks'

/**
 * @param {import('vue').Ref<Array>} source 原始列表（ref）
 * @param {(item: any) => string} getKey 条目 → 'YYYY-MM-DD'
 * @param {{ autoSelectCurrent?: boolean, size?: number }} options
 *   autoSelectCurrent=false 时不自动落到今天段（例如美甲师待审批列表，
 *   默认必须看到全部，否则会把未来几天的单藏起来）
 */
export function useDateBlockSelection(source, getKey, options = {}) {
  const autoSelectCurrent = options.autoSelectCurrent !== false
  const size = options.size || 3

  const blockKey = ref(null)
  // 用户是否主动点了「全部」；主动选择后，刷新数据不应把选择重置回今天段
  const explicitAll = ref(false)
  // 是否已经做过一次默认选择
  const initialised = ref(false)

  const blocks = computed(() => buildDateBlocks(source.value, getKey, { size }))
  const activeBlock = computed(
    () => blocks.value.find((block) => block.key === blockKey.value) || null
  )
  const visible = computed(() => filterByBlock(source.value, activeBlock.value, getKey))

  /** 选中某段；传 null 表示「全部」 */
  function pick(key) {
    blockKey.value = key
    explicitAll.value = key === null
  }

  /**
   * 数据加载后的默认选择：
   * - 用户已主动选「全部」→ 尊重选择；
   * - 首次加载，或选中的段在当前数据里不存在（例如切了状态筛选）→ 落到今天所在段；
   * - 找不到今天段时退回第一段。
   */
  function applyDefault() {
    if (!autoSelectCurrent || explicitAll.value) {
      return
    }
    const exists = blocks.value.some((block) => block.key === blockKey.value)
    if (!initialised.value || !exists) {
      const fallback = blocks.value.find((block) => block.isCurrent) || blocks.value[0]
      blockKey.value = fallback ? fallback.key : null
      initialised.value = true
    }
  }

  return { blockKey, blocks, activeBlock, visible, pick, applyDefault }
}
