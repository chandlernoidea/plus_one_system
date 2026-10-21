<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  generateSettlements,
  listSettlements,
  revokeSettlement,
  settleSettlement
} from '../../api/settlement'

const month = ref(currentMonth())
const records = ref([])
const loading = ref(false)
const generating = ref(false)
const actionId = ref(null)

const totalRevenue = computed(
  () => records.value.reduce((sum, row) => sum + Number(row.totalRevenue || 0), 0)
)
const totalFinalPayable = computed(
  () => records.value.reduce((sum, row) => sum + Number(row.finalPayable || 0), 0)
)

function currentMonth() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function money(value) {
  return Number(value || 0).toFixed(2)
}

function statusInfo(status) {
  return status === 'SETTLED'
    ? { label: '已结算', type: 'success' }
    : { label: '草稿', type: 'info' }
}

async function loadRecords() {
  if (!month.value) {
    return
  }
  loading.value = true
  try {
    const response = await listSettlements(month.value)
    records.value = response.data.data || []
  } catch (error) {
    records.value = []
  } finally {
    loading.value = false
  }
}

async function handleGenerate() {
  if (!month.value) {
    ElMessage.warning('请选择月份')
    return
  }
  try {
    const existing = await listSettlements(month.value)
    const existingRows = existing.data.data || []
    records.value = existingRows
    if (existingRows.length) {
      await ElMessageBox.confirm(
        '该月已有结算记录，是否重新生成 DRAFT 状态的记录？已 SETTLED 的记录不会被覆盖',
        '重新生成结算',
        { type: 'warning', confirmButtonText: '重新生成', cancelButtonText: '取消' }
      )
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      return
    }
    return
  }

  generating.value = true
  try {
    const response = await generateSettlements(month.value)
    const result = response.data.data || {}
    const skipped = result.skipped || []
    if (skipped.length) {
      ElMessage.warning(skipped.join('；'))
    } else {
      ElMessage.success(`结算已生成，共 ${(result.generated || []).length} 条`)
    }
    await loadRecords()
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    generating.value = false
  }
}

async function handleSettle(row) {
  try {
    await ElMessageBox.confirm(
      `确认结算 ${row.staffName} ${row.settleMonth} 的工资 ¥${money(row.finalPayable)} 吗？`,
      '确认结算',
      { type: 'warning', confirmButtonText: '确认结算', cancelButtonText: '取消' }
    )
  } catch (error) {
    return
  }
  actionId.value = row.id
  try {
    await settleSettlement(row.id)
    ElMessage.success('已确认结算')
    await loadRecords()
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    actionId.value = null
  }
}

async function handleRevoke(row) {
  try {
    await ElMessageBox.confirm(
      '撤销后结算会回到草稿状态，可重新生成。是否继续？',
      '撤销结算',
      { type: 'warning', confirmButtonText: '撤销', cancelButtonText: '取消' }
    )
  } catch (error) {
    return
  }
  actionId.value = row.id
  try {
    await revokeSettlement(row.id)
    ElMessage.success('结算已撤销为草稿')
    await loadRecords()
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    actionId.value = null
  }
}

onMounted(loadRecords)
</script>

<template>
  <section class="settlement-section">
    <div class="section-header">
      <div>
        <h2>月度结算</h2>
        <p>按完成时间归属业绩，按预约归属评价，结算后快照不自动变化</p>
      </div>
      <div class="header-actions">
        <el-date-picker
          v-model="month"
          type="month"
          value-format="YYYY-MM"
          placeholder="选择月份"
          :clearable="false"
          style="width: 150px"
          @change="loadRecords"
        />
        <el-button @click="loadRecords">刷新</el-button>
        <el-button type="primary" :loading="generating" @click="handleGenerate">
          生成结算
        </el-button>
      </div>
    </div>

    <div class="summary-row">
      <div class="summary-card">
        <span>总营收</span>
        <strong>¥{{ money(totalRevenue) }}</strong>
      </div>
      <div class="summary-card">
        <span>最终工资合计</span>
        <strong>¥{{ money(totalFinalPayable) }}</strong>
      </div>
      <div class="summary-card">
        <span>结算人数</span>
        <strong>{{ records.length }}</strong>
      </div>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="staffName" label="美甲师" min-width="110" fixed="left" />
        <el-table-column prop="completedCount" label="完成单数" width="90" />
        <el-table-column label="总营收" width="110">
          <template #default="{ row }">¥{{ money(row.totalRevenue) }}</template>
        </el-table-column>
        <el-table-column label="提成" width="110">
          <template #default="{ row }">¥{{ money(row.commissionAmount) }}</template>
        </el-table-column>
        <el-table-column prop="goodReviewCount" label="好评数" width="80" />
        <el-table-column label="好评奖励" width="110">
          <template #default="{ row }">¥{{ money(row.goodReviewBonusAmount) }}</template>
        </el-table-column>
        <el-table-column prop="badReviewCount" label="差评数" width="80" />
        <el-table-column label="差评扣减" width="110">
          <template #default="{ row }">¥{{ money(row.badReviewDeductionAmount) }}</template>
        </el-table-column>
        <el-table-column prop="noShowCount" label="爽约数" width="80" />
        <el-table-column label="爽约扣款" width="110">
          <template #default="{ row }">¥{{ money(row.noShowDeduction) }}</template>
        </el-table-column>
        <el-table-column label="最终工资" width="120">
          <template #default="{ row }">
            <strong>¥{{ money(row.finalPayable) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.status).type">
              {{ statusInfo(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="settledAt" label="结算时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT'"
              link
              type="success"
              :loading="actionId === row.id"
              @click="handleSettle(row)"
            >
              确认结算
            </el-button>
            <el-button
              v-else
              link
              type="warning"
              :loading="actionId === row.id"
              @click="handleRevoke(row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && records.length === 0" description="该月暂无结算记录" />
    </el-card>
  </section>
</template>

<style scoped>
.settlement-section {
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

.section-header h2 {
  margin: 0 0 6px;
  color: #303133;
  font-size: 18px;
}

.section-header p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.summary-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 150px;
  padding: 12px 16px;
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.summary-card span {
  color: #909399;
  font-size: 12px;
}

.summary-card strong {
  color: #303133;
  font-size: 18px;
}

@media (max-width: 720px) {
  .section-header {
    flex-direction: column;
  }
}
</style>
