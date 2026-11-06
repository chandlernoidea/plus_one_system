<script setup>
/**
 * 财务（P6，二级入口）。
 *
 * 一页交代三件事：这个月赚了多少、明细对不对得上、每一笔能不能追回预约。
 * 三本账分开看：收入流水 / 支出流水 / 定金流水。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SoloLayout from './SoloLayout.vue'
import SoloSheet from '../../components/solo/SoloSheet.vue'
import * as api from '../../api/solo'
import { formatMoney, timeText } from '../../utils/date'

const router = useRouter()

const month = ref(currentMonth())
const summary = ref(null)
const incomes = ref([])
const expenses = ref([])
const deposits = ref([])
const loading = ref(false)
const tab = ref('income')

const recordVisible = ref(false)
const submitting = ref(false)
const form = ref(emptyForm())

const CATEGORY_LABEL = { SERVICE: '服务收入', MATERIAL: '物料支出', OTHER: '其他' }
const DEPOSIT_LABEL = { COLLECT: '收取', OFFSET: '抵扣', REFUND: '退还', FORFEIT: '没收' }

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function emptyForm() {
  return { direction: 'IN', category: 'SERVICE', amount: '', remark: '' }
}

function shiftMonth(delta) {
  const [year, m] = month.value.split('-').map(Number)
  const date = new Date(year, m - 1 + delta, 1)
  month.value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
  load()
}

async function load() {
  loading.value = true
  try {
    const [summaryRes, incomeRes, expenseRes, depositRes] = await Promise.all([
      api.fetchFinanceSummary(month.value),
      api.fetchFinanceFlows(month.value, 'IN'),
      api.fetchFinanceFlows(month.value, 'OUT'),
      api.fetchFinanceDeposits(month.value)
    ])
    summary.value = summaryRes.data.data
    incomes.value = incomeRes.data.data || []
    expenses.value = expenseRes.data.data || []
    deposits.value = depositRes.data.data || []
  } catch (error) {
    summary.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)

const monthLabel = computed(() => {
  const [year, m] = month.value.split('-')
  return `${year} 年 ${Number(m)} 月`
})

const netPositive = computed(() => Number(summary.value?.net || 0) >= 0)

const rows = computed(() => {
  if (tab.value === 'deposit') {
    return deposits.value.map((item) => ({
      key: `d-${item.id}`,
      time: item.happenedAt,
      label: `定金${DEPOSIT_LABEL[item.type] || item.type}`,
      client: item.clientCode ? `${item.clientCode} ${item.clientNickname}` : '—',
      note: item.reason || '',
      amount: item.amount,
      sign: ['COLLECT', 'FORFEIT'].includes(item.type) ? 1 : -1,
      date: item.bookingStartAt?.slice(0, 10) || ''
    }))
  }
  const source = tab.value === 'income' ? incomes.value : expenses.value
  return source.map((item) => ({
    key: `m-${item.id}`,
    time: item.happenedAt,
    label: CATEGORY_LABEL[item.category] || item.category,
    client: item.clientCode ? `${item.clientCode} ${item.clientNickname}` : '—',
    note: item.remark || '',
    amount: item.amount,
    sign: item.direction === 'IN' ? 1 : -1,
    date: item.bookingStartAt?.slice(0, 10) || ''
  }))
})

const tabCounts = computed(() => ({
  income: incomes.value.length,
  expense: expenses.value.length,
  deposit: deposits.value.length
}))

/** 今天的 YYYY-MM-DD，给「记一笔」的日期框做默认值 */
function todayKey() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 当天的 HH:mm:ss */
function nowClock() {
  const d = new Date()
  return [d.getHours(), d.getMinutes(), d.getSeconds()].map((n) => String(n).padStart(2, '0')).join(':')
}

function openRecord() {
  form.value = {
    direction: tab.value === 'expense' ? 'OUT' : 'IN',
    category: tab.value === 'expense' ? 'MATERIAL' : 'SERVICE',
    amount: '',
    remark: '',
    date: todayKey()
  }
  recordVisible.value = true
}

async function submitRecord() {
  const amount = Number(form.value.amount)
  if (!amount || amount <= 0) {
    ElMessage.warning('金额要大于 0')
    return
  }
  submitting.value = true
  try {
    await api.recordMoneyFlow({
      direction: form.value.direction,
      category: form.value.category,
      amount,
      // 允许补记：选哪天就记到哪天（钟点用当前时间，只影响同一天内的排序）
      happenedAt: `${form.value.date || todayKey()} ${nowClock()}`,
      remark: form.value.remark.trim() || undefined
    })
    ElMessage.success('已记账')
    recordVisible.value = false
    await load()
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    submitting.value = false
  }
}

function jumpToBooking(row) {
  if (!row.date) {
    return
  }
  router.push({ path: '/solo/today', query: { date: row.date } })
}
</script>

<template>
  <SoloLayout back-to="/solo/today" back-label="今天">
    <header class="lede">
      <span class="eyebrow">SOLO · LEDGER</span>
      <h1 class="lede-title">
        {{ monthLabel }} · 净收入
        <span class="net" :class="{ 'is-negative': !netPositive }">
          {{ formatMoney(summary?.net || 0, { sign: true }) }}
        </span>
      </h1>
      <p class="lede-note">收入 − 支出 = 净收入 · 定金单独一本账</p>
    </header>

    <div class="month-nav">
      <button type="button" class="nav-btn" @click="shiftMonth(-1)">‹ 上月</button>
      <button type="button" class="nav-btn" @click="shiftMonth(1)">下月 ›</button>
      <span v-if="loading" class="nav-loading mono">读取中…</span>
    </div>

    <dl class="overview">
      <div class="cell">
        <dt>收入</dt>
        <dd class="num">{{ formatMoney(summary?.income || 0) }}</dd>
      </div>
      <div class="cell">
        <dt>支出</dt>
        <dd class="num">{{ formatMoney(summary?.expense || 0) }}</dd>
      </div>
      <div class="cell">
        <dt>净收入</dt>
        <dd class="num">{{ formatMoney(summary?.net || 0) }}</dd>
      </div>
      <div class="cell">
        <dt>定金没收</dt>
        <dd class="num">{{ formatMoney(summary?.depositForfeited || 0) }}</dd>
      </div>
    </dl>

    <p class="reconcile" :class="{ 'is-bad': !summary?.balanced }">
      <template v-if="summary?.balanced">✓ 明细求和与汇总一致</template>
      <template v-else>
        ⚠ 汇总与明细不一致：收入差 {{ formatMoney(summary?.incomeDiff || 0) }}，
        支出差 {{ formatMoney(summary?.expenseDiff || 0) }}
      </template>
    </p>

    <div class="tabs">
      <button type="button" class="tab" :class="{ 'is-active': tab === 'income' }" @click="tab = 'income'">
        收入 <span class="mono">{{ tabCounts.income }}</span>
      </button>
      <button type="button" class="tab" :class="{ 'is-active': tab === 'expense' }" @click="tab = 'expense'">
        支出 <span class="mono">{{ tabCounts.expense }}</span>
      </button>
      <button type="button" class="tab" :class="{ 'is-active': tab === 'deposit' }" @click="tab = 'deposit'">
        定金 <span class="mono">{{ tabCounts.deposit }}</span>
      </button>
      <button v-if="tab !== 'deposit'" type="button" class="record-btn" @click="openRecord">＋ 记一笔</button>
    </div>

    <p v-if="!rows.length" class="empty-line">这个月还没有{{ tab === 'deposit' ? '定金' : tab === 'income' ? '收入' : '支出' }}记录。</p>

    <ul v-else class="flows">
      <li v-for="row in rows" :key="row.key">
        <button type="button" class="flow" :class="{ 'is-out': row.sign < 0 }" @click="jumpToBooking(row)">
          <span class="flow-date mono">{{ row.time?.slice(5, 10).replace('-', '/') }}</span>
          <span class="flow-main">
            <span class="flow-label">{{ row.label }}</span>
            <span class="flow-meta">
              <span>{{ row.client }}</span>
              <span v-if="row.note"> · {{ row.note }}</span>
            </span>
          </span>
          <span class="flow-amount mono">{{ row.sign > 0 ? '+' : '−' }}{{ formatMoney(row.amount) }}</span>
        </button>
      </li>
    </ul>

    <SoloSheet
      :visible="recordVisible"
      title="记一笔"
      note="尾款收入、买料支出都记这里"
      @update:visible="recordVisible = $event"
    >
      <section class="field">
        <div class="field-head"><span class="field-label">方向</span></div>
        <div class="choices">
          <button type="button" class="choice" :class="{ 'is-active': form.direction === 'IN' }" @click="form.direction = 'IN'; form.category = 'SERVICE'">收入</button>
          <button type="button" class="choice" :class="{ 'is-active': form.direction === 'OUT' }" @click="form.direction = 'OUT'; form.category = 'MATERIAL'">支出</button>
        </div>
      </section>

      <section class="field">
        <div class="field-head"><span class="field-label">类别</span></div>
        <div class="choices">
          <button
            v-for="option in form.direction === 'IN' ? [['SERVICE', '服务收入'], ['OTHER', '其他']] : [['MATERIAL', '物料支出'], ['OTHER', '其他']]"
            :key="option[0]"
            type="button"
            class="choice"
            :class="{ 'is-active': form.category === option[0] }"
            @click="form.category = option[0]"
          >
            {{ option[1] }}
          </button>
        </div>
      </section>

      <section class="field">
        <div class="field-head"><span class="field-label">金额</span><span class="field-hint">元</span></div>
        <input v-model="form.amount" class="input" type="number" min="0" step="10" placeholder="168" />
      </section>

      <section class="field">
        <div class="field-head"><span class="field-label">日期</span><span class="field-hint">补记以前的就选那天</span></div>
        <input v-model="form.date" class="input" type="date" />
      </section>

      <section class="field">
        <div class="field-head"><span class="field-label">备注</span></div>
        <input v-model="form.remark" class="input" type="text" placeholder="尾款 / 补两瓶胶" />
      </section>

      <template #footer>
        <button type="button" class="btn" @click="recordVisible = false">取消</button>
        <button type="button" class="btn btn-primary wide" :disabled="submitting" @click="submitRecord">
          {{ submitting ? '保存中…' : '保存' }}
        </button>
      </template>
    </SoloSheet>
  </SoloLayout>
</template>

<style scoped>
.lede {
  margin-bottom: var(--s3);
}

.lede-title {
  margin-top: 2px;
  font-family: var(--font-display);
  font-size: 23px;
  font-weight: 400;
  letter-spacing: 0.01em;
  line-height: 1.35;
}

.net {
  color: var(--moss);
}

.net.is-negative {
  color: var(--lacquer);
}

.lede-note {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 11px;
}

.month-nav {
  display: flex;
  align-items: center;
  gap: var(--s2);
  margin-bottom: var(--s3);
}

.nav-btn {
  padding: 3px 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
}

.nav-btn:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.nav-loading {
  margin-left: auto;
  color: var(--ink-4);
  font-size: 10px;
}

.overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--s2);
  margin: 0 0 var(--s2);
  padding: var(--s2) 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.cell dt {
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.cell dd {
  margin: 2px 0 0;
  font-size: 16px;
  line-height: 1.2;
}

.reconcile {
  margin-bottom: var(--s3);
  padding: 4px 8px;
  border-left: 2px solid var(--moss);
  background: rgba(62, 89, 68, 0.05);
  color: var(--moss);
  font-size: 11px;
}

.reconcile.is-bad {
  border-left-color: var(--lacquer);
  background: var(--lacquer-wash);
  color: var(--lacquer-deep);
}

.tabs {
  display: flex;
  align-items: center;
  gap: var(--s2);
  margin-bottom: var(--s2);
  border-bottom: 1px solid var(--line);
}

.tab {
  padding: 6px 2px;
  border: 0;
  border-bottom: 1.5px solid transparent;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
}

.tab span {
  color: var(--ink-4);
  font-size: 9px;
}

.tab.is-active {
  border-bottom-color: var(--lacquer);
  color: var(--ink);
}

.record-btn {
  margin-left: auto;
  margin-bottom: 4px;
  padding: 4px 10px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  white-space: nowrap;
}

.flows {
  margin: 0;
  padding: 0;
  list-style: none;
}

.flow {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  width: 100%;
  padding: 8px 0;
  border: 0;
  border-bottom: 1px solid var(--line-soft);
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  text-align: left;
}

.flow:hover {
  background: var(--paper-raised);
}

.flow-date {
  flex: none;
  width: 40px;
  color: var(--ink-4);
  font-size: 10px;
}

.flow-main {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 1px;
}

.flow-label {
  font-size: 12px;
}

.flow-meta {
  overflow: hidden;
  color: var(--ink-4);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-amount {
  flex: none;
  color: var(--moss);
  font-size: 13px;
}

.flow.is-out .flow-amount {
  color: var(--lacquer);
}

.empty-line {
  padding: var(--s3) 0;
  color: var(--ink-3);
  font-size: 11px;
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

.field-hint {
  color: var(--ink-4);
  font-size: 10px;
}

.choices {
  display: flex;
  gap: var(--s2);
}

.choice {
  padding: 5px 14px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
}

.choice.is-active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
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

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.wide {
  flex: 1 1 auto;
}

@media (min-width: 900px) {
  .lede-title {
    font-size: 26px;
  }

  .cell dd {
    font-size: 20px;
  }

  .flow {
    padding: 10px 0;
  }
}
</style>
