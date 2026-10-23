<script setup>
import { computed, onMounted, ref } from 'vue'
import { listUpcomingPreps } from '../../api/prep'

const preps = ref([])
const loading = ref(false)
const days = ref(7)

const rangeOptions = [
  { label: '今天', value: 1 },
  { label: '未来 7 天', value: 7 },
  { label: '未来 30 天', value: 30 }
]

const statusConfig = {
  PENDING: { label: '待准备', type: 'warning' },
  READY: { label: '已就绪', type: 'success' }
}

const rangeLabel = computed(
  () => rangeOptions.find((option) => option.value === days.value)?.label || `未来 ${days.value} 天`
)

const heading = computed(() =>
  days.value === 1 ? '今日物料准备' : `${rangeLabel.value}物料准备`
)

const subtitle = computed(() =>
  days.value === 1
    ? '按今天已确认预约汇总款式、效果图和物料信息'
    : `按${rangeLabel.value}（含今天）已确认预约汇总款式、效果图和物料信息`
)

const readyCount = computed(() => preps.value.filter((item) => item.prepStatus === 'READY').length)

function statusInfo(status) {
  return statusConfig[status] || { label: status || '未知', type: 'info' }
}

/** 物料清单是一段自由文本，表格里给一句话摘要，完整内容仍在展开行 */
function materialSummary(text) {
  if (!text) {
    return '未填写'
  }
  const compact = String(text)
    .replace(/[{}"[\]]/g, ' ')
    .replace(/[,:，：]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return compact.length > 24 ? `${compact.slice(0, 24)}…` : compact
}

function updatedText(value) {
  if (!value) {
    return '—'
  }
  return String(value).slice(5, 16)
}

async function loadPreps() {
  loading.value = true
  try {
    const response = await listUpcomingPreps(days.value)
    preps.value = response.data.data || []
  } catch (error) {
    preps.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadPreps)
</script>

<template>
  <section class="prep-section">
    <div class="section-header">
      <div>
        <h2>{{ heading }}</h2>
        <p>{{ subtitle }}</p>
      </div>
      <div class="header-actions">
        <span class="list-count mono">{{ preps.length }} 条 · 就绪 {{ readyCount }}</span>
        <el-select v-model="days" style="width: 130px" @change="loadPreps">
          <el-option
            v-for="option in rangeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button @click="loadPreps">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="preps" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <div class="expand-title">物料清单</div>
              <pre class="material-json">{{ row.materialJson || '暂无物料清单' }}</pre>
              <div class="expand-title">美甲师备注</div>
              <div class="note-text">{{ row.staffNote || '暂无备注' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="appointmentTime" label="预约时间" width="170" />
        <el-table-column prop="customerName" label="顾客" width="100" />
        <el-table-column prop="staffName" label="美甲师" width="110" />
        <el-table-column prop="serviceTypeName" label="项目类型" min-width="110" />
        <el-table-column
          prop="styleDescription"
          label="款式说明"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column label="物料" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'empty-text': !row.materialJson }">
              {{ materialSummary(row.materialJson) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="效果图" width="86">
          <template #default="{ row }">
            <el-image
              v-if="row.effectImageUrl"
              :src="row.effectImageUrl"
              :preview-src-list="[row.effectImageUrl]"
              fit="cover"
              class="prep-thumb"
            />
            <span v-else class="empty-text">暂无</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="116">
          <template #default="{ row }">
            <span class="mono updated-at">{{ updatedText(row.updatedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工单状态" width="96">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.prepStatus).type">
              {{ statusInfo(row.prepStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!loading && preps.length === 0"
        :description="days === 1 ? '今天暂无已确认预约的物料工单' : `${rangeLabel}内暂无已确认预约的物料工单`"
      />
    </el-card>
  </section>
</template>

<style scoped>
.prep-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.list-count {
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.12em;
  white-space: nowrap;
}

.prep-thumb {
  width: 48px;
  height: 48px;
}

.empty-text {
  color: var(--ink-4);
  font-size: 12px;
}

.updated-at {
  color: var(--ink-3);
  font-size: 11px;
}

.expand-content {
  padding: 8px 24px 16px;
  color: var(--ink-2);
  font-size: 13px;
}

.expand-title {
  margin: 8px 0 6px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 500;
}

.material-json {
  margin: 0;
  padding: 12px;
  max-height: 220px;
  overflow: auto;
  border: 1px solid var(--line);
  background: var(--paper);
  color: var(--ink-2);
  font-family: var(--font-mono);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.note-text {
  white-space: pre-wrap;
}

@media (max-width: 960px) {
  .section-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
