<script setup>
/**
 * 设置（P7，二级入口）。
 *
 * 四项数字直接决定排期与定金行为；导出与演示数据也放这里，
 * 都是「装机器/搬数据」时才会点的低频操作。
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SoloLayout from './SoloLayout.vue'
import * as api from '../../api/solo'

const form = ref({
  openTime: '08:30',
  closeTime: '22:30',
  bufferMinutes: 20,
  depositAmount: 30,
  freeRescheduleHours: 48
})
const loading = ref(false)
const saving = ref(false)
const exporting = ref('')
const seeding = ref(false)
const demoResult = ref(null)

async function load() {
  loading.value = true
  try {
    const response = await api.fetchSettings()
    form.value = response.data.data
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function save() {
  saving.value = true
  try {
    const response = await api.saveSettings({
      openTime: form.value.openTime,
      closeTime: form.value.closeTime,
      bufferMinutes: Number(form.value.bufferMinutes),
      depositAmount: Number(form.value.depositAmount),
      freeRescheduleHours: Number(form.value.freeRescheduleHours)
    })
    form.value = response.data.data
    ElMessage.success('已保存，之后的建单和排期都按新设置走')
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    saving.value = false
  }
}

async function exportCsv(kind, filename) {
  exporting.value = kind
  try {
    const response = await api.downloadCsv(kind)
    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success(`${filename} 已下载`)
  } catch (error) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = ''
  }
}

async function seedDemo() {
  seeding.value = true
  demoResult.value = null
  try {
    const response = await api.seedDemoData()
    demoResult.value = response.data.data
    ElMessage.success(demoResult.value.created ? '演示数据已生成' : '库里已有演示数据')
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    seeding.value = false
  }
}
</script>

<template>
  <SoloLayout back-to="/solo/today" back-label="今天">
    <header class="lede">
      <span class="eyebrow">SOLO · SETTINGS</span>
      <h1 class="lede-title">设置</h1>
      <p class="lede-note">改完立刻生效 · 只影响之后的建单与排期</p>
    </header>

    <section class="block">
      <div class="block-head">
        <span class="block-title">营业与规则</span>
        <span class="block-line"></span>
      </div>

      <div class="form" :class="{ 'is-loading': loading }">
        <div class="row">
          <label class="cell">
            <span class="cell-label">开门</span>
            <input v-model="form.openTime" class="input" type="time" step="300" />
          </label>
          <label class="cell">
            <span class="cell-label">打烊</span>
            <input v-model="form.closeTime" class="input" type="time" step="300" />
          </label>
        </div>
        <p class="cell-hint">营业时间模板：当天没有单独排营业段时，作业单按这个区间画时间轴。</p>

        <div class="row">
          <label class="cell">
            <span class="cell-label">默认缓冲</span>
            <span class="cell-input">
              <input v-model.number="form.bufferMinutes" class="input" type="number" min="0" max="120" step="5" />
              <em>分钟</em>
            </span>
          </label>
          <label class="cell">
            <span class="cell-label">定金</span>
            <span class="cell-input">
              <input v-model.number="form.depositAmount" class="input" type="number" min="0" max="1000" step="10" />
              <em>元</em>
            </span>
          </label>
          <label class="cell">
            <span class="cell-label">免费改期阈值</span>
            <span class="cell-input">
              <input v-model.number="form.freeRescheduleHours" class="input" type="number" min="1" max="168" step="1" />
              <em>小时</em>
            </span>
          </label>
        </div>
        <p class="cell-hint">
          建单默认带出的缓冲；收定金按这个金额；距开始 ≥ 阈值且没用过免费改期时，改期免费。
        </p>

        <button type="button" class="btn btn-primary" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存设置' }}
        </button>
      </div>
    </section>

    <section class="block">
      <div class="block-head">
        <span class="block-title">导出</span>
        <span class="block-line"></span>
      </div>
      <p class="cell-hint">CSV 带 BOM，Excel 双击打开不乱码。</p>
      <div class="actions">
        <button type="button" class="btn" :disabled="exporting === 'clients'" @click="exportCsv('clients', 'solo-clients.csv')">
          {{ exporting === 'clients' ? '导出中…' : '客户 CSV' }}
        </button>
        <button type="button" class="btn" :disabled="exporting === 'bookings'" @click="exportCsv('bookings', 'solo-bookings.csv')">
          {{ exporting === 'bookings' ? '导出中…' : '预约 CSV' }}
        </button>
        <button type="button" class="btn" :disabled="exporting === 'flows'" @click="exportCsv('flows', 'solo-flows.csv')">
          {{ exporting === 'flows' ? '导出中…' : '流水 CSV' }}
        </button>
      </div>
    </section>

    <section class="block">
      <div class="block-head">
        <span class="block-title">演示数据</span>
        <span class="block-line"></span>
      </div>
      <p class="cell-hint">3 位客户、5 个款式、今天 4 单（含 1 单连做，并留出空档）。没有演示数据时才创建。</p>
      <button type="button" class="btn" :disabled="seeding" @click="seedDemo">
        {{ seeding ? '生成中…' : '一键生成演示数据' }}
      </button>

      <ul v-if="demoResult" class="demo-list">
        <li v-for="note in demoResult.notes" :key="note">{{ note }}</li>
      </ul>
    </section>
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
  line-height: 1.25;
}

.lede-note {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 11px;
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

.block-line {
  flex: 1 1 auto;
  height: 1px;
  background: var(--line);
}

.form.is-loading {
  opacity: 0.6;
}

.row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s4);
  margin-bottom: var(--s2);
}

.cell {
  display: flex;
  flex: 1 1 120px;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.cell-label {
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.1em;
}

.cell-input {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.cell-input em {
  color: var(--ink-4);
  font-size: 10px;
  font-style: normal;
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
  font-size: 15px;
  outline: none;
}

.input:focus {
  border-bottom-color: var(--ink);
}

.cell-hint {
  margin-bottom: var(--s3);
  color: var(--ink-4);
  font-size: 10px;
  line-height: 1.7;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s2);
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

.demo-list {
  margin: var(--s3) 0 0;
  padding-left: var(--s4);
  color: var(--ink-3);
  font-size: 11px;
  line-height: 1.9;
}

@media (min-width: 900px) {
  .block {
    max-width: 760px;
  }
}
</style>
