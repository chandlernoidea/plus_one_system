<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSchedule,
  deleteSchedule,
  listSchedules,
  updateSchedule
} from '../../api/schedule'
import { useUserStore } from '../../store/user'

const userStore = useUserStore()

// 从档期日历点「为这一天排班」进来时的预填参数
const props = defineProps({
  initialDate: { type: String, default: '' },
  requestToken: { type: Number, default: 0 }
})

const loading = ref(false)
const saving = ref(false)
const schedules = ref([])
const dateRange = ref(getCurrentWeekRange())
const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingId = ref(null)
const formRef = ref()

const form = reactive({
  workDate: formatDate(new Date()),
  startTime: '09:00:00',
  endTime: '18:00:00',
  scheduleType: 'NORMAL',
  remark: ''
})

const rules = {
  workDate: [{ required: true, message: '请选择排班日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

const dialogTitle = computed(() =>
  dialogMode.value === 'create' ? '新增排班' : '编辑排班'
)
const currentStaffName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || ''
)

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getCurrentWeekRange() {
  const today = new Date()
  const day = today.getDay() || 7
  const monday = new Date(today)
  monday.setDate(today.getDate() - day + 1)
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  return [formatDate(monday), formatDate(sunday)]
}

function disabledPastDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

function formatDisplayTime(time) {
  return time ? time.slice(0, 5) : ''
}

function scheduleTypeLabel(type) {
  return type === 'LEAVE' ? '休假' : '正常'
}

function scheduleTypeTag(type) {
  return type === 'LEAVE' ? 'warning' : 'success'
}

async function loadSchedules() {
  if (!dateRange.value || dateRange.value.length !== 2) {
    schedules.value = []
    return
  }

  loading.value = true
  try {
    const [from, to] = dateRange.value
    const response = await listSchedules({ from, to })
    schedules.value = response.data.data || []
  } catch (error) {
    schedules.value = []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.workDate = formatDate(new Date())
  form.startTime = '09:00:00'
  form.endTime = '18:00:00'
  form.scheduleType = 'NORMAL'
  form.remark = ''
}

function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEditDialog(row) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.workDate = row.workDate
  form.startTime = row.startTime
  form.endTime = row.endTime
  form.scheduleType = row.scheduleType || 'NORMAL'
  form.remark = row.remark || ''
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function saveSchedule() {
  if (!formRef.value) {
    return
  }

  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  saving.value = true
  try {
    const payload = {
      workDate: form.workDate,
      startTime: form.startTime,
      endTime: form.endTime,
      scheduleType: form.scheduleType,
      remark: form.remark
    }

    if (dialogMode.value === 'create') {
      await createSchedule(payload)
      ElMessage.success('排班新增成功')
    } else {
      await updateSchedule(editingId.value, payload)
      ElMessage.success('排班修改成功')
    }

    dialogVisible.value = false
    await loadSchedules()
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除 ${row.workDate} ${formatDisplayTime(row.startTime)}-${formatDisplayTime(row.endTime)} 的排班吗？`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
  } catch (error) {
    return
  }

  try {
    await deleteSchedule(row.id)
    ElMessage.success('排班删除成功')
    await loadSchedules()
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  }
}

onMounted(loadSchedules)

watch(
  () => props.requestToken,
  () => {
    if (!props.requestToken) {
      return
    }
    openCreateDialog()
    if (props.initialDate) {
      form.workDate = props.initialDate
    }
  },
  { immediate: true }
)
</script>

<template>
  <section class="schedule-section">
    <div class="section-header">
      <div>
        <h2>排班管理</h2>
        <p>当前美甲师：{{ currentStaffName }}，时间槽按 15 分钟自动生成</p>
      </div>
      <el-button type="primary" @click="openCreateDialog">新增排班</el-button>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <span class="toolbar-label">日期范围</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="loadSchedules"
        />
      </div>

      <el-table v-loading="loading" :data="schedules" stripe>
        <el-table-column prop="workDate" label="日期" width="130" />
        <el-table-column label="开始时间" width="110">
          <template #default="{ row }">{{ formatDisplayTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="110">
          <template #default="{ row }">{{ formatDisplayTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="scheduleTypeTag(row.scheduleType)">
              {{ scheduleTypeLabel(row.scheduleType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="日期" prop="workDate">
          <el-date-picker
            v-model="form.workDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            :disabled-date="disabledPastDate"
            placeholder="请选择日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="开始时间" prop="startTime">
          <el-time-picker
            v-model="form.startTime"
            format="HH:mm"
            value-format="HH:mm:ss"
            placeholder="请选择开始时间"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="结束时间" prop="endTime">
          <el-time-picker
            v-model="form.endTime"
            format="HH:mm"
            value-format="HH:mm:ss"
            placeholder="请选择结束时间"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="班次类型" prop="scheduleType">
          <el-select v-model="form.scheduleType" style="width: 100%">
            <el-option label="正常" value="NORMAL" />
            <el-option label="休假" value="LEAVE" />
          </el-select>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSchedule">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.schedule-section {
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

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.toolbar-label {
  color: #606266;
  font-size: 14px;
}

@media (max-width: 720px) {
  .section-header {
    flex-direction: column;
  }

  .toolbar {
    flex-wrap: wrap;
  }
}
</style>
