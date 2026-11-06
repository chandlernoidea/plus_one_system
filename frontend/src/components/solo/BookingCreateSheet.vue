<script setup>
/**
 * 建单抽屉：选客户 → 选款式（可多选，顺序即连做顺序）→ 定时间 → 收定金 → 保存。
 *
 * 保存成功后如果勾了「收定金」，紧接着记一条 COLLECT 流水；
 * 冲突时把后端 409 返回的冲突单摘要摊开，不静默覆盖。
 */
import { computed, ref, watch } from 'vue'
import SoloSheet from './SoloSheet.vue'
import { collectDeposit, createBooking, createClient, jumpBooking, searchClients } from '../../api/solo'
import { formatMoney } from '../../utils/date'

const props = defineProps({
  visible: { type: Boolean, default: false },
  date: { type: String, default: '' },
  /** 从空档点进来时预填开始时间（HH:mm）。 */
  presetTime: { type: String, default: '' },
  /** normal 常规建单；jump 插队（走后端空档校验，不能强排）。 */
  mode: { type: String, default: 'normal' },
  styles: { type: Array, default: () => [] }
})

const isJump = computed(() => props.mode === 'jump')

const emit = defineEmits(['update:visible', 'created'])

const keyword = ref('')
const clients = ref([])
const selectedClient = ref(null)

const selectedStyles = ref([])
const dateValue = ref('')
const timeValue = ref('10:00')
const buffer = ref(20)
const remark = ref('')
const withDeposit = ref(true)

const submitting = ref(false)
const errorText = ref('')
const conflicts = ref([])

const newClientName = ref('')
const creatingClient = ref(false)

const totalMinutes = computed(() =>
  selectedStyles.value.reduce((sum, style) => sum + Number(style.durationMin || 0), 0)
)
const totalPrice = computed(() =>
  selectedStyles.value.reduce((sum, style) => sum + Number(style.price || 0), 0)
)
const occupyMinutes = computed(() => totalMinutes.value + Number(buffer.value || 0))

const endText = computed(() => {
  if (!timeValue.value || !totalMinutes.value) {
    return ''
  }
  const [hours, minutes] = timeValue.value.split(':').map(Number)
  const end = hours * 60 + minutes + occupyMinutes.value
  return `${String(Math.floor(end / 60) % 24).padStart(2, '0')}:${String(end % 60).padStart(2, '0')}`
})

function defaultStart() {
  if (props.presetTime) {
    return { date: props.date || toDateString(new Date()), time: props.presetTime }
  }
  const now = new Date()
  const minutes = now.getMinutes()
  const rounded = new Date(now.getTime() + (30 - (minutes % 30)) * 60000)
  // 太晚就把默认时间放到明天 10:00，避免一打开就是个已经过去的时段
  if (rounded.getHours() >= 22) {
    const tomorrow = new Date(now.getTime() + 86400000)
    return { date: props.date || toDateString(tomorrow), time: '10:00' }
  }
  const hour = String(rounded.getHours()).padStart(2, '0')
  const minute = String(rounded.getMinutes()).padStart(2, '0')
  return { date: props.date || toDateString(now), time: `${hour}:${minute}` }
}

function toDateString(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function reset() {
  const start = defaultStart()
  keyword.value = ''
  selectedClient.value = null
  selectedStyles.value = []
  dateValue.value = start.date
  timeValue.value = start.time
  buffer.value = 20
  remark.value = ''
  withDeposit.value = true
  errorText.value = ''
  conflicts.value = []
  newClientName.value = ''
  loadClients('')
}

let searchTimer = null

watch(keyword, (value) => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadClients(value), 220)
})

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      reset()
    }
  }
)

async function loadClients(text) {
  try {
    const response = await searchClients(text)
    clients.value = response.data.data || []
  } catch (error) {
    clients.value = []
  }
}

function pickClient(client) {
  selectedClient.value = client
  keyword.value = ''
}

function clearClient() {
  selectedClient.value = null
  loadClients('')
}

function toggleStyle(style) {
  const index = selectedStyles.value.findIndex((item) => item.id === style.id)
  if (index >= 0) {
    selectedStyles.value.splice(index, 1)
  } else {
    selectedStyles.value.push(style)
  }
}

function isPicked(style) {
  return selectedStyles.value.some((item) => item.id === style.id)
}

async function quickAddClient() {
  const name = newClientName.value.trim()
  if (!name) {
    return
  }
  creatingClient.value = true
  try {
    const response = await createClient({ nickname: name })
    const created = response.data.data
    clients.value = [created, ...clients.value]
    selectedClient.value = created
    newClientName.value = ''
  } catch (error) {
    // 提示已由 request 拦截器统一弹出
  } finally {
    creatingClient.value = false
  }
}

async function submit(force = false) {
  errorText.value = ''
  conflicts.value = []

  if (!selectedClient.value) {
    errorText.value = '先选一个客户'
    return
  }
  if (!selectedStyles.value.length) {
    errorText.value = '至少选一个款式'
    return
  }
  if (!dateValue.value || !timeValue.value) {
    errorText.value = '选一个开始时间'
    return
  }

  submitting.value = true
  try {
    const payload = {
      clientId: selectedClient.value.id,
      startAt: `${dateValue.value} ${timeValue.value}:00`,
      bufferMinutes: Number(buffer.value || 0),
      source: isJump.value ? 'JUMP' : 'NORMAL',
      remark: remark.value.trim() || undefined,
      items: selectedStyles.value.map((style) => ({ styleId: style.id }))
    }
    if (force) {
      payload.force = true
    }
    const response = isJump.value ? await jumpBooking(payload) : await createBooking(payload)
    const booking = response.data.data

    if (withDeposit.value) {
      try {
        await collectDeposit(booking.id, { type: 'COLLECT', reason: '建单时收取' })
      } catch (error) {
        // 定金没记上不影响建单本身，页面会显示「未收」，可再补
      }
    }

    emit('created', booking)
    emit('update:visible', false)
  } catch (error) {
    const body = error.response?.data
    if (error.response?.status === 409) {
      conflicts.value = Array.isArray(body?.data) ? body.data : []
      errorText.value = body?.message || '该时段已被占用'
    } else {
      errorText.value = body?.message || (isJump.value ? '插队失败' : '建单失败')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <SoloSheet
    :visible="visible"
    :title="isJump ? '插一单' : '建一单'"
    :note="
      isJump
        ? '只能插进已释放的空档，时长 + 缓冲塞不下会被拦下'
        : '选客户 → 选款式 → 定时间，占用 = 项目时长 + 缓冲'
    "
    @update:visible="emit('update:visible', $event)"
  >
    <!-- 客户 -->
    <section class="field">
      <div class="field-head">
        <span class="field-label">客户</span>
        <span class="field-hint">短号 / 昵称 / 手机后四位都能搜</span>
      </div>

      <div v-if="selectedClient" class="picked">
        <span class="picked-code mono">{{ selectedClient.code }}</span>
        <span class="picked-name">{{ selectedClient.nickname }}</span>
        <button type="button" class="text-btn" @click="clearClient">换一个</button>
      </div>

      <template v-else>
        <input v-model="keyword" class="input" type="search" placeholder="搜客户…" />
        <ul class="client-list" v-if="clients.length">
          <li v-for="client in clients" :key="client.id">
            <button type="button" class="client-item" @click="pickClient(client)">
              <span class="mono">{{ client.code }}</span>
              <span class="client-name">{{ client.nickname }}</span>
              <span class="mono client-tail" v-if="client.phoneTail">{{ client.phoneTail }}</span>
            </button>
          </li>
        </ul>
        <div class="quick-add">
          <input
            v-model="newClientName"
            class="input"
            type="text"
            placeholder="新客户昵称，回车快速建档"
            @keyup.enter="quickAddClient"
          />
          <button type="button" class="btn nowrap" :disabled="creatingClient" @click="quickAddClient">
            建档
          </button>
        </div>
      </template>
    </section>

    <!-- 款式 -->
    <section class="field">
      <div class="field-head">
        <span class="field-label">款式</span>
        <span class="field-hint">可多选，按点选顺序连做</span>
      </div>
      <div class="style-grid" v-if="styles.length">
        <button
          v-for="style in styles"
          :key="style.id"
          type="button"
          class="style-chip"
          :class="{ 'is-picked': isPicked(style) }"
          @click="toggleStyle(style)"
        >
          <span class="style-name">{{ style.name }}</span>
          <span class="style-meta mono">{{ style.durationMin }}′ · {{ formatMoney(style.price) }}</span>
        </button>
      </div>
      <p v-else class="empty-line">款式库还是空的，去「款式库」建一个。</p>
    </section>

    <!-- 时间 -->
    <section class="field">
      <div class="field-head">
        <span class="field-label">时间</span>
        <span class="field-hint" v-if="endText">预计 {{ endText }} 结束</span>
      </div>
      <div class="row">
        <input v-model="dateValue" class="input" type="date" />
        <input v-model="timeValue" class="input" type="time" step="900" />
      </div>
      <div class="row">
        <label class="inline-label">
          缓冲
          <input v-model.number="buffer" class="input input-narrow" type="number" min="0" max="120" step="5" />
          分钟
        </label>
        <span v-if="selectedStyles.length" class="occupy mono">占用 {{ occupyMinutes }} 分钟</span>
      </div>
    </section>

    <!-- 备注与定金 -->
    <section class="field">
      <div class="field-head">
        <span class="field-label">备注</span>
      </div>
      <input v-model="remark" class="input" type="text" placeholder="要带的图、色号、加急…" />

      <label class="check-line">
        <input v-model="withDeposit" type="checkbox" />
        <span>同时收定金 ¥30</span>
      </label>
    </section>

    <p v-if="errorText" class="error">{{ errorText }}</p>
    <div v-if="conflicts.length" class="conflict">
      <p class="conflict-title">撞上的单：</p>
      <ul>
        <li v-for="item in conflicts" :key="item.bookingId" class="conflict-item mono">
          {{ item.clientCode }} {{ item.clientNickname }} · {{ item.startAt?.slice(11, 16) }}–{{ item.endAt?.slice(11, 16) }}
        </li>
      </ul>
      <button v-if="!isJump" type="button" class="btn force" :disabled="submitting" @click="submit(true)">
        仍然强排（作业单里会标红）
      </button>
    </div>

    <template #footer>
      <button type="button" class="btn" @click="emit('update:visible', false)">取消</button>
      <button type="button" class="btn btn-primary wide" :disabled="submitting" @click="submit()">
        {{ submitting ? '保存中…' : `保存${totalPrice ? ' · ' + formatMoney(totalPrice, { sign: true }) : ''}` }}
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

.input-narrow {
  width: 56px;
  padding: 2px 0;
  text-align: center;
}

.picked {
  display: flex;
  align-items: center;
  gap: var(--s2);
  padding: 8px 0;
  border-bottom: 1px solid var(--line);
}

.picked-code {
  font-size: 10px;
  color: var(--ink-3);
  border: 1px solid var(--line);
  padding: 1px 4px;
}

.picked-name {
  font-family: var(--font-display);
  font-size: 15px;
}

.text-btn {
  margin-left: auto;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-size: 11px;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.client-list {
  max-height: 168px;
  margin: var(--s1) 0 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
  border-bottom: 1px solid var(--line-soft);
}

.client-item {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  width: 100%;
  padding: 7px 0;
  border: 0;
  border-bottom: 1px solid var(--line-soft);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-size: 12px;
  text-align: left;
}

.client-item:hover {
  color: var(--lacquer);
}

.client-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.client-tail {
  margin-left: auto;
  color: var(--ink-4);
  font-size: 10px;
}

.quick-add {
  display: flex;
  align-items: center;
  gap: var(--s2);
  margin-top: var(--s2);
}

.quick-add .input {
  flex: 1 1 auto;
}

.nowrap {
  flex: none;
  white-space: nowrap;
}

.style-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s2);
}

.style-chip {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 6px 10px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  text-align: left;
}

.style-chip.is-picked {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
}

.style-name {
  font-size: 13px;
}

.style-meta {
  font-size: 10px;
  opacity: 0.72;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--s3);
  margin-top: var(--s2);
}

.row .input {
  flex: 1 1 0;
}

.inline-label {
  display: flex;
  align-items: center;
  gap: var(--s1);
  color: var(--ink-3);
  font-size: 11px;
}

.occupy {
  margin-left: auto;
  color: var(--ink-3);
  font-size: 10px;
}

.check-line {
  display: flex;
  align-items: center;
  gap: var(--s2);
  margin-top: var(--s3);
  color: var(--ink-2);
  font-size: 12px;
  cursor: pointer;
}

.empty-line {
  color: var(--ink-3);
  font-size: 12px;
}

.error {
  margin-bottom: var(--s2);
  color: var(--lacquer);
  font-size: 12px;
}

.conflict {
  padding: var(--s2) var(--s3);
  border-left: 2px solid var(--lacquer);
  background: var(--lacquer-wash);
}

.conflict-title {
  color: var(--lacquer-deep);
  font-size: 11px;
}

.conflict-item {
  color: var(--ink-2);
  font-size: 11px;
  line-height: 1.7;
}

.force {
  margin-top: var(--s2);
  border-color: var(--lacquer);
  color: var(--lacquer-deep);
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

.btn:hover {
  border-color: var(--ink);
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

.mono {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}
</style>
