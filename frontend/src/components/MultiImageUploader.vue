<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFile } from '../api/upload'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  limit: {
    type: Number,
    default: 3
  }
})

const emit = defineEmits(['update:modelValue'])

const fileInputRef = ref(null)
const uploading = ref(false)

function openPicker() {
  if (props.modelValue.length >= props.limit) {
    ElMessage.warning(`最多上传${props.limit}张图片`)
    return
  }
  fileInputRef.value?.click()
}

async function handleFiles(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) {
    return
  }

  const remaining = props.limit - props.modelValue.length
  if (remaining <= 0) {
    ElMessage.warning(`最多上传${props.limit}张图片`)
    return
  }

  const selected = files.slice(0, remaining)
  if (files.length > remaining) {
    ElMessage.warning(`最多上传${props.limit}张图片，已截取前${remaining}张`)
  }

  uploading.value = true
  const urls = [...props.modelValue]
  try {
    for (const file of selected) {
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
        ElMessage.error(`${file.name} 格式不支持`)
        continue
      }
      if (file.size > 5 * 1024 * 1024) {
        ElMessage.error(`${file.name} 超过5MB`)
        continue
      }
      try {
        const response = await uploadFile(file)
        if (response.data.code === 200 && response.data.data) {
          urls.push(response.data.data)
        }
      } catch (error) {
        // request.js 已统一提示，继续上传剩余图片。
      }
    }
    emit('update:modelValue', urls)
  } finally {
    uploading.value = false
  }
}

function removeImage(index) {
  const urls = [...props.modelValue]
  urls.splice(index, 1)
  emit('update:modelValue', urls)
}
</script>

<template>
  <div class="multi-uploader">
    <div class="image-grid">
      <div v-for="(url, index) in modelValue" :key="url" class="image-item">
        <el-image
          :src="url"
          :preview-src-list="modelValue"
          :initial-index="index"
          fit="cover"
          class="image"
        />
        <button type="button" class="remove-btn" @click="removeImage(index)">×</button>
      </div>
      <button
        v-if="modelValue.length < limit"
        type="button"
        class="upload-btn"
        :disabled="uploading"
        @click="openPicker"
      >
        <span>{{ uploading ? '上传中' : '+' }}</span>
      </button>
    </div>
    <input
      ref="fileInputRef"
      type="file"
      accept=".jpg,.jpeg,.png,.webp"
      multiple
      class="hidden-input"
      @change="handleFiles"
    />
    <div class="upload-tip">
      最多 {{ limit }} 张，支持 jpg、jpeg、png、webp，单张不超过 5MB
    </div>
  </div>
</template>

<style scoped>
.multi-uploader {
  width: 100%;
}

.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.image-item {
  position: relative;
  width: 92px;
  height: 92px;
}

.image {
  width: 92px;
  height: 92px;
  border-radius: 8px;
}

.remove-btn {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 50%;
  background: #f56c6c;
  color: #ffffff;
  cursor: pointer;
  line-height: 22px;
  text-align: center;
}

.upload-btn {
  width: 92px;
  height: 92px;
  border: 1px dashed #c0c4cc;
  border-radius: 8px;
  background: #fafafa;
  color: #909399;
  font-size: 28px;
  cursor: pointer;
}

.upload-btn:disabled {
  cursor: wait;
}

.hidden-input {
  display: none;
}

.upload-tip {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}
</style>
