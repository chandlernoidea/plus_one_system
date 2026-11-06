<script setup>
/**
 * CSV 导入客户：贴着「先预览再入库」的顺序做。
 *
 * 手机上从微信复制一段表格就能导，不用先存成文件；
 * 同名行标黄并提示，导完给短号区间，方便回找。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import SoloSheet from './SoloSheet.vue'
import * as api from '../../api/solo'

const props = defineProps({
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'imported'])

const csv = ref('')
const preview = ref(null)
const parsing = ref(false)
const importing = ref(false)

const sample = `昵称,手机后四位,来源,备注
阿岚,8821,小红书,怕深色
小满,3090,朋友介绍,喜欢猫眼`

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      csv.value = ''
      preview.value = null
    }
  }
)

const previewRows = computed(() => (preview.value?.rows || []).slice(0, 5))

async function parse() {
  if (!csv.value.trim()) {
    ElMessage.warning('先贴一段 CSV')
    return
  }
  parsing.value = true
  try {
    const response = await api.previewClientImport(csv.value)
    preview.value = response.data.data
    if (!preview.value.total) {
      ElMessage.warning('没解析出有效行，检查一下格式')
    }
  } catch (error) {
    preview.value = null
  } finally {
    parsing.value = false
  }
}

async function confirmImport() {
  importing.value = true
  try {
    const response = await api.importClients(csv.value)
    const result = response.data.data
    ElMessage.success(`导入 ${result.created} 位客户${result.skipped ? `，跳过 ${result.skipped} 行` : ''}`)
    emit('imported', result)
    emit('update:visible', false)
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    importing.value = false
  }
}

function fillSample() {
  csv.value = sample
  preview.value = null
}
</script>

<template>
  <SoloSheet
    :visible="visible"
    title="导入客户"
    note="列顺序：昵称, 手机后四位, 来源, 备注 · 只导客户，不导历史预约"
    @update:visible="emit('update:visible', $event)"
  >
    <section class="field">
      <div class="field-head">
        <span class="field-label">CSV 内容</span>
        <button type="button" class="text-btn" @click="fillSample">填个示例</button>
      </div>
      <textarea v-model="csv" class="textarea" rows="6" placeholder="昵称,手机后四位,来源,备注" />
      <p class="hint">从 Excel / 微信复制过来就行，第一行是表头会自动跳过。</p>
    </section>

    <p v-if="preview" class="summary">
      共 {{ preview.total }} 行
      <span v-if="preview.duplicates" class="dup-count">· {{ preview.duplicates }} 行同名</span>
    </p>

    <table v-if="preview" class="table">
      <thead>
        <tr>
          <th>昵称</th>
          <th>后四位</th>
          <th>来源</th>
          <th>备注</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in previewRows" :key="row.line" :class="{ 'is-dup': row.duplicate, 'is-bad': !row.valid }">
          <td>{{ row.nickname || '—' }}</td>
          <td class="mono">{{ row.phoneTail || '—' }}</td>
          <td>{{ row.source || '—' }}</td>
          <td class="note">{{ row.note || row.remark || '' }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="preview && preview.total > 5" class="hint">只显示前 5 行，导入时全部 {{ preview.total }} 行都会处理。</p>

    <template #footer>
      <button type="button" class="btn" @click="emit('update:visible', false)">取消</button>
      <button type="button" class="btn" :disabled="parsing" @click="parse">
        {{ parsing ? '解析中…' : '预览' }}
      </button>
      <button type="button" class="btn btn-primary wide" :disabled="!preview?.total || importing" @click="confirmImport">
        {{ importing ? '导入中…' : `确认导入${preview?.total ? ' ' + preview.total + ' 行' : ''}` }}
      </button>
    </template>
  </SoloSheet>
</template>

<style scoped>
.field {
  margin-bottom: var(--s4);
}

.field-head {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  margin-bottom: var(--s2);
}

.field-label {
  font-family: var(--font-display);
  font-size: 14px;
  letter-spacing: 0.06em;
}

.text-btn {
  margin-left: auto;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink-4);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 10px;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.textarea {
  width: 100%;
  min-height: 120px;
  padding: var(--s2);
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.7;
  outline: none;
  resize: vertical;
}

.textarea:focus {
  border-color: var(--ink);
}

.hint {
  margin-top: 4px;
  color: var(--ink-4);
  font-size: 10px;
}

.summary {
  margin-bottom: var(--s2);
  color: var(--ink-2);
  font-size: 12px;
}

.dup-count {
  color: var(--ochre);
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}

.table th {
  padding: 4px 6px;
  border-bottom: 1px solid var(--line);
  color: var(--ink-4);
  font-weight: 400;
  text-align: left;
}

.table td {
  padding: 5px 6px;
  border-bottom: 1px solid var(--line-soft);
  color: var(--ink-2);
}

.table tr.is-dup td {
  background: rgba(138, 101, 38, 0.08);
}

.table tr.is-bad td {
  color: var(--lacquer);
}

.note {
  color: var(--ink-4);
  font-size: 10px;
}

.btn {
  padding: 6px 14px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
  letter-spacing: 0.06em;
}

.btn-primary {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.wide {
  flex: 1 1 auto;
}
</style>
