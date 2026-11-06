<script setup>
/**
 * 物料（P6，二级入口）：今天要买的料 + 库存。
 *
 * 「今天要买的料」不是拍脑袋的清单：把当天预约的款式材料清单按名字聚合，
 * 再和库存对一遍，缺口才算要买。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SoloLayout from './SoloLayout.vue'
import SoloSheet from '../../components/solo/SoloSheet.vue'
import * as api from '../../api/solo'
import { formatMoney, toKey } from '../../utils/date'

const needs = ref([])
const materials = ref([])
const loading = ref(false)
const activeStatus = ref('')

const editorVisible = ref(false)
const editing = ref(null)
const submitting = ref(false)
const form = ref(emptyForm())

const STATUS = [
  ['TODO', '待采购'],
  ['ORDERED', '已下单'],
  ['SHIPPING', '在途'],
  ['ARRIVED', '已到货'],
  ['READY', '已备好']
]
const STATUS_LABEL = Object.fromEntries(STATUS)

function emptyForm() {
  return {
    name: '',
    unit: '个',
    unitCost: 0,
    quantity: 0,
    sponsored: 0,
    sponsorName: '',
    sponsorValue: '',
    purchaseStatus: 'TODO',
    remark: ''
  }
}

async function load() {
  loading.value = true
  try {
    const [needRes, materialRes] = await Promise.all([
      api.fetchMaterialTodo(toKey(new Date())),
      api.fetchMaterials({})
    ])
    needs.value = needRes.data.data || []
    materials.value = materialRes.data.data || []
  } catch (error) {
    needs.value = []
    materials.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

const pendingNeeds = computed(() => needs.value.filter((item) => !item.ready))
const visibleMaterials = computed(() =>
  activeStatus.value
    ? materials.value.filter((material) => material.purchaseStatus === activeStatus.value)
    : materials.value
)

const statusCounts = computed(() => {
  const counts = {}
  materials.value.forEach((material) => {
    counts[material.purchaseStatus] = (counts[material.purchaseStatus] || 0) + 1
  })
  return counts
})

function openCreate() {
  editing.value = null
  form.value = emptyForm()
  editorVisible.value = true
}

function openEdit(material) {
  editing.value = material
  form.value = {
    name: material.name,
    unit: material.unit,
    unitCost: material.unitCost,
    quantity: material.quantity,
    sponsored: material.sponsored,
    sponsorName: material.sponsorName || '',
    sponsorValue: material.sponsorValue ?? '',
    purchaseStatus: material.purchaseStatus,
    remark: material.remark || ''
  }
  editorVisible.value = true
}

async function submit() {
  if (!form.value.name.trim()) {
    ElMessage.warning('物料名称不能为空')
    return
  }
  submitting.value = true
  const payload = {
    name: form.value.name.trim(),
    unit: form.value.unit.trim() || '个',
    unitCost: Number(form.value.unitCost) || 0,
    quantity: Number(form.value.quantity) || 0,
    sponsored: Number(form.value.sponsored) || 0,
    sponsorName: form.value.sponsorName.trim() || undefined,
    sponsorValue: form.value.sponsorValue === '' ? undefined : Number(form.value.sponsorValue),
    purchaseStatus: form.value.purchaseStatus,
    remark: form.value.remark.trim() || undefined
  }
  try {
    if (editing.value) {
      await api.updateMaterial(editing.value.id, payload)
      ElMessage.success('已更新')
    } else {
      await api.createMaterial(payload)
      ElMessage.success('已入库')
    }
    editorVisible.value = false
    await load()
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    submitting.value = false
  }
}

async function quickStatus(material, status) {
  try {
    await api.updateMaterialStatus(material.id, status)
    await load()
  } catch (error) {
    // 提示已由拦截器统一给出
  }
}

function stockText(material) {
  const qty = Number(material.quantity || 0)
  const text = qty % 1 === 0 ? String(qty) : qty.toFixed(2)
  return `${text} ${material.unit}`
}
</script>

<template>
  <SoloLayout back-to="/solo/today" back-label="今天">
    <header class="lede">
      <span class="eyebrow">SOLO · MATERIALS</span>
      <h1 class="lede-title">
        {{ pendingNeeds.length ? `今天还要备 ${pendingNeeds.length} 样料` : '今天的料都齐了' }}
      </h1>
      <p class="lede-note">按当天预约的款式清单算 · 库存 {{ materials.length }} 样</p>
    </header>

    <!-- 今天要买的料 -->
    <section class="block">
      <div class="block-head">
        <span class="block-title">今天要买的料</span>
        <span class="block-count mono">{{ pendingNeeds.length }}</span>
        <span class="block-line"></span>
      </div>

      <p v-if="loading" class="empty-line">读取中…</p>
      <p v-else-if="!needs.length" class="empty-line">
        今天的款式没登记材料清单。
        <em>在「款式库」里给款式填上材料，这里就会自动汇总。</em>
      </p>

      <ul v-else class="needs">
        <li v-for="need in needs" :key="need.name" class="need" :class="{ 'is-ready': need.ready }">
          <span class="need-name">{{ need.name }}</span>
          <span class="need-calc mono">
            要 {{ need.needQty }} · 存 {{ need.stockQty }}
            <template v-if="need.shortfall > 0"> · 缺 <em>{{ need.shortfall }}</em></template>
          </span>
          <span class="need-flag">{{ need.ready ? '齐了' : need.purchaseStatus ? STATUS_LABEL[need.purchaseStatus] : '没建库存' }}</span>
        </li>
      </ul>
    </section>

    <!-- 库存 -->
    <section class="block">
      <div class="block-head">
        <span class="block-title">库存</span>
        <span class="block-line"></span>
        <button type="button" class="create-btn" @click="openCreate">＋ 新建</button>
      </div>

      <div class="filters">
        <button type="button" class="tag" :class="{ 'is-active': !activeStatus }" @click="activeStatus = ''">
          全部 <span class="mono">{{ materials.length }}</span>
        </button>
        <button
          v-for="item in STATUS"
          :key="item[0]"
          type="button"
          class="tag"
          :class="{ 'is-active': activeStatus === item[0] }"
          @click="activeStatus = activeStatus === item[0] ? '' : item[0]"
        >
          {{ item[1] }} <span class="mono">{{ statusCounts[item[0]] || 0 }}</span>
        </button>
      </div>

      <p v-if="!visibleMaterials.length" class="empty-line">这里还没有物料。</p>

      <div v-else class="materials">
        <button
          v-for="material in visibleMaterials"
          :key="material.id"
          type="button"
          class="material"
          @click="openEdit(material)"
        >
          <span class="material-row">
            <span class="material-name">{{ material.name }}</span>
            <span class="material-stock mono">{{ stockText(material) }}</span>
          </span>
          <span class="material-meta">
            <span>{{ STATUS_LABEL[material.purchaseStatus] || material.purchaseStatus }}</span>
            <span v-if="material.sponsored === 1" class="sponsored">赞助 · {{ material.sponsorName || '—' }}</span>
            <span v-else class="mono">成本 {{ formatMoney(material.unitCost, { sign: true }) }}/{{ material.unit }}</span>
          </span>
        </button>
      </div>
    </section>

    <SoloSheet
      :visible="editorVisible"
      :title="editing ? '编辑物料' : '新建物料'"
      note="赞助物料成本记 0，另记赞助商与价值"
      @update:visible="editorVisible = $event"
    >
      <section class="field">
        <div class="field-head"><span class="field-label">名称</span></div>
        <input v-model="form.name" class="input" type="text" placeholder="裸色甲油胶 #E03" />
      </section>

      <section class="field row">
        <div class="half">
          <div class="field-head"><span class="field-label">库存</span></div>
          <input v-model.number="form.quantity" class="input" type="number" min="0" step="1" />
        </div>
        <div class="half">
          <div class="field-head"><span class="field-label">单位</span></div>
          <input v-model="form.unit" class="input" type="text" placeholder="瓶 / 包 / 个" />
        </div>
      </section>

      <section class="field row">
        <div class="half">
          <div class="field-head"><span class="field-label">单位成本</span><span class="field-hint">元</span></div>
          <input v-model.number="form.unitCost" class="input" type="number" min="0" step="1" />
        </div>
        <div class="half">
          <div class="field-head"><span class="field-label">是否赞助</span></div>
          <div class="choices">
            <button type="button" class="choice" :class="{ 'is-active': form.sponsored === 0 }" @click="form.sponsored = 0">自购</button>
            <button type="button" class="choice" :class="{ 'is-active': form.sponsored === 1 }" @click="form.sponsored = 1">赞助</button>
          </div>
        </div>
      </section>

      <section v-if="form.sponsored === 1" class="field">
        <div class="field-head"><span class="field-label">赞助商 / 赞助价值</span></div>
        <div class="row">
          <input v-model="form.sponsorName" class="input" type="text" placeholder="甲油品牌A" />
          <input v-model="form.sponsorValue" class="input" type="number" min="0" step="10" placeholder="200" />
        </div>
      </section>

      <section class="field">
        <div class="field-head"><span class="field-label">采购状态</span></div>
        <div class="choices wrap">
          <button
            v-for="item in STATUS"
            :key="item[0]"
            type="button"
            class="choice"
            :class="{ 'is-active': form.purchaseStatus === item[0] }"
            @click="form.purchaseStatus = item[0]"
          >
            {{ item[1] }}
          </button>
        </div>
      </section>

      <section class="field">
        <div class="field-head"><span class="field-label">备注</span></div>
        <input v-model="form.remark" class="input" type="text" placeholder="在哪买、型号…" />
      </section>

      <template #footer>
        <button type="button" class="btn" @click="editorVisible = false">取消</button>
        <button type="button" class="btn btn-primary wide" :disabled="submitting" @click="submit">
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
  line-height: 1.3;
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

.block-count {
  color: var(--lacquer);
  font-size: 11px;
}

.block-line {
  flex: 1 1 auto;
  height: 1px;
  background: var(--line);
}

.create-btn {
  flex: none;
  padding: 4px 10px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  white-space: nowrap;
}

.needs {
  margin: 0;
  padding: 0;
  list-style: none;
}

.need {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  padding: 7px 0;
  border-bottom: 1px solid var(--line-soft);
  font-size: 12px;
}

.need.is-ready {
  color: var(--ink-4);
}

.need-name {
  overflow: hidden;
  flex: 1 1 auto;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.need-calc {
  flex: none;
  color: var(--ink-3);
  font-size: 10px;
}

.need-calc em {
  color: var(--lacquer);
  font-style: normal;
}

.need-flag {
  flex: none;
  color: var(--ochre);
  font-size: 10px;
}

.need.is-ready .need-flag {
  color: var(--moss);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: var(--s2);
}

.tag {
  padding: 2px 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
}

.tag span {
  color: var(--ink-4);
  font-size: 9px;
}

.tag.is-active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
}

.tag.is-active span {
  color: var(--paper-sunken);
}

.materials {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--s2);
}

.material {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: var(--s3);
  border: 1px solid var(--line);
  border-left: 2px solid var(--line-strong);
  background: var(--paper-raised);
  color: var(--ink);
  cursor: pointer;
  text-align: left;
}

.material:hover {
  border-left-color: var(--lacquer);
}

.material-row {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  min-width: 0;
}

.material-name {
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-stock {
  flex: none;
  margin-left: auto;
  color: var(--ink-2);
  font-size: 12px;
}

.material-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--ink-3);
  font-size: 10px;
}

.sponsored {
  color: var(--ochre);
}

.empty-line {
  padding: var(--s2) 0;
  color: var(--ink-3);
  font-size: 11px;
}

.empty-line em {
  display: block;
  margin-top: 2px;
  color: var(--ink-4);
  font-size: 10px;
  font-style: normal;
}

/* 表单 */

.field {
  margin-bottom: var(--s4);
}

.field.row {
  display: flex;
  gap: var(--s4);
}

.half {
  flex: 1 1 0;
  min-width: 0;
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

.row .input {
  flex: 1 1 0;
}

.choices {
  display: flex;
  gap: var(--s2);
}

.choices.wrap {
  flex-wrap: wrap;
}

.choice {
  padding: 4px 12px;
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
  .materials {
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: var(--s3);
  }
}
</style>
