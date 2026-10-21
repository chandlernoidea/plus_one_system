<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const uploadRef = ref()
const fileList = ref([])
const uploading = ref(false)

const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}))

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      fileList.value = [{ name: value.split('/').pop(), url: value }]
    } else {
      fileList.value = []
    }
  },
  { immediate: true }
)

function beforeUpload(file) {
  const allowed = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowed.includes(file.type)) {
    ElMessage.error('只支持 jpg、jpeg、png、webp 图片')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过5MB')
    return false
  }
  uploading.value = true
  return true
}

async function handleSuccess(response, file) {
  uploading.value = false
  if (!response || response.code !== 200 || !response.data) {
    ElMessage.error(response?.message || '图片上传失败')
    fileList.value = props.modelValue
      ? [{ name: props.modelValue.split('/').pop(), url: props.modelValue }]
      : []
    uploadRef.value?.clearFiles()
    return
  }
  const url = response.data
  fileList.value = [{ name: url.split('/').pop(), url }]
  emit('update:modelValue', url)
  ElMessage.success('图片上传成功')
}

function handleError() {
  uploading.value = false
  ElMessage.error('图片上传失败')
  fileList.value = props.modelValue
    ? [{ name: props.modelValue.split('/').pop(), url: props.modelValue }]
    : []
}

function handleRemove() {
  uploading.value = false
  fileList.value = []
  emit('update:modelValue', '')
}

function handleExceed() {
  ElMessage.warning('只能上传一张效果图')
}
</script>

<template>
  <div class="image-uploader">
    <el-upload
      ref="uploadRef"
      v-model:file-list="fileList"
      action="/api/upload"
      name="file"
      list-type="picture-card"
      accept=".jpg,.jpeg,.png,.webp"
      :headers="uploadHeaders"
      :limit="1"
      :before-upload="beforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      :on-remove="handleRemove"
      :on-exceed="handleExceed"
    >
      <span class="plus">{{ uploading ? '上传中' : '+' }}</span>
    </el-upload>
    <div class="upload-tip">支持 jpg、jpeg、png、webp，最大 5MB</div>
  </div>
</template>

<style scoped>
.image-uploader {
  width: 100%;
}

.plus {
  font-size: 28px;
  line-height: 1;
}

.upload-tip {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}
</style>
