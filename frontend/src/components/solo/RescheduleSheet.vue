<script setup>
/**
 * 改期抽屉。
 *
 * 先按 NONE 提交，让后端判定「免费 / 需扣定金」：
 * 免费就直接改完；需扣定金时把三个选项摆出来，老板选完再提交一次。
 */
import { computed, ref, watch } from 'vue'
import SoloSheet from './SoloSheet.vue'
import { rescheduleBooking } from '../../api/solo'

const props = defineProps({
  visible: { type: Boolean, default: false },
  booking: { type: Object, default: null }
})

const emit = defineEmits(['update:visible', 'updated'])

const dateValue = ref('')
const timeValue = ref('')
const reason = ref('')
const submitting = ref(false)
const hint = ref('')
const needDecision = ref(false)
const errorText = ref('')

const occupyText = computed(() => {
  if (!props.booking) {
    return ''
  }
  return `${props.booking.plannedMinutes} + ${props.booking.bufferMinutes} 分钟缓冲`
})

watch(
  () => props.visible,
  (visible) => {
    if (!visible || !props.booking) {
      return
    }
    const start = String(props.booking.startAt || '')
    dateValue.value = start.slice(0, 10)
    timeValue.value = start.slice(11, 16)
    reason.value = ''
    hint.value = ''
    errorText.value = ''
    needDecision.value = false
  }
)

async function submit(decision = 'NONE') {
  errorText.value = ''
  if (!dateValue.value || !timeValue.value) {
    errorText.value = '选一个时间'
    return
  }

  submitting.value = true
  try {
    const response = await rescheduleBooking(props.booking.id, {
      newStart: `${dateValue.value} ${timeValue.value}:00`,
      initiator: 'CLIENT',
      reason: reason.value.trim() || undefined,
      depositDecision: decision
    })
    const result = response.data.data
    if (result.changed) {
      emit('updated', result)
      emit('update:visible', false)
      return
    }
    needDecision.value = true
    hint.value = result.message
  } catch (error) {
    errorText.value = error.response?.data?.message || '改期失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <SoloSheet
    :visible="visible"
    title="改期"
    :note="booking ? `${booking.clientCode} ${booking.clientNickname} · ${occupyText}` : ''"
    @update:visible="emit('update:visible', $event)"
  >
    <section class="field">
      <div class="field-head">
        <span class="field-label">新的开始时间</span>
        <span class="field-hint">原 {{ booking?.startAt?.slice(11, 16) }}</span>
      </div>
      <div class="row">
        <input v-model="dateValue" class="input" type="date" />
        <input v-model="timeValue" class="input" type="time" step="900" />
      </div>
    </section>

    <section class="field">
      <div class="field-head">
        <span class="field-label">原因</span>
      </div>
      <input v-model="reason" class="input" type="text" placeholder="客户临时有事 / 老板让位…" />
    </section>

    <div v-if="hint" class="hint">
      <p>{{ hint }}</p>
      <div class="decisions" v-if="needDecision">
        <button type="button" class="btn" :disabled="submitting" @click="submit('REFUND')">
          退定金并改期
        </button>
        <button type="button" class="btn" :disabled="submitting" @click="submit('FORFEIT')">
          没收定金并改期
        </button>
        <button type="button" class="btn" :disabled="submitting" @click="submit('FREE')">
          仍然免费改期
        </button>
      </div>
    </div>

    <p v-if="errorText" class="error">{{ errorText }}</p>

    <template #footer>
      <button type="button" class="btn" @click="emit('update:visible', false)">取消</button>
      <button type="button" class="btn btn-primary wide" :disabled="submitting" @click="submit('NONE')">
        {{ submitting ? '提交中…' : '确认改期' }}
      </button>
    </template>
  </SoloSheet>
</template>

<style scoped>
.field {
  margin-bottom: var(--s5);
}

.field-head {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  margin-bottom: var(--s2);
}

.field-label {
  font-family: var(--font-display);
  font-size: 14px;
  letter-spacing: 0.06em;
}

.field-hint {
  color: var(--ink-4);
  font-size: 10px;
}

.input {
  width: 100%;
  min-width: 0;
  padding: 8px 0;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font-family: var(--font-ui);
  font-size: 14px;
  outline: none;
}

.input:focus {
  border-bottom-color: var(--ink);
}

.row {
  display: flex;
  gap: var(--s3);
}

.row .input {
  flex: 1 1 0;
}

.hint {
  padding: var(--s2) var(--s3);
  border-left: 2px solid var(--ochre);
  background: rgba(138, 101, 38, 0.06);
  color: var(--ink-2);
  font-size: 12px;
  line-height: 1.6;
}

.decisions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s2);
  margin-top: var(--s3);
}

.error {
  margin-top: var(--s2);
  color: var(--lacquer);
  font-size: 12px;
}

.btn {
  padding: 6px 14px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
  letter-spacing: 0.06em;
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.btn-primary {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
}

.wide {
  flex: 1 1 auto;
}
</style>
