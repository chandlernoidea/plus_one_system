<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../../store/user'
import { getPrep, updatePrep } from '../../api/prep'
import { acceptReschedule, rejectReschedule } from '../../api/reschedule'
import {
  completeAppointment,
  noShowAppointment,
  startAppointment
} from '../../api/appointment'
import ImageUploader from '../../components/ImageUploader.vue'
import MultiImageUploader from '../../components/MultiImageUploader.vue'
import RescheduleDialog from '../../components/RescheduleDialog.vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  appointment: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'updated'])

const userStore = useUserStore()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const loadingPrep = ref(false)
const savingPrep = ref(false)
const rescheduleVisible = ref(false)
const completeDialogVisible = ref(false)
const finishImageUrls = ref([])
const completionAmount = ref(0)
const lifecycleLoading = ref(false)
const responding = ref(false)
const prepLoaded = ref(false)
const prepForm = reactive({
  styleDescription: '',
  effectImageUrl: '',
  materialJson: '',
  staffNote: ''
})

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

const statusInfo = computed(() =>
  statusConfig[props.appointment?.status] || {
    label: props.appointment?.status || '未知',
    type: 'info'
  }
)

const pendingReschedule = computed(() => props.appointment?.pendingReschedule || null)
const isRescheduleProposer = computed(() =>
  Boolean(pendingReschedule.value)
  && pendingReschedule.value.proposerId === userStore.userInfo?.userId
)
const canRespondReschedule = computed(() =>
  Boolean(pendingReschedule.value) && !isRescheduleProposer.value
)
const canCreateReschedule = computed(() =>
  props.appointment?.status === 'CONFIRMED'
)
const canEditPrep = computed(() =>
  props.appointment
  && !['PENDING', 'REJECTED', 'EXPIRED', 'CANCELLED'].includes(props.appointment.status)
)

watch(
  () => [props.modelValue, props.appointment?.id, props.appointment?.status],
  () => {
    if (props.modelValue && props.appointment) {
      resetPrepForm()
      loadPrep()
    }
  }
)

function resetPrepForm() {
  prepForm.styleDescription = ''
  prepForm.effectImageUrl = ''
  prepForm.materialJson = ''
  prepForm.staffNote = ''
  prepLoaded.value = false
}

async function loadPrep() {
  if (!props.appointment || !canEditPrep.value) {
    return
  }
  loadingPrep.value = true
  try {
    const response = await getPrep(props.appointment.id)
    const prep = response.data.data
    prepForm.styleDescription = prep.styleDescription || ''
    prepForm.effectImageUrl = prep.effectImageUrl || ''
    prepForm.materialJson = prep.materialJson || ''
    prepForm.staffNote = prep.staffNote || ''
    prepLoaded.value = true
  } catch (error) {
    prepLoaded.value = false
  } finally {
    loadingPrep.value = false
  }
}

async function savePrep() {
  if (!props.appointment) {
    return
  }
  savingPrep.value = true
  try {
    await updatePrep(props.appointment.id, {
      styleDescription: prepForm.styleDescription,
      effectImageUrl: prepForm.effectImageUrl,
      materialJson: prepForm.materialJson,
      staffNote: prepForm.staffNote
    })
    ElMessage.success('工单已保存')
    prepLoaded.value = true
    emit('updated')
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    savingPrep.value = false
  }
}

async function handleAcceptReschedule() {
  if (!pendingReschedule.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定接受改约到 ${pendingReschedule.value.proposedStart} 吗？`,
      '接受改约',
      { type: 'warning', confirmButtonText: '接受', cancelButtonText: '取消' }
    )
  } catch (error) {
    return
  }

  responding.value = true
  try {
    await acceptReschedule(pendingReschedule.value.id)
    ElMessage.success('改约已接受')
    visible.value = false
    emit('updated')
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    responding.value = false
  }
}

async function handleRejectReschedule() {
  if (!pendingReschedule.value) {
    return
  }
  try {
    await ElMessageBox.confirm('确定拒绝这次改约提案吗？', '拒绝改约', {
      type: 'warning',
      confirmButtonText: '拒绝',
      cancelButtonText: '取消'
    })
  } catch (error) {
    return
  }

  responding.value = true
  try {
    await rejectReschedule(pendingReschedule.value.id)
    ElMessage.success('改约已拒绝')
    visible.value = false
    emit('updated')
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    responding.value = false
  }
}

async function handleStartService() {
  if (!props.appointment) {
    return
  }
  try {
    await ElMessageBox.confirm('确认开始服务吗？', '开始服务', {
      type: 'warning', confirmButtonText: '开始', cancelButtonText: '取消'
    })
  } catch (error) {
    return
  }
  lifecycleLoading.value = true
  try {
    await startAppointment(props.appointment.id)
    ElMessage.success('已开始服务')
    visible.value = false
    emit('updated')
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    lifecycleLoading.value = false
  }
}

async function handleNoShow() {
  if (!props.appointment) {
    return
  }
  try {
    await ElMessageBox.confirm('确认将该预约标记为爽约吗？该时段会释放。', '标记爽约', {
      type: 'warning', confirmButtonText: '标记爽约', cancelButtonText: '取消'
    })
  } catch (error) {
    return
  }
  lifecycleLoading.value = true
  try {
    await noShowAppointment(props.appointment.id)
    ElMessage.success('已标记爽约')
    visible.value = false
    emit('updated')
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    lifecycleLoading.value = false
  }
}

function openCompleteDialog() {
  finishImageUrls.value = []
  completionAmount.value = 0
  completeDialogVisible.value = true
}

async function submitComplete() {
  if (!props.appointment) {
    return
  }
  if (completionAmount.value === null || completionAmount.value < 0) {
    ElMessage.warning('订单金额不能小于0')
    return
  }
  lifecycleLoading.value = true
  try {
    await completeAppointment(props.appointment.id, {
      finishImageUrls: finishImageUrls.value,
      totalAmount: Number(completionAmount.value)
    })
    ElMessage.success('服务已完成')
    completeDialogVisible.value = false
    visible.value = false
    emit('updated')
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    lifecycleLoading.value = false
  }
}

function openRescheduleDialog() {
  rescheduleVisible.value = true
}

function handleRescheduleSuccess() {
  visible.value = false
  emit('updated')
}
</script>

<template>
  <el-drawer v-model="visible" title="预约详情" size="620px">
    <template v-if="appointment">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="顾客">{{ appointment.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="项目">{{ appointment.serviceTypeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ appointment.confirmedStart || appointment.requestedStart }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusInfo.type">{{ statusInfo.label }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="预计时长">
          {{ appointment.estDurationMinutes }} 分钟
        </el-descriptions-item>
        <el-descriptions-item label="预约 ID">{{ appointment.id }}</el-descriptions-item>
        <el-descriptions-item label="实际开始">{{ appointment.actualStart || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际结束">{{ appointment.actualEnd || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="pendingReschedule" class="reschedule-box">
        <div class="box-title">待处理改约</div>
        <div>提案人：{{ pendingReschedule.proposerName }}</div>
        <div>新时间：{{ pendingReschedule.proposedStart }}</div>
        <div>过期时间：{{ pendingReschedule.expireAt }}</div>
        <div v-if="pendingReschedule.reason">原因：{{ pendingReschedule.reason }}</div>
        <div v-if="isRescheduleProposer" class="waiting">等待对方回应</div>
        <div v-else-if="canRespondReschedule" class="reschedule-actions">
          <el-button type="success" :loading="responding" @click="handleAcceptReschedule">
            接受
          </el-button>
          <el-button type="danger" :loading="responding" @click="handleRejectReschedule">
            拒绝
          </el-button>
        </div>
      </div>

      <div v-if="appointment.finishImageUrls?.length" class="section-title">完工图</div>
      <div v-if="appointment.finishImageUrls?.length" class="finish-images">
        <el-image
          v-for="(url, index) in appointment.finishImageUrls"
          :key="url"
          :src="url"
          :preview-src-list="appointment.finishImageUrls"
          :initial-index="index"
          fit="cover"
          class="finish-image"
        />
      </div>

      <div v-if="appointment.review" class="section-title">顾客评价</div>
      <div v-if="appointment.review" class="review-box">
        <el-rate :model-value="appointment.review.rating" disabled show-score />
        <div class="review-content">{{ appointment.review.content }}</div>
        <div v-if="appointment.review.imageUrls?.length" class="review-images">
          <el-image
            v-for="(url, index) in appointment.review.imageUrls"
            :key="url"
            :src="url"
            :preview-src-list="appointment.review.imageUrls"
            :initial-index="index"
            fit="cover"
            class="review-image"
          />
        </div>
      </div>

      <div class="section-title">
        <span>款式物料工单</span>
        <el-tag v-if="prepLoaded" type="success" size="small">已加载</el-tag>
      </div>

      <div v-if="!canEditPrep" class="empty-tip">当前预约状态暂不可编辑工单。</div>
      <div v-else v-loading="loadingPrep">
        <el-form label-width="92px" class="prep-form">
          <el-form-item label="款式说明">
            <el-input
              v-model="prepForm.styleDescription"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              placeholder="描述款式、颜色、风格等"
            />
          </el-form-item>
          <el-form-item label="效果图">
            <ImageUploader v-model="prepForm.effectImageUrl" />
          </el-form-item>
          <el-form-item label="物料清单">
            <el-input
              v-model="prepForm.materialJson"
              type="textarea"
              :rows="5"
              placeholder='可自由填写，支持 JSON 格式，例如 {"色号":"N01","饰品":"水钻"}'
            />
          </el-form-item>
          <el-form-item label="美甲师备注">
            <el-input
              v-model="prepForm.staffNote"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="内部备注"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingPrep" @click="savePrep">
              保存工单
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="drawer-footer">
        <el-button
          v-if="canCreateReschedule"
          type="warning"
          plain
          @click="openRescheduleDialog"
        >
          发起改约
        </el-button>
        <el-button
          v-if="appointment.status === 'CONFIRMED'"
          type="primary"
          :loading="lifecycleLoading"
          @click="handleStartService"
        >
          开始服务
        </el-button>
        <el-button
          v-if="appointment.status === 'CONFIRMED'"
          type="danger"
          plain
          :loading="lifecycleLoading"
          @click="handleNoShow"
        >
          标记爽约
        </el-button>
        <el-button
          v-if="appointment.status === 'IN_SERVICE'"
          type="success"
          :loading="lifecycleLoading"
          @click="openCompleteDialog"
        >
          完成服务
        </el-button>
      </div>

      <RescheduleDialog
        v-model="rescheduleVisible"
        :appointment="appointment"
        @success="handleRescheduleSuccess"
      />

      <el-dialog v-model="completeDialogVisible" title="完成服务" width="500px">
        <el-form label-width="92px">
          <el-form-item label="订单金额" required>
            <el-input-number v-model="completionAmount" :min="0" :precision="2" :step="50" />
            <span class="amount-tip">用于月度业绩与提成结算</span>
          </el-form-item>
          <el-form-item label="完工图">
            <MultiImageUploader v-model="finishImageUrls" :limit="6" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="completeDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="lifecycleLoading" @click="submitComplete">
            确认完成
          </el-button>
        </template>
      </el-dialog>
    </template>
  </el-drawer>
</template>

<style scoped>
.reschedule-box {
  margin-top: 18px;
  padding: 14px 16px;
  border: 1px solid #f3d19e;
  border-radius: 8px;
  background: #fdf6ec;
  color: #606266;
  font-size: 13px;
  line-height: 1.9;
}

.box-title {
  margin-bottom: 4px;
  color: #e6a23c;
  font-weight: 600;
}

.waiting {
  margin-top: 8px;
  color: #e6a23c;
  font-weight: 500;
}

.reschedule-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 24px 0 14px;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.empty-tip {
  color: #909399;
  font-size: 13px;
}

.prep-form {
  padding-top: 4px;
}

.amount-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.finish-images {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.finish-image {
  width: 100px;
  height: 100px;
  border-radius: 8px;
}

.review-box {
  padding: 14px 16px;
  border-radius: 8px;
  background: #f5f7fa;
}

.review-content {
  margin-top: 8px;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.review-image {
  width: 76px;
  height: 76px;
  border-radius: 6px;
}

.drawer-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
</style>
