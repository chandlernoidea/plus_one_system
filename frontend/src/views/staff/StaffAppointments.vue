<script setup>
/**
 * 美甲师 · 预约管理。
 *
 * 默认只看「待确认」，方便逐单审批；切到其他状态时列表可能很长，
 * 因此额外提供「每 3 天一段」的日期段。这里刻意不自动选中今天段：
 * 待审批的单分散在未来几天，自动过滤会把它们藏起来。
 */
import { computed, onMounted, ref } from 'vue'
import DateBlockBar from '../../components/DateBlockBar.vue'
import { useDateBlockSelection } from '../../composables/useDateBlockSelection'
import { ElMessage } from 'element-plus'
import {
  approveAppointment,
  listStaffAppointments,
  rejectAppointment
} from '../../api/appointment'
import StaffAppointmentDetail from './StaffAppointmentDetail.vue'

const appointments = ref([])
const loading = ref(false)
const statusFilter = ref('PENDING')
const approvingId = ref(null)
const rejectDialogVisible = ref(false)
const rejecting = ref(false)
const rejectTarget = ref(null)
const rejectReason = ref('')
const detailVisible = ref(false)
const selectedAppointment = ref(null)

const statusOptions = [
  { label: '待确认', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '改约中', value: 'RESCHEDULING' },
  { label: '服务中', value: 'IN_SERVICE' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '爽约', value: 'NO_SHOW' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已过期', value: 'EXPIRED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '全部', value: 'ALL' }
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

const dayOf = (row) => String(row.requestedStart || '').slice(0, 10)
// 美甲师侧不自动选中「今天」段：待审批的单分散在未来几天，默认必须看到全部
const { blockKey, blocks, visible: visibleAppointments, pick } = useDateBlockSelection(
  appointments,
  dayOf,
  { autoSelectCurrent: false }
)

function statusInfo(status) {
  return statusConfig[status] || { label: status || '未知', type: 'info' }
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

async function handleApprove(row) {
  approvingId.value = row.id
  try {
    await approveAppointment(row.id)
    ElMessage.success('预约已通过，已创建工单，请在详情中填写款式和物料')
    await loadAppointments()
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    approvingId.value = null
  }
}

function openRejectDialog(row) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function submitReject() {
  if (!rejectTarget.value) {
    return
  }
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写拒绝原因')
    return
  }

  rejecting.value = true
  try {
    await rejectAppointment(rejectTarget.value.id, { reason })
    ElMessage.success('预约已拒绝')
    rejectDialogVisible.value = false
    await loadAppointments()
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    rejecting.value = false
  }
}

function openDetail(row) {
  selectedAppointment.value = row
  detailVisible.value = true
}

async function handleDetailUpdated() {
  await loadAppointments()
  const updated = appointments.value.find((item) => item.id === selectedAppointment.value?.id)
  if (updated) {
    selectedAppointment.value = updated
  } else {
    detailVisible.value = false
  }
}

onMounted(loadAppointments)
</script>

<template>
  <section class="staff-appointments-section">
    <div class="section-header">
      <div>
        <h2>预约管理</h2>
        <p>审批顾客的预约申请</p>
      </div>
      <div class="header-actions">
        <el-select v-model="statusFilter" style="width: 160px" @change="loadAppointments">
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

    <DateBlockBar
      :blocks="blocks"
      :model-value="blockKey"
      :total="appointments.length"
      @update:model-value="pick"
    />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="visibleAppointments" stripe>
        <el-table-column prop="customerName" label="顾客" min-width="110" />
        <el-table-column prop="serviceTypeName" label="项目类型" min-width="130" />
        <el-table-column prop="requestedStart" label="申请开始时间" width="180" />
        <el-table-column label="预计时长" width="100">
          <template #default="{ row }">{{ row.estDurationMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="状态" width="105">
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
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button
                link
                type="success"
                :loading="approvingId === row.id"
                @click="handleApprove(row)"
              >
                通过
              </el-button>
              <el-button link type="danger" @click="openRejectDialog(row)">拒绝</el-button>
            </template>
            <span v-else class="no-action">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="rejectDialogVisible" title="拒绝预约" width="460px">
      <el-form label-width="80px">
        <el-form-item label="拒绝原因">
          <el-input
            v-model="rejectReason"
            type="textarea"
            :rows="4"
            maxlength="255"
            show-word-limit
            placeholder="请填写拒绝原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejecting" @click="submitReject">
          确认拒绝
        </el-button>
      </template>
    </el-dialog>

    <StaffAppointmentDetail
      v-model="detailVisible"
      :appointment="selectedAppointment"
      @updated="handleDetailUpdated"
    />
  </section>
</template>

<style scoped>
.staff-appointments-section {
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
  font-size: 18px;
  color: #303133;
}

.section-header p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.exempt-tag {
  margin-left: 6px;
}

.no-action {
  color: #c0c4cc;
}

@media (max-width: 720px) {
  .section-header {
    flex-direction: column;
  }
}
</style>
