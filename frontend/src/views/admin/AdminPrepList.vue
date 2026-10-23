<script setup>
/**
 * 管理员 · 物料准备。
 *
 * 数据来自「今天起 30 天」内已确认 / 服务中 / 改约中的预约工单，
 * 用「每 3 天一段」的日期段切分，默认只看今天所在段，避免一屏几十行。
 *
 * 两种操作的区别：
 * - 编辑：改款式 / 效果图 / 物料 JSON / 备注，保存后由后端按字段完整度
 *   重算状态（三项齐全才是「已就绪」）；
 * - 确认已备好：管理员直接置为 READY，不要求字段齐全，可撤回。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmPrep, listUpcomingPreps, unconfirmPrep, updatePrep } from '../../api/prep'
import ImageUploader from '../../components/ImageUploader.vue'
import DateBlockBar from '../../components/DateBlockBar.vue'
import { useDateBlockSelection } from '../../composables/useDateBlockSelection'

const PREP_DAYS = 30

const preps = ref([])
const loading = ref(false)
const actionId = ref(null)

const editVisible = ref(false)
const editTarget = ref(null)
const saving = ref(false)
const editForm = reactive({
  styleDescription: '',
  effectImageUrl: '',
  materialJson: '',
  staffNote: ''
})

const statusConfig = {
  PENDING: { label: '待准备', type: 'warning' },
  READY: { label: '已就绪', type: 'success' }
}

// 工单按预约时间归属到「每 3 天一段」，默认只看今天所在段
const dayOf = (row) => String(row.appointmentTime || '').slice(0, 10)
const {
  blockKey,
  blocks,
  activeBlock,
  visible: visiblePreps,
  pick,
  applyDefault
} = useDateBlockSelection(preps, dayOf)

const readyCount = computed(
  () => visiblePreps.value.filter((item) => item.prepStatus === 'READY').length
)

const heading = computed(() =>
  activeBlock.value ? `物料准备 · ${activeBlock.value.label}` : '物料准备'
)

const subtitle = computed(() => {
  if (activeBlock.value) {
    return `未来 30 天内的工单，当前只看 ${activeBlock.value.label}；点上方日期段可切换`
  }
  if (preps.value.length === 0) {
    return '未来 30 天内暂无已确认预约的工单'
  }
  return `未来 30 天内的工单共 ${preps.value.length} 条；点上方日期段可只看三天`
})

function statusInfo(status) {
  return statusConfig[status] || { label: status || '未知', type: 'info' }
}

/** 物料清单是一段自由文本，表格里给一句话摘要，完整内容仍在展开行 */
function materialSummary(text) {
  if (!text) {
    return '未填写'
  }
  const compact = String(text)
    .replace(/[{}"[\]]/g, ' ')
    .replace(/[,:，：]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return compact.length > 24 ? `${compact.slice(0, 24)}…` : compact
}

function updatedText(value) {
  if (!value) {
    return '—'
  }
  return String(value).slice(5, 16)
}

const materialHint = computed(() => {
  const text = editForm.materialJson.trim()
  if (!text) {
    return '可留空；填写后美甲师与管理员都能看到'
  }
  try {
    JSON.parse(text)
    return 'JSON 格式有效'
  } catch (error) {
    return '不是合法 JSON，保存后按原文展示'
  }
})

function openEdit(row) {
  editTarget.value = row
  editForm.styleDescription = row.styleDescription || ''
  editForm.effectImageUrl = row.effectImageUrl || ''
  editForm.materialJson = row.materialJson || ''
  editForm.staffNote = row.staffNote || ''
  editVisible.value = true
}

async function saveEdit() {
  if (!editTarget.value) {
    return
  }
  saving.value = true
  try {
    await updatePrep(editTarget.value.appointmentId, {
      styleDescription: editForm.styleDescription,
      effectImageUrl: editForm.effectImageUrl,
      materialJson: editForm.materialJson,
      staffNote: editForm.staffNote
    })
    ElMessage.success('物料已保存')
    editVisible.value = false
    await loadPreps()
  } catch (error) {
    // 错误提示由 request.js 统一处理
  } finally {
    saving.value = false
  }
}

async function handleConfirm(row) {
  actionId.value = row.appointmentId
  try {
    await confirmPrep(row.appointmentId)
    ElMessage.success('已确认物料备好')
    await loadPreps()
  } catch (error) {
    // 错误提示由 request.js 统一处理
  } finally {
    actionId.value = null
  }
}

async function handleUnconfirm(row) {
  actionId.value = row.appointmentId
  try {
    await unconfirmPrep(row.appointmentId)
    ElMessage.success('已撤回确认')
    await loadPreps()
  } catch (error) {
    // 错误提示由 request.js 统一处理
  } finally {
    actionId.value = null
  }
}

async function loadPreps() {
  loading.value = true
  try {
    const response = await listUpcomingPreps(PREP_DAYS)
    preps.value = response.data.data || []
    applyDefault()
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
        <h2>{{ heading }}</h2>
        <p>{{ subtitle }}</p>
      </div>
      <div class="header-actions">
        <span class="list-count mono">{{ visiblePreps.length }} 条 · 就绪 {{ readyCount }}</span>
        <el-button @click="loadPreps">刷新</el-button>
      </div>
    </div>

    <DateBlockBar
      :blocks="blocks"
      :model-value="blockKey"
      :total="preps.length"
      @update:model-value="pick"
    />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="visiblePreps" stripe>
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
        <el-table-column prop="appointmentTime" label="预约时间" width="170" />
        <el-table-column prop="customerName" label="顾客" width="100" />
        <el-table-column prop="staffName" label="美甲师" width="110" />
        <el-table-column prop="serviceTypeName" label="项目类型" min-width="110" />
        <el-table-column
          prop="styleDescription"
          label="款式说明"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column label="物料" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'empty-text': !row.materialJson }">
              {{ materialSummary(row.materialJson) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="效果图" width="86">
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
        <el-table-column label="更新时间" width="116">
          <template #default="{ row }">
            <span class="mono updated-at">{{ updatedText(row.updatedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工单状态" width="96">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.prepStatus).type">
              {{ statusInfo(row.prepStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="156" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.prepStatus !== 'READY'"
              link
              type="primary"
              :loading="actionId === row.appointmentId"
              @click="handleConfirm(row)"
            >
              确认已备好
            </el-button>
            <el-button
              v-else
              link
              :loading="actionId === row.appointmentId"
              @click="handleUnconfirm(row)"
            >
              撤回
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!loading && visiblePreps.length === 0"
        :description="activeBlock ? `${activeBlock.label} 没有物料工单` : '暂无物料工单'"
      />
    </el-card>

    <el-dialog v-model="editVisible" title="编辑物料工单" width="560px">
      <div v-if="editTarget" class="edit-meta">
        <span class="mono">{{ editTarget.appointmentTime }}</span>
        <span>{{ editTarget.customerName }}</span>
        <span>{{ editTarget.staffName }}</span>
        <span>{{ editTarget.serviceTypeName }}</span>
      </div>

      <el-form label-width="88px" class="edit-form">
        <el-form-item label="款式说明">
          <el-input
            v-model="editForm.styleDescription"
            type="textarea"
            :rows="2"
            maxlength="1000"
            show-word-limit
            placeholder="描述款式、颜色、风格"
          />
        </el-form-item>
        <el-form-item label="效果图">
          <ImageUploader v-model="editForm.effectImageUrl" />
        </el-form-item>
        <el-form-item label="物料清单">
          <el-input
            v-model="editForm.materialJson"
            type="textarea"
            :rows="5"
            placeholder='可留空，支持 JSON，例如 {"色号":"N01","饰品":"水钻"}'
          />
          <div class="form-hint">{{ materialHint }}</div>
        </el-form-item>
        <el-form-item label="美甲师备注">
          <el-input
            v-model="editForm.staffNote"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="内部备注"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.list-count {
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.12em;
  white-space: nowrap;
}

.prep-thumb {
  width: 48px;
  height: 48px;
}

.edit-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s3);
  margin-bottom: var(--s4);
  padding-bottom: var(--s3);
  border-bottom: 1px solid var(--line);
  color: var(--ink-3);
  font-size: 12px;
}

.edit-form {
  margin-top: var(--s2);
}

.form-hint {
  margin-top: 4px;
  color: var(--ink-4);
  font-size: 11px;
  line-height: 1.5;
}

.empty-text {
  color: var(--ink-4);
  font-size: 12px;
}

.updated-at {
  color: var(--ink-3);
  font-size: 11px;
}

.expand-content {
  padding: 8px 24px 16px;
  color: var(--ink-2);
  font-size: 13px;
}

.expand-title {
  margin: 8px 0 6px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 500;
}

.material-json {
  margin: 0;
  padding: 12px;
  max-height: 220px;
  overflow: auto;
  border: 1px solid var(--line);
  background: var(--paper);
  color: var(--ink-2);
  font-family: var(--font-mono);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.note-text {
  white-space: pre-wrap;
}

@media (max-width: 960px) {
  .section-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
