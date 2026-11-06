<script setup>
/**
 * 客户档案（P5）。
 *
 * 列表按最近到访倒序（后端排好）；搜索短号 / 昵称 / 手机后四位都能命中。
 * 新建时如果撞名，会提示「已有同名」，让人自己加个区分词——不替用户改名。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SoloLayout from './SoloLayout.vue'
import SoloSheet from '../../components/solo/SoloSheet.vue'
import * as api from '../../api/solo'
import { toKey } from '../../utils/date'

const router = useRouter()

const keyword = ref('')
const clients = ref([])
const loading = ref(false)
const createVisible = ref(false)
const submitting = ref(false)

const form = ref(emptyForm())
const sameName = ref([])

function emptyForm() {
  return { nickname: '', phoneTail: '', source: '', colorNotes: '', remark: '' }
}

let searchTimer = null
let nameTimer = null

async function load() {
  loading.value = true
  try {
    const response = await api.searchClients(keyword.value.trim())
    clients.value = response.data.data || []
  } catch (error) {
    clients.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(load, 250)
})

watch(
  () => form.value.nickname,
  (value) => {
    clearTimeout(nameTimer)
    const nickname = value.trim()
    if (!nickname) {
      sameName.value = []
      return
    }
    nameTimer = setTimeout(async () => {
      try {
        const response = await api.checkClientName(nickname)
        sameName.value = response.data.data || []
      } catch (error) {
        sameName.value = []
      }
    }, 400)
  }
)

const totalText = computed(() =>
  keyword.value.trim() ? `找到 ${clients.value.length} 位` : `${clients.value.length} 位客户`
)

function openCreate() {
  form.value = emptyForm()
  sameName.value = []
  createVisible.value = true
}

async function submit() {
  if (!form.value.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  submitting.value = true
  try {
    const response = await api.createClient({
      nickname: form.value.nickname.trim(),
      phoneTail: form.value.phoneTail.trim() || undefined,
      source: form.value.source.trim() || undefined,
      colorNotes: form.value.colorNotes.trim() || undefined,
      remark: form.value.remark.trim() || undefined
    })
    const created = response.data.data
    ElMessage.success(`已建客户 ${created.code}`)
    createVisible.value = false
    await load()
    router.push(`/solo/clients/${created.id}`)
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    submitting.value = false
  }
}

function openDetail(client) {
  router.push(`/solo/clients/${client.id}`)
}

function lastVisitText(client) {
  if (!client.lastVisitDate) {
    return '还没到过店'
  }
  return client.lastVisitDate === toKey(new Date()) ? '今天来过' : `最近 ${client.lastVisitDate}`
}
</script>

<template>
  <SoloLayout>
    <header class="lede">
      <span class="eyebrow">SOLO · CLIENTS</span>
      <h1 class="lede-title">{{ totalText }}</h1>
      <p class="lede-note">按最近到访排序 · 短号 / 昵称 / 手机后四位都能搜</p>
    </header>

    <div class="toolbar">
      <input
        v-model="keyword"
        class="search"
        type="search"
        placeholder="搜客户…（#C001 / 阿岚 / 8821）"
      />
      <button type="button" class="create-btn" @click="openCreate">＋ 新建</button>
    </div>

    <p v-if="loading" class="empty-line">读取中…</p>

    <div v-else-if="!clients.length" class="empty-note">
      {{ keyword ? '没搜到，换个短号或后四位试试。' : '还没有客户。' }}
      <em>建第一个客户后，建单时就能直接搜到。</em>
    </div>

    <div v-else class="clients">
      <button
        v-for="client in clients"
        :key="client.id"
        type="button"
        class="client"
        @click="openDetail(client)"
      >
        <span class="client-row">
          <span class="code mono">{{ client.code }}</span>
          <span class="name">{{ client.nickname }}</span>
          <span v-if="client.phoneTail" class="tail mono">{{ client.phoneTail }}</span>
        </span>
        <span class="client-meta">
          <span>{{ lastVisitText(client) }}</span>
          <span v-if="client.source" class="dot">·</span>
          <span v-if="client.source">{{ client.source }}</span>
        </span>
        <span v-if="client.colorNotes" class="client-color">色号 {{ client.colorNotes }}</span>
      </button>
    </div>

    <SoloSheet
      :visible="createVisible"
      title="新建客户"
      note="短号自动生成，建完就能拿去建单"
      @update:visible="createVisible = $event"
    >
      <section class="field">
        <div class="field-head"><span class="field-label">昵称</span></div>
        <input v-model="form.nickname" class="input" type="text" placeholder="阿岚" />
        <p v-if="sameName.length" class="dup">
          已有同名客户 {{ sameName.map((item) => item.code).join('、') }}，建议加个区分词（如「阿岚-长甲」）
        </p>
      </section>

      <section class="field">
        <div class="field-head">
          <span class="field-label">手机后四位</span>
          <span class="field-hint">选填</span>
        </div>
        <input v-model="form.phoneTail" class="input" type="text" maxlength="4" placeholder="8821" />
      </section>

      <section class="field">
        <div class="field-head"><span class="field-label">来源</span></div>
        <input v-model="form.source" class="input" type="text" placeholder="小红书 / 朋友介绍 / 路过" />
      </section>

      <section class="field">
        <div class="field-head">
          <span class="field-label">常用色号</span>
          <span class="field-hint">选填</span>
        </div>
        <input v-model="form.colorNotes" class="input" type="text" placeholder="#E03 裸粉，怕深色" />
      </section>

      <section class="field">
        <div class="field-head"><span class="field-label">备注</span></div>
        <input v-model="form.remark" class="input" type="text" placeholder="到店当面确认的偏好" />
      </section>

      <template #footer>
        <button type="button" class="btn" @click="createVisible = false">取消</button>
        <button type="button" class="btn btn-primary wide" :disabled="submitting" @click="submit">
          {{ submitting ? '保存中…' : '保存' }}
        </button>
      </template>
    </SoloSheet>
  </SoloLayout>
</template>

<style scoped>
.lede {
  margin-bottom: var(--s3);
}

.lede-title {
  margin-top: 2px;
  font-family: var(--font-display);
  font-size: 23px;
  font-weight: 400;
  letter-spacing: 0.01em;
  line-height: 1.25;
}

.lede-note {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 11px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--s2);
  margin-bottom: var(--s3);
}

.search {
  flex: 1 1 auto;
  min-width: 0;
  padding: 8px 0;
  border: 0;
  border-bottom: 1px solid var(--line-strong);
  background: transparent;
  color: var(--ink);
  font-family: var(--font-ui);
  font-size: 14px;
  outline: none;
}

.search:focus {
  border-bottom-color: var(--lacquer);
}

.create-btn {
  flex: none;
  padding: 6px 12px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.clients {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--s2);
}

.client {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: var(--s3);
  border: 1px solid var(--line);
  border-left: 2px solid var(--line-strong);
  background: var(--paper-raised);
  color: var(--ink);
  cursor: pointer;
  text-align: left;
  transition: border-color var(--dur-1) var(--ease);
}

.client:hover {
  border-left-color: var(--lacquer);
}

.client-row {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
  min-width: 0;
}

.code {
  flex: none;
  padding: 1px 4px;
  border: 1px solid var(--line);
  color: var(--ink-3);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.name {
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tail {
  flex: none;
  margin-left: auto;
  color: var(--ink-4);
  font-size: 10px;
}

.client-meta {
  display: flex;
  gap: 4px;
  color: var(--ink-3);
  font-size: 10px;
}

.dot {
  color: var(--ink-4);
}

.client-color {
  overflow: hidden;
  color: var(--ink-3);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-line {
  padding: var(--s3) 0;
  color: var(--ink-3);
  font-size: 11px;
}

/* 表单 */

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

.field-hint {
  color: var(--ink-4);
  font-size: 10px;
}

.input {
  width: 100%;
  min-width: 0;
  padding: 8px 0;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font-family: var(--font-ui);
  font-size: 14px;
  outline: none;
}

.input:focus {
  border-bottom-color: var(--ink);
}

.dup {
  margin-top: var(--s2);
  padding: 4px 8px;
  border-left: 2px solid var(--ochre);
  background: rgba(138, 101, 38, 0.06);
  color: var(--ink-2);
  font-size: 11px;
  line-height: 1.6;
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

/* 电脑：卡片多列 */
@media (min-width: 900px) {
  .clients {
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: var(--s3);
  }

  .client {
    padding: var(--s3) var(--s4);
  }
}
</style>
