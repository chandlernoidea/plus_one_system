<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listRules, saveRule } from '../../api/compensationRule'
import { listStaffs } from '../../api/staff'

const rules = ref([])
const staffs = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)

const emptyForm = () => ({
  staffId: null,
  baseSalary: 0,
  commissionPercent: 30,
  goodReviewBonus: 0,
  badReviewDeduction: 0,
  effectiveFrom: today()
})

const form = ref(emptyForm())

const configuredStaffIds = computed(() => new Set(rules.value.map((rule) => rule.staffId)))
const unconfiguredStaffs = computed(
  () => staffs.value.filter((staff) => !configuredStaffIds.value.has(staff.staffId))
)
const editingStaffName = computed(() => {
  const staff = staffs.value.find((item) => item.staffId === form.value.staffId)
  return staff ? staffName(staff) : `美甲师#${form.value.staffId}`
})

function today() {
  const date = new Date()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function money(value) {
  return Number(value || 0).toFixed(2)
}

function percent(value) {
  return `${(Number(value || 0) * 100).toFixed(2).replace(/\.?0+$/, '')}%`
}

function staffName(staff) {
  return staff.realName || staff.staffNo || `美甲师#${staff.staffId}`
}

async function loadData() {
  loading.value = true
  try {
    const [ruleResponse, staffResponse] = await Promise.all([listRules(), listStaffs()])
    rules.value = ruleResponse.data.data || []
    staffs.value = staffResponse.data.data || []
  } catch (error) {
    rules.value = []
  } finally {
    loading.value = false
  }
}

function openCreate(staff) {
  editing.value = false
  form.value = emptyForm()
  if (staff) {
    form.value.staffId = staff.staffId
  }
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  form.value = {
    staffId: row.staffId,
    baseSalary: Number(row.baseSalary || 0),
    commissionPercent: Number(row.commissionRate || 0) * 100,
    goodReviewBonus: Number(row.goodReviewBonus || 0),
    badReviewDeduction: Number(row.badReviewDeduction || 0),
    effectiveFrom: row.effectiveFrom || today()
  }
  dialogVisible.value = true
}

async function submit() {
  if (!form.value.staffId) {
    ElMessage.warning('请选择美甲师')
    return
  }
  if (form.value.commissionPercent < 0 || form.value.commissionPercent > 100) {
    ElMessage.warning('提成比例必须在0%到100%之间')
    return
  }

  saving.value = true
  try {
    await saveRule({
      staffId: form.value.staffId,
      baseSalary: Number(form.value.baseSalary || 0),
      commissionRate: Number((Number(form.value.commissionPercent || 0) / 100).toFixed(4)),
      goodReviewBonus: Number(form.value.goodReviewBonus || 0),
      badReviewDeduction: Number(form.value.badReviewDeduction || 0),
      effectiveFrom: form.value.effectiveFrom || today()
    })
    ElMessage.success('薪酬规则已保存')
    dialogVisible.value = false
    await loadData()
  } catch (error) {
    // 错误提示由 request.js 统一处理。
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <section class="rule-section">
    <div class="section-header">
      <div>
        <h2>薪酬规则</h2>
        <p>每个美甲师一条 ACTIVE 规则，修改后立即影响未结算月份</p>
      </div>
      <div class="header-actions">
        <el-button @click="loadData">刷新</el-button>
        <el-button type="primary" @click="openCreate()">新增规则</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rules" stripe>
        <el-table-column prop="staffName" label="美甲师" min-width="120" />
        <el-table-column label="底薪" width="110">
          <template #default="{ row }">¥{{ money(row.baseSalary) }}</template>
        </el-table-column>
        <el-table-column label="提成比例" width="110">
          <template #default="{ row }">{{ percent(row.commissionRate) }}</template>
        </el-table-column>
        <el-table-column label="好评奖励" width="120">
          <template #default="{ row }">¥{{ money(row.goodReviewBonus) }}</template>
        </el-table-column>
        <el-table-column label="差评扣减" width="120">
          <template #default="{ row }">¥{{ money(row.badReviewDeduction) }}</template>
        </el-table-column>
        <el-table-column prop="effectiveFrom" label="生效日期" width="120" />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && rules.length === 0" description="暂无薪酬规则" />
    </el-card>

    <el-card v-if="unconfiguredStaffs.length" shadow="never">
      <template #header>
        <span>未配置薪酬规则的美甲师</span>
      </template>
      <div class="staff-tags">
        <el-tag
          v-for="staff in unconfiguredStaffs"
          :key="staff.staffId"
          type="warning"
          effect="plain"
          class="staff-tag"
        >
          <span>{{ staffName(staff) }}</span>
          <el-button link type="primary" size="small" @click="openCreate(staff)">配置</el-button>
        </el-tag>
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑薪酬规则' : '新增薪酬规则'"
      width="520px"
    >
      <el-form label-width="100px">
        <el-form-item label="美甲师" required>
          <el-input v-if="editing" :model-value="editingStaffName" disabled />
          <el-select v-else v-model="form.staffId" placeholder="请选择美甲师" style="width: 100%">
            <el-option
              v-for="staff in staffs"
              :key="staff.staffId"
              :label="staffName(staff)"
              :value="staff.staffId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="底薪" required>
          <el-input-number v-model="form.baseSalary" :min="0" :precision="2" :step="100" />
        </el-form-item>
        <el-form-item label="提成比例" required>
          <el-input-number
            v-model="form.commissionPercent"
            :min="0"
            :max="100"
            :precision="2"
            :step="1"
          />
          <span class="unit">%（界面填 30 表示 30%）</span>
        </el-form-item>
        <el-form-item label="好评奖励" required>
          <el-input-number v-model="form.goodReviewBonus" :min="0" :precision="2" :step="5" />
        </el-form-item>
        <el-form-item label="差评扣减" required>
          <el-input-number v-model="form.badReviewDeduction" :min="0" :precision="2" :step="5" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker
            v-model="form.effectiveFrom"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="默认今天"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.rule-section {
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
  color: #303133;
  font-size: 18px;
}

.section-header p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.staff-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.staff-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
}

.unit {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 720px) {
  .section-header {
    flex-direction: column;
  }
}
</style>
