<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../../store/user'
import { getPrepCustomerView } from '../../api/prep'
import { acceptReschedule, rejectReschedule } from '../../api/reschedule'
import { createReview } from '../../api/review'
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
const prepView = ref(null)
const rescheduleVisible = ref(false)
const reviewDialogVisible = ref(false)
const submittingReview = ref(false)
const responding = ref(false)
const reviewForm = ref({ rating: 5, content: '', imageUrls: [] })

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
const canReview = computed(() =>
  props.appointment?.status === 'COMPLETED' && !props.appointment?.review
)
const hasPrepContent = computed(() =>
  Boolean(prepView.value?.styleDescription || prepView.value?.effectImageUrl)
)

watch(
  () => [props.modelValue, props.appointment?.id, props.appointment?.status],
  () => {
    if (props.modelValue && props.appointment) {
      loadPrep()
    }
  }
)

async function loadPrep() {
  if (!props.appointment) {
    return
  }
  prepView.value = props.appointment.customerPrepView || null
  if (!['CONFIRMED', 'RESCHEDULING', 'IN_SERVICE', 'COMPLETED'].includes(props.appointment.status)) {
    return
  }
  if (prepView.value) {
    return
  }

  loadingPrep.value = true
  try {
    const response = await getPrepCustomerView(props.appointment.id)
    prepView.value = response.data.data || null
  } catch (error) {
    prepView.value = null
  } finally {
    loadingPrep.value = false
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

function openReviewDialog() {
  reviewForm.value = { rating: 5, content: '', imageUrls: [] }
  reviewDialogVisible.value = true
}

async function submitReview() {
  if (!props.appointment) {
    return
  }
  if (!reviewForm.value.rating) {
    ElMessage.warning('请选择评分')
    return
  }
  if (!reviewForm.value.content.trim()) {
    ElMessage.warning('请填写评价内容')
    return
  }

  submittingReview.value = true
  try {
    await createReview(props.appointment.id, {
      rating: reviewForm.value.rating,
      content: reviewForm.value.content,
      imageUrls: reviewForm.value.imageUrls
    })
    ElMessage.success('评价提交成功')
    reviewDialogVisible.value = false
    visible.value = false
    emit('updated')
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    submittingReview.value = false
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
  <el-drawer v-model="visible" title="预约详情" size="580px">
    <template v-if="appointment">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="项目">{{ appointment.serviceTypeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="美甲师">{{ appointment.staffName || '-' }}</el-descriptions-item>
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

      <div v-if="appointment.finishImageUrls?.length" class="section-title">完工效果</div>
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

      <div v-if="appointment.review" class="section-title">我的评价</div>
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

      <div class="section-title">工单信息</div>
      <div v-loading="loadingPrep" class="prep-view">
        <template v-if="hasPrepContent">
          <div v-if="prepView?.styleDescription" class="prep-row">
            <span class="prep-label">款式说明</span>
            <span>{{ prepView.styleDescription }}</span>
          </div>
          <div v-if="prepView?.effectImageUrl" class="prep-row">
            <span class="prep-label">效果图</span>
            <el-image
              :src="prepView.effectImageUrl"
              :preview-src-list="[prepView.effectImageUrl]"
              fit="cover"
              class="prep-image"
            />
          </div>
        </template>
        <el-empty v-else description="美甲师还未填写工单信息" :image-size="72" />
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
        <el-button v-if="canReview" type="primary" @click="openReviewDialog">
          评价服务
        </el-button>
      </div>

      <RescheduleDialog
        v-model="rescheduleVisible"
        :appointment="appointment"
        @success="handleRescheduleSuccess"
      />

      <el-dialog v-model="reviewDialogVisible" title="评价服务" width="520px">
        <el-form label-width="80px">
          <el-form-item label="评分">
            <el-rate v-model="reviewForm.rating" />
          </el-form-item>
          <el-form-item label="评价内容">
            <el-input
              v-model="reviewForm.content"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="请分享本次服务体验"
            />
          </el-form-item>
          <el-form-item label="图片">
            <MultiImageUploader v-model="reviewForm.imageUrls" :limit="3" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="reviewDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submittingReview" @click="submitReview">
            提交评价
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
  margin: 24px 0 14px;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.prep-view {
  min-height: 80px;
}

.prep-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
}

.prep-label {
  flex: 0 0 70px;
  color: #909399;
}

.prep-image {
  width: 120px;
  height: 120px;
  border-radius: 6px;
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
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
</style>
