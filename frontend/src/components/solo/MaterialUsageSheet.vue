<script setup>
/**
 * 按单领用抽屉：这单用了哪些料、还剩多少库存。
 *
 * 领用会当场扣库存并写成本快照（赞助物料成本记 0），所以这里不用再算钱。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import SoloSheet from './SoloSheet.vue'
import * as api from '../../api/solo'
import { formatMoney } from '../../utils/date'

const props = defineProps({
  visible: { type: Boolean, default: false },
  booking: { type: Object, default: null }
})

const emit = defineEmits(['update:visible', 'changed'])

const usages = ref([])
const materials = ref([])
const materialId = ref('')
const quantity = ref(1)
const submitting = ref(false)
const loading = ref(false)

const totalCost = computed(() =>
  usages.value.reduce((sum, item) => sum + Number(item.costSnapshot || 0), 0)
)

const selected = computed(() => materials.value.find((item) => item.id === Number(materialId.value)))

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) {
      return
    }
    materialId.value = ''
    quantity.value = 1
    await loadAll()
  }
)

async function loadAll() {
  loading.value = true
  try {
    const [usageRes, materialRes] = await Promise.all([
      props.booking ? api.fetchBookingMaterials(props.booking.id) : Promise.resolve({ data: { data: [] } }),
      api.fetchMaterials({})
    ])
    usages.value = usageRes.data.data || []
    materials.value = materialRes.data.data || []
  } catch (error) {
    usages.value = []
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!materialId.value) {
    ElMessage.warning('先选一样料')
    return
  }
  const qty = Number(quantity.value)
  if (!qty || qty <= 0) {
    ElMessage.warning('数量要大于 0')
    return
  }
  submitting.value = true
  try {
    await api.addBookingMaterial(props.booking.id, {
      materialId: Number(materialId.value),
      quantity: qty
    })
    ElMessage.success('已领用，库存已扣')
    materialId.value = ''
    quantity.value = 1
    await loadAll()
    emit('changed')
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <SoloSheet
    :visible="visible"
    title="按单领料"
    :note="booking ? `${booking.clientCode} ${booking.clientNickname} · ${booking.startAt?.slice(11, 16)}` : ''"
    @update:visible="emit('update:visible', $event)"
  >
    <p v-if="loading" class="empty-line">读取中…</p>

    <template v-else>
      <section class="field">
        <div class="field-head">
          <span class="field-label">已领</span>
          <span class="field-hint mono">{{ usages.length }} 项 · 成本 {{ formatMoney(totalCost, { sign: true }) }}</span>
        </div>
        <p v-if="!usages.length" class="empty-line">这单还没领料。</p>
        <ul v-else class="usage-list">
          <li v-for="usage in usages" :key="usage.id" class="usage">
            <span class="usage-name">{{ usage.materialName }}</span>
            <span class="usage-qty mono">×{{ usage.quantity }}</span>
            <span class="usage-cost mono">{{ formatMoney(usage.costSnapshot, { sign: true }) }}</span>
          </li>
        </ul>
      </section>

      <section class="field">
        <div class="field-head"><span class="field-label">再领一样</span></div>
        <select v-model="materialId" class="input">
          <option value="">选物料…</option>
          <option v-for="material in materials" :key="material.id" :value="material.id">
            {{ material.name }}（库存 {{ material.quantity }} {{ material.unit }}）
          </option>
        </select>
        <p v-if="selected" class="hint mono">
          成本 {{ formatMoney(selected.unitCost, { sign: true }) }} / {{ selected.unit }}
          <template v-if="selected.sponsored === 1">· 赞助物料，记账成本 0</template>
        </p>
        <div class="qty-row">
          <span class="field-hint">数量</span>
          <input v-model.number="quantity" class="input qty" type="number" min="0.01" step="1" />
          <button type="button" class="btn btn-primary" :disabled="submitting" @click="submit">
            {{ submitting ? '记账中…' : '领用' }}
          </button>
        </div>
      </section>
    </template>

    <template #footer>
      <button type="button" class="btn wide" @click="emit('update:visible', false)">关闭</button>
    </template>
  </SoloSheet>
</template>

<style scoped>
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

.usage-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.usage {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  padding: 6px 0;
  border-bottom: 1px solid var(--line-soft);
  font-size: 12px;
}

.usage-name {
  overflow: hidden;
  flex: 1 1 auto;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.usage-qty {
  flex: none;
  color: var(--ink-3);
  font-size: 11px;
}

.usage-cost {
  flex: none;
  width: 64px;
  color: var(--ink-3);
  font-size: 11px;
  text-align: right;
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

select.input {
  padding: 8px 0;
}

.hint {
  margin-top: 4px;
  color: var(--ink-4);
  font-size: 10px;
}

.qty-row {
  display: flex;
  align-items: center;
  gap: var(--s2);
  margin-top: var(--s3);
}

.qty {
  width: 72px;
  border: 1px solid var(--line);
  padding: 4px 6px;
  text-align: center;
}

.empty-line {
  padding: var(--s2) 0;
  color: var(--ink-3);
  font-size: 11px;
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
  margin-left: auto;
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
</style>
