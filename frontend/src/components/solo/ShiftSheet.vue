<script setup>
/**
 * 顺延抽屉：选中一个起点，整体平移它和它之后的预约。
 *
 * 先预览（只算不改），确认后执行；执行完把「待通知清单」交给页面去展示，
 * 不自动发消息——系统只提醒老板。
 */
import { computed, ref, watch } from 'vue'
import SoloSheet from './SoloSheet.vue'
import { applyShift, previewShift } from '../../api/solo'
import { timeText } from '../../utils/date'

const props = defineProps({
  visible: { type: Boolean, default: false },
  date: { type: String, default: '' },
  /** 从哪一单开始顺延（YYYY-MM-DD HH:mm:ss）。 */
  fromTime: { type: String, default: '' },
  bookingLabel: { type: String, default: '' }
})

const emit = defineEmits(['update:visible', 'shifted'])

const STEPS = [15, 30, 45, 60]

const minutes = ref(30)
const preview = ref(null)
const loading = ref(false)
const submitting = ref(false)
const errorText = ref('')

const canSubmit = computed(() => (preview.value?.count || 0) > 0 && !submitting.value)

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) {
      return
    }
    minutes.value = 30
    preview.value = null
    errorText.value = ''
    await loadPreview()
  }
)

watch(minutes, () => {
  if (props.visible) {
    loadPreview()
  }
})

async function loadPreview() {
  if (!props.date || !props.fromTime || !minutes.value) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const response = await previewShift({
      date: props.date,
      fromTime: props.fromTime,
      minutes: Number(minutes.value)
    })
    preview.value = response.data.data
  } catch (error) {
    preview.value = null
    errorText.value = error.response?.data?.message || '预览失败'
  } finally {
    loading.value = false
  }
}

async function confirm() {
  submitting.value = true
  errorText.value = ''
  try {
    const response = await applyShift({
      date: props.date,
      fromTime: props.fromTime,
      minutes: Number(minutes.value),
      remark: `从 ${timeText(props.fromTime)} 起顺延 ${minutes.value} 分钟`
    })
    emit('shifted', response.data.data)
    emit('update:visible', false)
  } catch (error) {
    errorText.value = error.response?.data?.message || '顺延失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <SoloSheet
    :visible="visible"
    title="顺延"
    :note="`从 ${timeText(fromTime)} ${bookingLabel} 起，后面的单整体往后挪`"
    @update:visible="emit('update:visible', $event)"
  >
    <section class="field">
      <div class="field-head">
        <span class="field-label">挪多久</span>
        <span class="field-hint">分钟</span>
      </div>
      <div class="steps">
        <button
          v-for="step in STEPS"
          :key="step"
          type="button"
          class="step"
          :class="{ 'is-active': Number(minutes) === step }"
          @click="minutes = step"
        >
          {{ step }}
        </button>
      </div>
      <label class="custom">
        自定义
        <input v-model.number="minutes" class="input-min" type="number" min="15" max="480" step="5" />
        分钟
      </label>
    </section>

    <section class="field">
      <div class="field-head">
        <span class="field-label">影响</span>
        <span class="field-hint mono">{{ loading ? '计算中…' : `${preview?.count || 0} 单` }}</span>
      </div>

      <p v-if="!loading && preview && preview.count === 0" class="empty-line">
        这个时间点之后没有可顺延的单。
      </p>

      <ul v-if="preview?.items?.length" class="plan">
        <li v-for="item in preview.items" :key="item.bookingId" class="plan-item">
          <span class="plan-client">
            <span class="mono">{{ item.clientCode }}</span> {{ item.clientNickname }}
          </span>
          <span class="plan-time mono">
            {{ timeText(item.oldStart) }} → {{ timeText(item.newStart) }}
          </span>
        </li>
      </ul>
    </section>

    <p v-if="errorText" class="error">{{ errorText }}</p>
    <p class="fineprint">确认后会逐单改期，并生成待通知清单；不会自动通知客户。</p>

    <template #footer>
      <button type="button" class="btn" @click="emit('update:visible', false)">取消</button>
      <button type="button" class="btn btn-primary wide" :disabled="!canSubmit" @click="confirm">
        {{ submitting ? '平移中…' : `确认顺延 ${preview?.count || 0} 单` }}
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

.steps {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s2);
}

.step {
  padding: 5px 12px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 12px;
}

.step.is-active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
}

.custom {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: var(--s2);
  color: var(--ink-4);
  font-size: 10px;
}

.input-min {
  width: 46px;
  padding: 4px 0;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 12px;
  text-align: left;
  outline: none;
}

.input-min:focus {
  border-bottom-color: var(--ink);
}

.plan {
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--line-soft);
}

.plan-item {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  padding: 6px 0;
  border-bottom: 1px solid var(--line-soft);
  font-size: 12px;
}

.plan-client {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plan-time {
  flex: none;
  margin-left: auto;
  color: var(--ink-3);
  font-size: 11px;
}

.empty-line {
  color: var(--ink-3);
  font-size: 11px;
}

.error {
  margin-bottom: var(--s2);
  color: var(--lacquer);
  font-size: 12px;
}

.fineprint {
  color: var(--ink-4);
  font-size: 10px;
  line-height: 1.7;
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
