<script setup>
/**
 * 作业单卡片：一单的身家信息 + 当前能做的动作。
 *
 * 不在这里发请求，只抛事件给页面；页面统一调接口后刷新，
 * 避免卡片各自维护一份状态导致前后端不一致。
 */
import { computed, ref } from 'vue'
import { formatMoney } from '../../utils/date'

const props = defineProps({
  booking: { type: Object, required: true },
  cover: { type: String, default: '' },
  usageCount: { type: Number, default: 0 },
  /** 强排撞档：标红提示，但数据照旧保留。 */
  conflicting: { type: Boolean, default: false }
})

const emit = defineEmits([
  'arrive',
  'start',
  'complete',
  'reschedule',
  'cancel',
  'no-show',
  'deposit',
  'materials'
])

const STATUS = {
  WAIT_DEPOSIT: { label: '待定金', tone: 'wait' },
  BOOKED: { label: '已定档', tone: 'booked' },
  ARRIVED: { label: '已到店', tone: 'arrived' },
  IN_SERVICE: { label: '服务中', tone: 'service' },
  COMPLETED: { label: '已完成', tone: 'done' },
  CANCELLED: { label: '已取消', tone: 'void' },
  NO_SHOW: { label: '爽约', tone: 'void' }
}

const DEPOSIT = {
  NONE: '未收',
  PAID: '已收',
  OFFSET: '已抵扣',
  REFUNDED: '已退',
  FORFEITED: '已没收'
}

const coverBroken = ref(false)

const status = computed(() => STATUS[props.booking.status] || { label: props.booking.status, tone: '' })
const items = computed(() => props.booking.items || [])
const multi = computed(() => items.value.length > 1)
const totalMinutes = computed(
  () => (props.booking.plannedMinutes || 0) + (props.booking.bufferMinutes || 0)
)
const totalPrice = computed(() =>
  items.value.reduce((sum, item) => sum + Number(item.price || 0), 0)
)
const depositLabel = computed(() => {
  const key = props.booking.depositState || 'NONE'
  const text = DEPOSIT[key] || key
  const collected = Number(props.booking.depositCollected || 0)
  return collected > 0 && key !== 'NONE' ? `${text} ${formatMoney(collected)}` : text
})
const usageLabel = computed(() => {
  if (!props.usageCount) {
    return '未领'
  }
  return `已领 ${props.usageCount}`
})

/** 不同状态下可做的动作；顺序即按钮顺序。 */
const actions = computed(() => {
  switch (props.booking.status) {
    case 'WAIT_DEPOSIT':
      return [
        { key: 'deposit', label: '收定金', primary: true },
        { key: 'reschedule', label: '改期' },
        { key: 'cancel', label: '取消' },
        { key: 'no-show', label: '爽约' }
      ]
    case 'BOOKED':
      return [
        { key: 'arrive', label: '到店', primary: true },
        { key: 'reschedule', label: '改期' },
        { key: 'cancel', label: '取消' },
        { key: 'no-show', label: '爽约' }
      ]
    case 'ARRIVED':
      return [
        { key: 'start', label: '开始', primary: true },
        { key: 'reschedule', label: '改期' },
        { key: 'cancel', label: '取消' }
      ]
    case 'IN_SERVICE':
      return [{ key: 'complete', label: '完成', primary: true }]
    default:
      return []
  }
})

function fire(key) {
  emit(key)
}

function hideCover() {
  coverBroken.value = true
}
</script>

<template>
  <article class="card" :class="[`tone-${status.tone}`, { 'is-conflict': conflicting }]">
    <div class="card-main">
      <div class="card-head">
        <span class="client">
          <span class="code mono">{{ booking.clientCode || '—' }}</span>
          <span class="name">{{ booking.clientNickname }}</span>
        </span>
        <span class="status">{{ status.label }}</span>
      </div>

      <div class="items">
        <div v-for="item in items" :key="item.id" class="item">
          <span class="item-name">{{ item.styleName }}</span>
          <span class="item-duration mono">{{ item.durationMin }}′</span>
          <span class="item-price mono">{{ formatMoney(item.price) }}</span>
        </div>
      </div>

      <p class="remark" v-if="booking.remark">{{ booking.remark }}</p>

      <div class="chips">
        <span class="chip">定金 {{ depositLabel }}</span>
        <button type="button" class="chip chip-btn" @click.stop="emit('materials')">料 {{ usageLabel }}</button>
        <span class="chip" v-if="multi">连做 {{ items.length }} 项</span>
        <span class="chip" v-if="booking.source === 'JUMP'">插队</span>
        <span class="chip mono">{{ totalMinutes }} 分钟 · {{ formatMoney(totalPrice, { sign: true }) }}</span>
      </div>

      <div class="actions" v-if="actions.length">
        <button
          v-for="action in actions"
          :key="action.key"
          type="button"
          class="btn"
          :class="{ 'btn-primary': action.primary }"
          @click="fire(action.key)"
        >
          {{ action.label }}
        </button>
      </div>
    </div>

    <div class="thumb" v-if="cover && !coverBroken">
      <img :src="cover" :alt="items[0]?.styleName || '款式'" @error="hideCover" />
    </div>
  </article>
</template>

<style scoped>
.card {
  display: flex;
  gap: var(--s3);
  padding: var(--s2) var(--s3) var(--s2) var(--s4);
  border: 1px solid var(--line);
  border-left-width: 2px;
  background: var(--paper-raised);
}

/* 左侧竖线用状态色标出，扫一眼就知道这单处在哪一步 */
.tone-wait {
  border-left-color: var(--ochre);
}

.tone-booked {
  border-left-color: var(--moss);
}

.tone-arrived {
  border-left-color: var(--slate);
}

.tone-service {
  border-left-color: var(--lacquer);
}

.tone-done {
  border-left-color: var(--ink-4);
}

.tone-void {
  border-left-color: var(--line-strong);
  opacity: 0.62;
}

.card-main {
  min-width: 0;
  flex: 1 1 auto;
}

.card-head {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
}

.client {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: var(--s2);
}

.code {
  flex: none;
  padding: 1px 4px;
  border: 1px solid var(--line);
  color: var(--ink-3);
  font-size: 10px;
  letter-spacing: 0.06em;
  line-height: 1.4;
}

.name {
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status {
  flex: none;
  margin-left: auto;
  color: var(--ink-3);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.tone-booked .status {
  color: var(--moss);
}

.tone-wait .status {
  color: var(--ochre);
}

.tone-service .status {
  color: var(--lacquer);
}

.items {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin-top: 6px;
}

.item {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  min-width: 0;
  font-size: 12px;
  line-height: 1.5;
}

.item-name {
  overflow: hidden;
  color: var(--ink-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-duration {
  flex: none;
  color: var(--ink-4);
  font-size: 10px;
}

.item-price {
  flex: none;
  margin-left: auto;
  color: var(--ink-3);
  font-size: 11px;
}

.remark {
  margin-top: var(--s2);
  overflow-wrap: anywhere;
  color: var(--ink-3);
  font-size: 11px;
  line-height: 1.5;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s1) var(--s2);
  margin-top: 6px;
}

.card.is-conflict {
  border-color: var(--lacquer);
  background: var(--lacquer-wash);
}

.chip-conflict {
  border-color: var(--lacquer);
  color: var(--lacquer-deep);
}

.chip-btn {
  cursor: pointer;
  font-family: var(--font-ui);
  transition: border-color var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.chip-btn:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.chip {
  padding: 1px 5px;
  border: 1px solid var(--line-soft);
  color: var(--ink-3);
  font-size: 10px;
  letter-spacing: 0.04em;
  line-height: 1.6;
  white-space: nowrap;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: var(--s2);
  padding-top: 6px;
  border-top: 1px dashed var(--line);
}

.btn {
  padding: 4px 11px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
  letter-spacing: 0.06em;
  line-height: 1.4;
  transition: background var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.btn:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.btn-primary {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
}

.btn-primary:hover {
  background: var(--lacquer-deep);
  border-color: var(--lacquer-deep);
  color: var(--paper-raised);
}

.thumb {
  flex: none;
  width: 46px;
  height: 58px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: var(--paper-sunken);
}

.thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
