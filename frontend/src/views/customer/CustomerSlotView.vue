<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listStaffs } from '../../api/staff'
import { listSlots } from '../../api/schedule'

const staffs = ref([])
const selectedStaffId = ref(null)
const selectedDate = ref(formatDate(new Date()))
const slots = ref([])
const loadingStaffs = ref(false)
const loadingSlots = ref(false)

const selectedStaff = computed(
  () => staffs.value.find((staff) => staff.staffId === selectedStaffId.value) || null
)

const statusConfig = {
  AVAILABLE: { label: '可申请', type: 'success' },
  BOOKED: { label: '已预约', type: 'danger' },
  IN_SERVICE: { label: '服务中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'info' },
  UNAVAILABLE: { label: '不可用', type: 'info' }
}

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function disabledPastDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

function formatSlotTime(value) {
  return value ? value.slice(11, 16) : ''
}

function statusInfo(status) {
  return statusConfig[status] || { label: status || '未知', type: 'info' }
}

async function loadStaffs() {
  loadingStaffs.value = true
  try {
    const response = await listStaffs()
    staffs.value = response.data.data || []
    if (!selectedStaffId.value && staffs.value.length > 0) {
      selectedStaffId.value = staffs.value[0].staffId
    }
  } catch (error) {
    staffs.value = []
  } finally {
    loadingStaffs.value = false
  }
}

async function loadSlots() {
  if (!selectedStaffId.value || !selectedDate.value) {
    slots.value = []
    return
  }

  loadingSlots.value = true
  try {
    const response = await listSlots({
      staffId: selectedStaffId.value,
      date: selectedDate.value
    })
    slots.value = response.data.data || []
  } catch (error) {
    slots.value = []
  } finally {
    loadingSlots.value = false
  }
}

function handleSlotClick(slot) {
  if (slot.status === 'AVAILABLE') {
    ElMessage.info('阶段 3 将开放预约申请')
  }
}

watch([selectedStaffId, selectedDate], loadSlots)
onMounted(loadStaffs)
</script>

<template>
  <section class="slot-section">
    <div class="section-header">
      <div>
        <h2>可预约时段</h2>
        <p>选择美甲师和日期后查看当天时间槽</p>
      </div>
    </div>

    <el-card shadow="never">
      <div class="filters">
        <el-select
          v-model="selectedStaffId"
          :loading="loadingStaffs"
          placeholder="请选择美甲师"
          style="width: 240px"
        >
          <el-option
            v-for="staff in staffs"
            :key="staff.staffId"
            :label="staff.staffNo ? `${staff.realName}（${staff.staffNo}）` : staff.realName"
            :value="staff.staffId"
          />
        </el-select>

        <el-date-picker
          v-model="selectedDate"
          type="date"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          :disabled-date="disabledPastDate"
          placeholder="请选择日期"
          style="width: 180px"
        />
      </div>

      <div v-loading="loadingSlots" class="slot-content">
        <div v-if="selectedStaff" class="selected-staff">
          当前美甲师：{{ selectedStaff.realName }}
        </div>

        <el-row v-if="slots.length > 0" :gutter="12">
          <el-col
            v-for="slot in slots"
            :key="slot.id"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
          >
            <div
              class="slot-card"
              :class="{ clickable: slot.status === 'AVAILABLE' }"
              @click="handleSlotClick(slot)"
            >
              <span class="slot-time">
                {{ formatSlotTime(slot.slotStart) }} - {{ formatSlotTime(slot.slotEnd) }}
              </span>
              <el-tag :type="statusInfo(slot.status).type" size="small">
                {{ statusInfo(slot.status).label }}
              </el-tag>
            </div>
          </el-col>
        </el-row>

        <el-empty v-else-if="!loadingSlots" description="该美甲师当天暂无排班" />
      </div>
    </el-card>
  </section>
</template>

<style scoped>
.slot-section {
  display: flex;
  flex-direction: column;
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

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.slot-content {
  min-height: 160px;
}

.selected-staff {
  margin-bottom: 14px;
  color: #606266;
  font-size: 13px;
}

.slot-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.slot-card.clickable {
  cursor: pointer;
}

.slot-card.clickable:hover {
  border-color: #67c23a;
  box-shadow: 0 2px 10px rgba(103, 194, 58, 0.15);
}

.slot-time {
  color: #303133;
  font-size: 14px;
  font-weight: 500;
}
</style>
