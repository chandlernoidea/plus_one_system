<script setup>
import { onMounted, ref } from 'vue'
import { listMySettlements } from '../../api/settlement'

const records = ref([])
const loading = ref(false)

function money(value) {
  return Number(value || 0).toFixed(2)
}

function statusInfo(status) {
  return status === 'SETTLED'
    ? { label: '已结算', type: 'success' }
    : { label: '草稿', type: 'info' }
}

async function loadRecords() {
  loading.value = true
  try {
    const response = await listMySettlements()
    records.value = response.data.data || []
  } catch (error) {
    records.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <section class="staff-settlement-section">
    <div class="section-header">
      <div>
        <h2>我的工资</h2>
        <p>结算数据为月末快照，如有疑问请联系店长</p>
      </div>
      <el-button @click="loadRecords">刷新</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="工资按完成服务归属月份结算，评价按预约关联统计；已结算快照不会自动变化。"
    />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="settlement-detail">
              <div class="detail-item">
                <span>底薪</span>
                <strong>¥{{ money(row.baseSalary) }}</strong>
              </div>
              <div class="detail-item">
                <span>提成</span>
                <strong>¥{{ money(row.commissionAmount) }}</strong>
              </div>
              <div class="detail-item">
                <span>好评奖励</span>
                <strong>¥{{ money(row.goodReviewBonusAmount) }}</strong>
              </div>
              <div class="detail-item">
                <span>差评扣减</span>
                <strong>-¥{{ money(row.badReviewDeductionAmount) }}</strong>
              </div>
              <div class="detail-item">
                <span>爽约扣款</span>
                <strong>-¥{{ money(row.noShowDeduction) }}</strong>
              </div>
              <div class="detail-item">
                <span>保底前应得</span>
                <strong>¥{{ money(row.finalPayableBeforeFloor) }}</strong>
              </div>
              <div class="detail-item highlight">
                <span>最终工资</span>
                <strong>¥{{ money(row.finalPayable) }}</strong>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="settleMonth" label="月份" width="100" />
        <el-table-column prop="completedCount" label="完成单数" width="100" />
        <el-table-column label="总营收" width="110">
          <template #default="{ row }">¥{{ money(row.totalRevenue) }}</template>
        </el-table-column>
        <el-table-column label="提成" width="110">
          <template #default="{ row }">¥{{ money(row.commissionAmount) }}</template>
        </el-table-column>
        <el-table-column prop="goodReviewCount" label="好评数" width="80" />
        <el-table-column prop="badReviewCount" label="差评数" width="80" />
        <el-table-column prop="noShowCount" label="爽约数" width="80" />
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
      </el-table>
      <el-empty v-if="!loading && records.length === 0" description="暂无结算记录" />
    </el-card>
  </section>
</template>

<style scoped>
.staff-settlement-section {
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

.settlement-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 8px 20px 12px;
  background: #fafafa;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 110px;
}

.detail-item span {
  color: #909399;
  font-size: 12px;
}

.detail-item strong {
  color: #303133;
  font-size: 14px;
}

.detail-item.highlight strong {
  color: #e6a23c;
}

@media (max-width: 720px) {
  .section-header {
    flex-direction: column;
  }
}
</style>
