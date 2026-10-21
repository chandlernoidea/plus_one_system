<script setup>
import { onMounted, ref } from 'vue'
import { listTodayPreps } from '../../api/prep'

const preps = ref([])
const loading = ref(false)

const statusConfig = {
  PENDING: { label: '待准备', type: 'warning' },
  READY: { label: '已就绪', type: 'success' }
}

function statusInfo(status) {
  return statusConfig[status] || { label: status || '未知', type: 'info' }
}

async function loadPreps() {
  loading.value = true
  try {
    const response = await listTodayPreps()
    preps.value = response.data.data || []
  } catch (error) {
    preps.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadPreps)
</script>

<template>
  <section class="prep-section">
    <div class="section-header">
      <div>
        <h2>今日物料准备</h2>
        <p>按今日已确认预约汇总款式、效果图和物料信息</p>
      </div>
      <el-button @click="loadPreps">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="preps" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <div class="expand-title">物料清单</div>
              <pre class="material-json">{{ row.materialJson || '暂无物料清单' }}</pre>
              <div class="expand-title">美甲师备注</div>
              <div class="note-text">{{ row.staffNote || '暂无备注' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="appointmentTime" label="预约时间" width="180" />
        <el-table-column prop="customerName" label="顾客" width="110" />
        <el-table-column prop="staffName" label="美甲师" width="120" />
        <el-table-column prop="serviceTypeName" label="项目类型" min-width="120" />
        <el-table-column prop="styleDescription" label="款式说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="效果图" width="100">
          <template #default="{ row }">
            <el-image
              v-if="row.effectImageUrl"
              :src="row.effectImageUrl"
              :preview-src-list="[row.effectImageUrl]"
              fit="cover"
              class="prep-thumb"
            />
            <span v-else class="empty-text">暂无</span>
          </template>
        </el-table-column>
        <el-table-column label="工单状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.prepStatus).type">
              {{ statusInfo(row.prepStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && preps.length === 0" description="今日暂无已确认预约的物料工单" />
    </el-card>
  </section>
</template>

<style scoped>
.prep-section {
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

.prep-thumb {
  width: 54px;
  height: 54px;
  border-radius: 6px;
}

.empty-text {
  color: #c0c4cc;
  font-size: 12px;
}

.expand-content {
  padding: 8px 24px 16px;
  color: #606266;
  font-size: 13px;
}

.expand-title {
  margin: 8px 0 6px;
  color: #303133;
  font-weight: 600;
}

.material-json {
  margin: 0;
  padding: 12px;
  max-height: 220px;
  overflow: auto;
  border-radius: 6px;
  background: #f5f7fa;
  color: #475569;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.note-text {
  white-space: pre-wrap;
}
</style>
