<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelAppointment, listMyAppointments } from '../../api/appointment'
import CustomerAppointmentDetail from './CustomerAppointmentDetail.vue'

const appointments = ref([])
const loading = ref(false)
const cancellingId = ref(null)
const detailVisible = ref(false)
const selectedAppointment = ref(null)

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

function statusInfo(status) {
  return statusConfig[status] || { label: status || '未知', type: 'info' }
}

function canCancel(status) {
  return status === 'PENDING' || status === 'CONFIRMED' || status === 'RESCHEDULING'
}

async function loadAppointments() {
  loading.value = true
  try {
    const response = await listMyAppointments()
    appointments.value = response.data.data || []
  } catch (error) {
    appointments.value = []
  } finally {
    loading.value = false
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(
      `确定取消 ${row.requestedStart} 的预约吗？`,
      '取消预约',
      {
        type: 'warning',
        confirmButtonText: '确定取消',
        cancelButtonText: '返回'
      }
    )
  } catch (error) {
    return
  }

  cancellingId.value = row.id
  try {
    await cancelAppointment(row.id)
    ElMessage.success('预约已取消')
    await loadAppointments()
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    cancellingId.value = null
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

defineExpose({ refresh: loadAppointments })
onMounted(loadAppointments)
</script>

<template>
  <section class="appointments-section">
    <div class="section-header">
      <div>
        <h2>我的预约</h2>
        <p>查看预约申请状态并取消待确认或已确认的预约</p>
      </div>
      <el-button @click="loadAppointments">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="appointments" stripe>
        <el-table-column prop="serviceTypeName" label="项目类型" min-width="130" />
        <el-table-column prop="staffName" label="美甲师" min-width="120" />
        <el-table-column prop="requestedStart" label="申请时间" width="180" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.status).type">
              {{ statusInfo(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="canCancel(row.status)"
              link
              type="danger"
              :loading="cancellingId === row.id"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
            <span v-else class="no-action">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <CustomerAppointmentDetail
      v-model="detailVisible"
      :appointment="selectedAppointment"
      @updated="handleDetailUpdated"
    />
  </section>
</template>

<style scoped>
.appointments-section {
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

.no-action {
  color: #c0c4cc;
}
</style>
