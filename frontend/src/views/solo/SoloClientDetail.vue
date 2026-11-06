<script setup>
/**
 * 客户详情（P5）：档案 + 历史预约 + 做过的款式。
 *
 * 二级页，左上角有「← 客户」；在电脑上同一套内容排成两栏。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SoloLayout from './SoloLayout.vue'
import SoloSheet from '../../components/solo/SoloSheet.vue'
import * as api from '../../api/solo'
import { formatMoney, timeText, toKey } from '../../utils/date'

const route = useRoute()
const router = useRouter()

const detail = ref(null)
const loading = ref(false)
const editVisible = ref(false)
const deleteVisible = ref(false)
const submitting = ref(false)
const form = ref(emptyForm())

const STATUS_LABEL = {
  WAIT_DEPOSIT: '待定金',
  BOOKED: '已定档',
  ARRIVED: '已到店',
  IN_SERVICE: '服务中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  NO_SHOW: '爽约'
}

function emptyForm() {
  return { nickname: '', phoneTail: '', source: '', colorNotes: '', remark: '' }
}

async function load() {
  loading.value = true
  try {
    const response = await api.fetchClientDetail(route.params.id)
    detail.value = response.data.data
  } catch (error) {
    detail.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)

const client = computed(() => detail.value?.client || null)
const bookings = computed(() => detail.value?.bookings || [])
const styleUsage = computed(() => detail.value?.styleUsage || [])
const stats = computed(() => [
  { label: '预约', value: detail.value?.totalBookings || 0 },
  { label: '完成', value: detail.value?.completedBookings || 0 },
  { label: '做过的款', value: styleUsage.value.length }
])

function openEdit() {
  form.value = {
    nickname: client.value?.nickname || '',
    phoneTail: client.value?.phoneTail || '',
    source: client.value?.source || '',
    colorNotes: client.value?.colorNotes || '',
    remark: client.value?.remark || ''
  }
  editVisible.value = true
}

async function submitEdit() {
  if (!form.value.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  submitting.value = true
  try {
    await api.updateClient(client.value.id, {
      nickname: form.value.nickname.trim(),
      phoneTail: form.value.phoneTail.trim(),
      source: form.value.source.trim(),
      colorNotes: form.value.colorNotes.trim(),
      remark: form.value.remark.trim()
    })
    ElMessage.success('已更新')
    editVisible.value = false
    await load()
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    submitting.value = false
  }
}

async function confirmDelete() {
  submitting.value = true
  try {
    await api.deleteClient(client.value.id)
    ElMessage.success('客户已删除，历史预约保留')
    deleteVisible.value = false
    router.replace('/solo/clients')
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    submitting.value = false
  }
}

function openBooking(booking) {
  router.push({ path: '/solo/today', query: { date: booking.startAt?.slice(0, 10) } })
}

function bookingDate(booking) {
  const key = booking.startAt?.slice(0, 10) || ''
  if (key === toKey(new Date())) {
    return '今天'
  }
  return key.slice(5).replace('-', '/')
}

function bookingStyles(booking) {
  return (booking.items || []).map((item) => item.styleName).join(' + ') || '—'
}

function bookingTotal(booking) {
  return (booking.items || []).reduce((sum, item) => sum + Number(item.price || 0), 0)
}
</script>

<template>
  <SoloLayout back-to="/solo/clients" back-label="客户">
    <p v-if="loading" class="empty-line">读取中…</p>

    <div v-else-if="!client" class="empty-note">
      没有这个客户。
      <em>可能已被删除，或者链接不对。</em>
    </div>

    <template v-else>
      <div class="detail-grid">
        <div class="col-left">
      <header class="profile">
        <div class="profile-main">
          <span class="code mono">{{ client.code }}</span>
          <h1 class="name">{{ client.nickname }}</h1>
          <span v-if="client.deleted" class="badge">已删除</span>
        </div>
        <p class="meta">
          <span v-if="client.phoneTail" class="mono">尾号 {{ client.phoneTail }}</span>
          <span v-if="client.source">· {{ client.source }}</span>
          <span v-if="client.firstVisitDate">· 首访 {{ client.firstVisitDate }}</span>
          <span v-if="client.lastVisitDate">· 最近 {{ client.lastVisitDate }}</span>
        </p>

        <dl class="stats">
          <div v-for="item in stats" :key="item.label" class="stat">
            <dt>{{ item.label }}</dt>
            <dd class="num">{{ item.value }}</dd>
          </div>
        </dl>

        <div class="actions">
          <button type="button" class="btn" @click="openEdit">编辑</button>
          <button type="button" class="btn danger" @click="deleteVisible = true">删除</button>
        </div>
      </header>

      <section class="block" v-if="client.colorNotes || client.remark">
        <div class="block-head"><span class="block-title">档案</span><span class="block-line"></span></div>
        <p v-if="client.colorNotes" class="line"><span class="line-label">常用色号</span>{{ client.colorNotes }}</p>
        <p v-if="client.remark" class="line"><span class="line-label">备注</span>{{ client.remark }}</p>
      </section>
        </div>

        <div class="col-right">
      <section class="block">
        <div class="block-head">
          <span class="block-title">做过的款</span>
          <span class="block-count mono">{{ styleUsage.length }}</span>
          <span class="block-line"></span>
        </div>
        <p v-if="!styleUsage.length" class="empty-line">还没有做过的款。</p>
        <div v-else class="chips">
          <span v-for="item in styleUsage" :key="item.styleName" class="chip">
            {{ item.styleName }} <em class="mono">×{{ item.times }}</em>
          </span>
        </div>
      </section>

      <section class="block">
        <div class="block-head">
          <span class="block-title">历史预约</span>
          <span class="block-count mono">{{ bookings.length }}</span>
          <span class="block-line"></span>
        </div>

        <p v-if="!bookings.length" class="empty-line">还没有预约记录。</p>

        <ul v-else class="history">
          <li v-for="booking in bookings" :key="booking.id">
            <button type="button" class="history-row" @click="openBooking(booking)">
              <span class="history-date mono">{{ bookingDate(booking) }}</span>
              <span class="history-time mono">{{ timeText(booking.startAt) }}</span>
              <span class="history-main">
                <span class="history-styles">{{ bookingStyles(booking) }}</span>
                <span class="history-price mono">{{ formatMoney(bookingTotal(booking), { sign: true }) }}</span>
              </span>
              <span class="history-status" :class="booking.status">{{ STATUS_LABEL[booking.status] || booking.status }}</span>
            </button>
          </li>
        </ul>
      </section>
        </div>
      </div>
    </template>

    <!-- 编辑 -->
    <SoloSheet
      :visible="editVisible"
      title="编辑客户"
      note="短号不会变，其它随时改"
      @update:visible="editVisible = $event"
    >
      <section class="field">
        <div class="field-head"><span class="field-label">昵称</span></div>
        <input v-model="form.nickname" class="input" type="text" />
      </section>
      <section class="field">
        <div class="field-head"><span class="field-label">手机后四位</span></div>
        <input v-model="form.phoneTail" class="input" type="text" maxlength="4" />
      </section>
      <section class="field">
        <div class="field-head"><span class="field-label">来源</span></div>
        <input v-model="form.source" class="input" type="text" />
      </section>
      <section class="field">
        <div class="field-head"><span class="field-label">常用色号</span></div>
        <input v-model="form.colorNotes" class="input" type="text" />
      </section>
      <section class="field">
        <div class="field-head"><span class="field-label">备注</span></div>
        <input v-model="form.remark" class="input" type="text" />
      </section>

      <template #footer>
        <button type="button" class="btn" @click="editVisible = false">取消</button>
        <button type="button" class="btn btn-primary wide" :disabled="submitting" @click="submitEdit">
          {{ submitting ? '保存中…' : '保存' }}
        </button>
      </template>
    </SoloSheet>

    <!-- 删除 -->
    <SoloSheet
      :visible="deleteVisible"
      title="删除客户"
      :note="client ? `${client.code} ${client.nickname}` : ''"
      @update:visible="deleteVisible = $event"
    >
      <p class="danger-note">
        删除后客户不再出现在列表和搜索里，但历史预约、定金与流水都会保留，账目不会断。
      </p>
      <template #footer>
        <button type="button" class="btn" @click="deleteVisible = false">再想想</button>
        <button type="button" class="btn btn-danger wide" :disabled="submitting" @click="confirmDelete">
          {{ submitting ? '删除中…' : '确认删除' }}
        </button>
      </template>
    </SoloSheet>
  </SoloLayout>
</template>

<style scoped>
.profile {
  margin-bottom: var(--s4);
}

.profile-main {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  min-width: 0;
}

.code {
  flex: none;
  padding: 1px 5px;
  border: 1px solid var(--line);
  color: var(--ink-3);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.name {
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 400;
  letter-spacing: 0.01em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge {
  flex: none;
  padding: 1px 5px;
  border: 1px solid var(--line-strong);
  color: var(--ink-4);
  font-size: 9px;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 10px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--s2);
  margin: var(--s3) 0 0;
  padding: var(--s2) 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.stat dt {
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.14em;
}

.stat dd {
  margin: 1px 0 0;
  font-size: 17px;
  line-height: 1.2;
}

.actions {
  display: flex;
  gap: var(--s2);
  margin-top: var(--s3);
}

.block {
  margin-bottom: var(--s5);
}

.block-head {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  margin-bottom: var(--s2);
}

.block-title {
  font-family: var(--font-display);
  font-size: 15px;
  letter-spacing: 0.04em;
}

.block-count {
  color: var(--lacquer);
  font-size: 11px;
}

.block-line {
  flex: 1 1 auto;
  height: 1px;
  background: var(--line);
}

.line {
  display: flex;
  gap: var(--s2);
  margin-bottom: 4px;
  color: var(--ink-2);
  font-size: 12px;
  line-height: 1.7;
}

.line-label {
  flex: none;
  width: 60px;
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s2);
}

.chip {
  padding: 2px 8px;
  border: 1px solid var(--line);
  color: var(--ink-2);
  font-size: 12px;
}

.chip em {
  color: var(--ink-4);
  font-size: 10px;
  font-style: normal;
}

.history {
  margin: 0;
  padding: 0;
  list-style: none;
}

.history-row {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  width: 100%;
  padding: 7px 0;
  border: 0;
  border-bottom: 1px solid var(--line-soft);
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  text-align: left;
}

.history-row:hover {
  background: var(--paper-raised);
}

.history-date {
  flex: none;
  width: 40px;
  color: var(--ink-3);
  font-size: 10px;
}

.history-time {
  flex: none;
  width: 34px;
  color: var(--ink-3);
  font-size: 10px;
}

.history-main {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  align-items: baseline;
  gap: var(--s2);
}

.history-styles {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-price {
  flex: none;
  margin-left: auto;
  color: var(--ink-4);
  font-size: 10px;
}

.history-status {
  flex: none;
  color: var(--ink-4);
  font-size: 9px;
}

.history-status.COMPLETED {
  color: var(--ink-4);
}

.history-status.BOOKED {
  color: var(--moss);
}

.history-status.IN_SERVICE {
  color: var(--lacquer);
}

.empty-line {
  padding: var(--s2) 0;
  color: var(--ink-3);
  font-size: 11px;
}

.danger-note {
  color: var(--ink-2);
  font-size: 12px;
  line-height: 1.8;
}

/* 表单 */

.field {
  margin-bottom: var(--s4);
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

.btn-primary {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
}

.btn-danger {
  border-color: var(--lacquer);
  background: var(--lacquer);
  color: var(--paper-raised);
}

.btn.danger {
  border-color: var(--line-strong);
  color: var(--lacquer-deep);
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.wide {
  flex: 1 1 auto;
}

/* 电脑：档案与历史并排 */
.detail-grid {
  display: block;
}

@media (min-width: 900px) {
  .detail-grid {
    display: grid;
    grid-template-columns: 340px minmax(0, 1fr);
    gap: var(--s6);
    align-items: start;
  }

  .col-left {
    position: sticky;
    top: 100px;
  }

  .name {
    font-size: 30px;
  }
}
</style>
