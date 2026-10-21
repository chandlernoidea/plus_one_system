<script setup>
import { onMounted, ref } from 'vue'
import { listReviews } from '../../api/review'

const reviews = ref([])
const loading = ref(false)

async function loadReviews() {
  loading.value = true
  try {
    const response = await listReviews()
    reviews.value = response.data.data || []
  } catch (error) {
    reviews.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadReviews)
</script>

<template>
  <section class="review-section">
    <div class="section-header">
      <div>
        <h2>服务评价</h2>
        <p>查看顾客对已完成服务的评价</p>
      </div>
      <el-button @click="loadReviews">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="reviews" stripe>
        <el-table-column prop="appointmentTime" label="预约时间" width="180" />
        <el-table-column prop="customerName" label="顾客" width="110" />
        <el-table-column prop="staffName" label="美甲师" width="120" />
        <el-table-column prop="serviceTypeName" label="项目类型" min-width="120" />
        <el-table-column label="评分" width="150">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled show-score />
          </template>
        </el-table-column>
        <el-table-column prop="content" label="评价内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="图片" width="100">
          <template #default="{ row }">
            <el-image
              v-if="row.imageUrls?.length"
              :src="row.imageUrls[0]"
              :preview-src-list="row.imageUrls"
              fit="cover"
              class="review-thumb"
            />
            <span v-else class="empty-text">暂无</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="评价时间" width="180" />
      </el-table>
      <el-empty v-if="!loading && reviews.length === 0" description="暂无评价" />
    </el-card>
  </section>
</template>

<style scoped>
.review-section {
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

.review-thumb {
  width: 54px;
  height: 54px;
  border-radius: 6px;
}

.empty-text {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
