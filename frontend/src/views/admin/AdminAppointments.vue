<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  exemptNoShow,
  listStaffAppointments,
  updateAppointmentTotalAmount
} from '../../api/appointment'

const appointments = ref([])
const loading = ref(false)
const statusFilter = ref('ALL')
const rangeFilter = ref('month')
const actionId = ref(null)
const amountDialogVisible = ref(false)
const amountTarget = ref(null)
const amountValue = ref(0)
const savingAmount = ref(false)

const rangeOptions = [
  { label: '本月', value: 'month' },
  { label: '今天', value: 'today' },
  { label: '未来 7 天', value: 'week' },
  { label: '全部', value: 'all' }
]

const statusOptions = [
  { label: '全部', value: 'ALL' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '服务中', value: 'IN_SERVICE' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '爽约', value: 'NO_SHOW' },
  { label: '已取消', value: 'CANCELLED' }
]

const statusConfig = {
  PENDING: { label: '待确认', type: 'primary' },
  CONFIRMED: { label: '已确认', type: 'success' },
  RESCHEDULING: { label: '改约中', type: 'warning' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  EXPIRED: { label: '已过期', type: 'info' },
  CANCELLED: { label: '已取消', type: 'info' },
  IN_SERVICE: { label: '服务中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'info' },
  NO_SHOW: { label: '爽约', type: 'danger' }
}

function shiftKey(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}-${String(next.getDate()).padStart(2, '0')}`
}

/** 日期范围在前端收窄：默认只看本月，避免历史数据把当天要处理的行淹掉 */
const visibleAppointments = computed(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const todayKey = shiftKey(today, 0)
  const weekEnd = shiftKey(today, 7)

  return appointments.value.filter((row) => {
    const key = (row.requestedStart || '').slice(0, 10)
    if (!key) {
      return true
    }
    if (rangeFilter.value === 'today') {
      return key === todayKey
    }
    if (rangeFilter.value === 'week') {
      return key >= todayKey && key <= weekEnd
    }
    if (rangeFilter.value === 'month') {
      return key.slice(0, 7) === todayKey.slice(0, 7)
    }
    return true
  })
})

function statusInfo(status) {
  return statusConfig[status] || { label: status || '未知', type: 'info' }
}

function money(value) {
  return value === null || value === undefined ? '未录入' : `¥${Number(value).toFixed(2)}`
}

async function loadAppointments() {
  loading.value = true
  try {
    const status = statusFilter.value === 'ALL' ? '' : statusFilter.value
    const response = await listStaffAppointments({ status })
    appointments.value = response.data.data || []
  } catch (error) {
    appointments.value = []
  } finally {
    loading.value = false
  }
}

async function handleExempt(row) {
  try {
    await ElMessageBox.confirm(
      `确认撤销 ${row.customerName} 的爽约标记吗？预约状态仍保留为爽约，结算时不再扣款。`,
      '撤销爽约',
      { type: 'warning', confirmButtonText: '撤销爽约', cancelButtonText: '取消' }
    )
  } catch (error) {
    return
  }
  actionId.value = row.id
  try {
    await exemptNoShow(row.id)
    ElMessage.success('已撤销爽约，结算时不再扣款')
    await loadAppointments()
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    actionId.value = null
  }
}

function openAmountDialog(row) {
  amountTarget.value = row
  amountValue.value = row.totalAmount === null ? 0 : Number(row.totalAmount)
  amountDialogVisible.value = true
}

async function submitAmount() {
  if (!amountTarget.value) {
    return
  }
  if (amountValue.value === null || amountValue.value < 0) {
    ElMessage.warning('订单金额不能小于0')
    return
  }
  savingAmount.value = true
  try {
    await updateAppointmentTotalAmount(amountTarget.value.id, {
      totalAmount: Number(amountValue.value)
    })
    ElMessage.success('订单金额已修正，请重新生成对应月份的草稿结算')
    amountDialogVisible.value = false
    await loadAppointments()
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    savingAmount.value = false
  }
}

onMounted(loadAppointments)
</script>

<template>
  <section class="admin-appointments-section">
    <div class="section-header">
      <div>
        <h2>预约管理</h2>
        <p>管理员可撤销爽约，或修正已完成预约的订单金额</p>
      </div>
      <div class="header-actions">
        <span class="list-count mono">{{ visibleAppointments.length }} 条</span>
        <el-select v-model="rangeFilter" style="width: 130px">
          <el-option
            v-for="option in rangeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select v-model="statusFilter" style="width: 140px" @change="loadAppointments">
          <el-option
            v-for="option in statusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button @click="loadAppointments">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="visibleAppointments" stripe>
        <el-table-column prop="customerName" label="顾客" width="110" />
        <el-table-column prop="staffName" label="美甲师" width="120" />
        <el-table-column prop="serviceTypeName" label="项目" min-width="120" />
        <el-table-column prop="requestedStart" label="申请时间" width="180" />
        <el-table-column prop="confirmedStart" label="确认时间" width="180" />
        <el-table-column label="金额" width="110">
          <template #default="{ row }">{{ money(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.status).type">
              {{ statusInfo(row.status).label }}
            </el-tag>
            <el-tag
              v-if="row.status === 'NO_SHOW' && row.noShowExempt === 1"
              type="info"
              size="small"
              class="exempt-tag"
            >
              已豁免
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'NO_SHOW' && row.noShowExempt !== 1"
              link
              type="warning"
              :loading="actionId === row.id"
              @click="handleExempt(row)"
            >
              撤销爽约
            </el-button>
            <el-button
              v-if="row.status === 'COMPLETED'"
              link
              type="primary"
              @click="openAmountDialog(row)"
            >
              修正金额
            </el-button>
            <span
              v-if="row.status !== 'NO_SHOW' && row.status !== 'COMPLETED'"
              class="no-action"
            >
              -
            </span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && visibleAppointments.length === 0" description="这个范围内没有预约" />
    </el-card>

    <el-dialog v-model="amountDialogVisible" title="修正订单金额" width="420px">
      <el-form label-width="90px">
        <el-form-item label="顾客">
          <span>{{ amountTarget?.customerName }}</span>
        </el-form-item>
        <el-form-item label="订单金额" required>
          <el-input-number v-model="amountValue" :min="0" :precision="2" :step="50" />
        </el-form-item>
        <div class="dialog-tip">
          修正后请重新生成对应月份的草稿结算；已结算快照不会自动变化。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="amountDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingAmount" @click="submitAmount">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-appointments-section {
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
  align-items: center;
  gap: 10px;
}

.list-count {
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.exempt-tag {
  margin-left: 6px;
}

.no-action {
  color: #c0c4cc;
}

.dialog-tip {
  margin-left: 90px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 720px) {
  .section-header {
    flex-direction: column;
  }
}
</style>
