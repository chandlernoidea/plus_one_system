<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createReschedule } from '../api/reschedule'
import { listSlots } from '../api/schedule'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  appointment: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const submitting = ref(false)
const loadingSlots = ref(false)
const slots = ref([])
const form = reactive({
  date: '',
  slotStart: '',
  reason: ''
})

const startOptions = computed(() => {
  const appointment = props.appointment
  if (!appointment || !slots.value.length) {
    return []
  }
  const duration = appointment.estDurationMinutes
  if (!duration || duration % 15 !== 0) {
    return []
  }
  const expected = duration / 15
  const oldStart = parseDateTime(appointment.confirmedStart || appointment.requestedStart)
  const oldEnd = oldStart
    ? new Date(oldStart.getTime() + duration * 60000)
    : null

  const options = []
  for (let index = 0; index < slots.value.length; index++) {
    const first = slots.value[index]
    const firstStart = parseDateTime(first.slotStart)
    if (!firstStart || firstStart.getTime() <= Date.now()) {
      continue
    }
    let usable = true
    for (let offset = 0; offset < expected; offset++) {
      const slot = slots.value[index + offset]
      if (!slot) {
        usable = false
        break
      }
      const expectedStart = new Date(firstStart.getTime() + offset * 15 * 60000)
      const actualStart = parseDateTime(slot.slotStart)
      if (!actualStart || actualStart.getTime() !== expectedStart.getTime()) {
        usable = false
        break
      }
      const bookedBySelf = slot.status === 'BOOKED'
        && oldStart && oldEnd
        && actualStart.getTime() >= oldStart.getTime()
        && actualStart.getTime() < oldEnd.getTime()
      if (slot.status !== 'AVAILABLE' && !bookedBySelf) {
        usable = false
        break
      }
    }
    if (usable) {
      options.push({
        label: first.slotStart.slice(11, 16),
        value: first.slotStart
      })
    }
  }
  return options
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open || !props.appointment) {
      return
    }
    const confirmedStart = props.appointment.confirmedStart || props.appointment.requestedStart || ''
    form.date = confirmedStart.slice(0, 10)
    form.slotStart = ''
    form.reason = ''
    loadSlots()
  }
)

watch(
  () => form.date,
  () => {
    if (props.modelValue) {
      form.slotStart = ''
      loadSlots()
    }
  }
)

function parseDateTime(value) {
  if (!value) {
    return null
  }
  const parsed = new Date(value.replace(' ', 'T'))
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

function disabledPastDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

async function loadSlots() {
  if (!props.appointment || !form.date) {
    slots.value = []
    return
  }
  loadingSlots.value = true
  try {
    const response = await listSlots({
      staffId: props.appointment.staffId,
      date: form.date
    })
    slots.value = response.data.data || []
  } catch (error) {
    slots.value = []
  } finally {
    loadingSlots.value = false
  }
}

async function submit() {
  if (!props.appointment) {
    return
  }
  if (!form.slotStart) {
    ElMessage.warning('请选择新的开始时间')
    return
  }

  submitting.value = true
  try {
    await createReschedule(props.appointment.id, {
      proposedStart: form.slotStart,
      reason: form.reason
    })
    ElMessage.success('改约提案已提交，等待对方回应')
    visible.value = false
    emit('success')
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="发起改约" width="480px">
    <el-form label-width="92px">
      <el-form-item label="原预约时间">
        {{ appointment?.confirmedStart || appointment?.requestedStart || '-' }}
      </el-form-item>
      <el-form-item label="预计时长">
        {{ appointment?.estDurationMinutes || '-' }} 分钟
      </el-form-item>
      <el-form-item label="新日期">
        <el-date-picker
          v-model="form.date"
          type="date"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          :disabled-date="disabledPastDate"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="新开始时间">
        <el-select
          v-model="form.slotStart"
          :loading="loadingSlots"
          placeholder="请选择可用的开始时间"
          style="width: 100%"
          no-data-text="该日期没有足够连续可用时段"
        >
          <el-option
            v-for="option in startOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="改约原因">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="选填"
        />
      </el-form-item>
      <div class="tip">
        只展示能覆盖完整服务时长的连续时段；新旧时段重叠时，原预约占用的槽也会被允许选择。
      </div>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交提案</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tip {
  padding-left: 92px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
</style>
