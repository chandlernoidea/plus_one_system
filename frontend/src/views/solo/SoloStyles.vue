<script setup>
/**
 * 款式库（P5，二级入口）。
 *
 * 两个用途：建单时选款，做完后当作品集发小红书。
 * 导出走 canvas 现场拼图（3×4、九宫格、带水印），不引第三方库，也不用后端出图。
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SoloLayout from './SoloLayout.vue'
import SoloSheet from '../../components/solo/SoloSheet.vue'
import ImageUploader from '../../components/ImageUploader.vue'
import * as api from '../../api/solo'
import { formatMoney } from '../../utils/date'

const styles = ref([])
const loading = ref(false)
const activeTag = ref('')

const editorVisible = ref(false)
const editing = ref(null)
const submitting = ref(false)
const form = ref(emptyForm())
const materialText = ref('')

const exportVisible = ref(false)
const exportTags = ref([])
const exportPreview = ref('')
const building = ref(false)
const exportNote = ref('')

function emptyForm() {
  return { name: '', coverUrl: '', durationMin: 60, price: 0, tags: '' }
}

async function load() {
  loading.value = true
  try {
    const response = await api.fetchStyles({})
    styles.value = response.data.data || []
  } catch (error) {
    styles.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

const allTags = computed(() => {
  const set = new Set()
  styles.value.forEach((style) => {
    ;(style.tags || '')
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean)
      .forEach((tag) => set.add(tag))
  })
  return [...set]
})

const visibleStyles = computed(() =>
  activeTag.value ? styles.value.filter((style) => (style.tags || '').includes(activeTag.value)) : styles.value
)

const enabledCount = computed(() => styles.value.filter((style) => style.enabled === 1).length)

function coverOf(style) {
  return style.coverUrl || ''
}

function openCreate() {
  editing.value = null
  form.value = emptyForm()
  materialText.value = ''
  editorVisible.value = true
}

function openEdit(style) {
  editing.value = style
  form.value = {
    name: style.name,
    coverUrl: style.coverUrl || '',
    durationMin: style.durationMin,
    price: style.price,
    tags: style.tags || ''
  }
  materialText.value = materialToText(style.materialJson)
  editorVisible.value = true
}

/** 材料清单：界面上是「每行一项：底胶 1」，存库仍是 JSON 数组。 */
function materialToText(json) {
  if (!json) {
    return ''
  }
  try {
    const list = JSON.parse(json)
    return list.map((item) => `${item.name} ${item.qty ?? 1}`).join('\n')
  } catch (error) {
    return ''
  }
}

function textToMaterial(text) {
  const lines = text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  if (!lines.length) {
    return null
  }
  const list = lines.map((line) => {
    const matched = line.match(/^(.*?)\s+([\d.]+)$/)
    return matched
      ? { name: matched[1].trim(), qty: Number(matched[2]) }
      : { name: line, qty: 1 }
  })
  return JSON.stringify(list)
}

async function submitStyle() {
  if (!form.value.name.trim()) {
    ElMessage.warning('款式名称不能为空')
    return
  }
  submitting.value = true
  const payload = {
    name: form.value.name.trim(),
    coverUrl: form.value.coverUrl || undefined,
    durationMin: Number(form.value.durationMin) || 60,
    price: Number(form.value.price) || 0,
    tags: form.value.tags.trim() || undefined,
    materialJson: textToMaterial(materialText.value),
    enabled: editing.value ? editing.value.enabled : 1
  }
  try {
    if (editing.value) {
      await api.updateStyle(editing.value.id, payload)
      ElMessage.success('已更新')
    } else {
      await api.createStyle(payload)
      ElMessage.success('已新建')
    }
    editorVisible.value = false
    await load()
  } catch (error) {
    // 提示已由拦截器统一给出
  } finally {
    submitting.value = false
  }
}

async function disableCurrent() {
  if (!editing.value) {
    return
  }
  try {
    await api.disableStyle(editing.value.id)
    ElMessage.success('已下架，历史单不受影响')
    editorVisible.value = false
    await load()
  } catch (error) {
    // 提示已由拦截器统一给出
  }
}

// ---------------------------------------------------------------- 导出案例图

const EXPORT_WIDTH = 1080
const EXPORT_HEIGHT = 1440
const PAD = 48
const GAP = 16

function openExport() {
  exportTags.value = activeTag.value ? [activeTag.value] : []
  exportPreview.value = ''
  exportNote.value = ''
  exportVisible.value = true
  nextTick(buildPreview)
}

const exportList = computed(() => {
  const picked = exportTags.value.length
    ? styles.value.filter((style) => exportTags.value.some((tag) => (style.tags || '').includes(tag)))
    : styles.value.filter((style) => style.enabled === 1)
  return picked.filter((style) => style.enabled === 1).slice(0, 9)
})

function toggleExportTag(tag) {
  const index = exportTags.value.indexOf(tag)
  if (index >= 0) {
    exportTags.value.splice(index, 1)
  } else {
    exportTags.value.push(tag)
  }
}

async function buildPreview() {
  building.value = true
  exportNote.value = ''
  try {
    const canvas = await renderCanvas(exportList.value)
    exportPreview.value = canvas.toDataURL('image/png')
    if (exportList.value.length < 9) {
      exportNote.value = `选了 ${exportList.value.length} 款，其余格子留白；满 9 款最好看。`
    }
  } catch (error) {
    exportNote.value = '生成失败，换几款再试。'
  } finally {
    building.value = false
  }
}

async function renderCanvas(list) {
  const canvas = document.createElement('canvas')
  canvas.width = EXPORT_WIDTH
  canvas.height = EXPORT_HEIGHT
  const ctx = canvas.getContext('2d')

  // 纸面底色
  ctx.fillStyle = '#f3eee4'
  ctx.fillRect(0, 0, EXPORT_WIDTH, EXPORT_HEIGHT)

  // 抬头
  ctx.fillStyle = '#17130f'
  ctx.font = '500 44px "Noto Serif CJK SC", "Songti SC", serif'
  ctx.fillText('款式案例', PAD, PAD + 44)
  ctx.fillStyle = '#7b7064'
  ctx.font = '22px "Noto Sans CJK SC", "PingFang SC", sans-serif'
  const tagText = exportTags.value.length ? exportTags.value.join(' · ') : '全部款式'
  ctx.fillText(`${tagText} · ${new Date().toLocaleDateString('zh-CN')}`, PAD, PAD + 82)

  // 网格
  const top = PAD + 120
  const cellWidth = (EXPORT_WIDTH - PAD * 2 - GAP * 2) / 3
  const cellHeight = (EXPORT_HEIGHT - top - PAD - 40 - GAP * 2) / 3

  for (let index = 0; index < 9; index += 1) {
    const row = Math.floor(index / 3)
    const col = index % 3
    const x = PAD + col * (cellWidth + GAP)
    const y = top + row * (cellHeight + GAP)
    const style = list[index]

    ctx.fillStyle = '#faf6ed'
    ctx.fillRect(x, y, cellWidth, cellHeight)
    ctx.strokeStyle = 'rgba(23,19,15,0.14)'
    ctx.lineWidth = 1
    ctx.strokeRect(x, y, cellWidth, cellHeight)

    if (!style) {
      continue
    }

    const imageHeight = cellHeight - 78
    if (style.coverUrl) {
      const image = await loadImage(style.coverUrl)
      if (image) {
        drawCover(ctx, image, x, y, cellWidth, imageHeight)
      } else {
        drawFallback(ctx, style, x, y, cellWidth, imageHeight)
      }
    } else {
      drawFallback(ctx, style, x, y, cellWidth, imageHeight)
    }

    ctx.fillStyle = '#17130f'
    ctx.font = '26px "Noto Serif CJK SC", "Songti SC", serif'
    ctx.fillText(clipText(ctx, style.name, cellWidth - 24), x + 12, y + imageHeight + 34)

    ctx.fillStyle = '#7b7064'
    ctx.font = '18px "Noto Sans CJK SC", "PingFang SC", sans-serif'
    ctx.fillText(
      `${style.durationMin} 分钟 · ${formatMoney(style.price, { sign: true })}`,
      x + 12,
      y + imageHeight + 62
    )
  }

  // 水印：斜向浅色，既不挡图也能防拿去直接用
  ctx.save()
  ctx.globalAlpha = 0.08
  ctx.fillStyle = '#17130f'
  ctx.font = '500 40px "Noto Serif CJK SC", "Songti SC", serif'
  ctx.translate(EXPORT_WIDTH / 2, EXPORT_HEIGHT / 2)
  ctx.rotate(-Math.PI / 9)
  for (let i = -4; i <= 4; i += 1) {
    ctx.fillText('指间工作室 · SOLO', -320, i * 150)
  }
  ctx.restore()

  // 页脚
  ctx.fillStyle = '#a5998a'
  ctx.font = '18px "Noto Sans CJK SC", "PingFang SC", sans-serif'
  ctx.fillText('指间工作室 · 单人美甲工作室', PAD, EXPORT_HEIGHT - PAD)

  return canvas
}

function drawCover(ctx, image, x, y, width, height) {
  const scale = Math.max(width / image.width, height / image.height)
  const drawWidth = image.width * scale
  const drawHeight = image.height * scale
  ctx.save()
  ctx.beginPath()
  ctx.rect(x, y, width, height)
  ctx.clip()
  ctx.drawImage(image, x + (width - drawWidth) / 2, y + (height - drawHeight) / 2, drawWidth, drawHeight)
  ctx.restore()
}

function drawFallback(ctx, style, x, y, width, height) {
  ctx.fillStyle = '#eae2d4'
  ctx.fillRect(x, y, width, height)
  ctx.fillStyle = '#a5998a'
  ctx.font = '500 64px "Noto Serif CJK SC", "Songti SC", serif'
  ctx.textAlign = 'center'
  ctx.fillText(style.name.slice(0, 1), x + width / 2, y + height / 2 + 20)
  ctx.textAlign = 'left'
}

function clipText(ctx, text, maxWidth) {
  if (ctx.measureText(text).width <= maxWidth) {
    return text
  }
  let clipped = text
  while (clipped.length > 1 && ctx.measureText(`${clipped}…`).width > maxWidth) {
    clipped = clipped.slice(0, -1)
  }
  return `${clipped}…`
}

function loadImage(url) {
  return new Promise((resolve) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => resolve(null)
    image.src = url
  })
}

function saveImage() {
  if (!exportPreview.value) {
    return
  }
  const link = document.createElement('a')
  link.href = exportPreview.value
  link.download = `款式案例-${exportTags.value.join('-') || '全部'}-${Date.now()}.png`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  ElMessage.success('已保存，可直接发小红书')
}

const exporting = computed(() => exportList.value)
</script>

<template>
  <SoloLayout back-to="/solo/today" back-label="今天">
    <header class="lede">
      <span class="eyebrow">SOLO · STYLES</span>
      <h1 class="lede-title">{{ styles.length }} 款 · {{ enabledCount }} 款在架</h1>
      <p class="lede-note">兼作品集 · 按标签筛 · 可导出竖版九宫格案例图</p>
    </header>

    <div class="toolbar">
      <div class="tags">
        <button type="button" class="tag" :class="{ 'is-active': !activeTag }" @click="activeTag = ''">
          全部
        </button>
        <button
          v-for="tag in allTags"
          :key="tag"
          type="button"
          class="tag"
          :class="{ 'is-active': activeTag === tag }"
          @click="activeTag = activeTag === tag ? '' : tag"
        >
          {{ tag }}
        </button>
      </div>
      <div class="actions">
        <button type="button" class="ghost" @click="openExport">导出案例图</button>
        <button type="button" class="create-btn" @click="openCreate">＋ 新建</button>
      </div>
    </div>

    <p v-if="loading" class="empty-line">读取中…</p>

    <div v-else-if="!visibleStyles.length" class="empty-note">
      {{ activeTag ? `「${activeTag}」下还没有款式。` : '还没有款式。' }}
      <em>建几个常做的款，建单时就能直接选。</em>
    </div>

    <div v-else class="grid">
      <button
        v-for="style in visibleStyles"
        :key="style.id"
        type="button"
        class="card"
        :class="{ 'is-off': style.enabled !== 1 }"
        @click="openEdit(style)"
      >
        <span class="cover">
          <img v-if="coverOf(style)" :src="coverOf(style)" :alt="style.name" />
          <span v-else class="cover-fallback">{{ style.name.slice(0, 1) }}</span>
          <span v-if="style.enabled !== 1" class="off-flag">已下架</span>
        </span>
        <span class="body">
          <span class="name">{{ style.name }}</span>
          <span class="meta mono">{{ style.durationMin }}′ · {{ formatMoney(style.price, { sign: true }) }}</span>
          <span v-if="style.tags" class="tag-line">{{ style.tags }}</span>
        </span>
      </button>
    </div>

    <!-- 新建 / 编辑 -->
    <SoloSheet
      :visible="editorVisible"
      :title="editing ? '编辑款式' : '新建款式'"
      note="时长与价格会在建单时带出，可再改"
      @update:visible="editorVisible = $event"
    >
      <section class="field">
        <div class="field-head"><span class="field-label">名称</span></div>
        <input v-model="form.name" class="input" type="text" placeholder="法式裸粉" />
      </section>

      <section class="field">
        <div class="field-head">
          <span class="field-label">封面</span>
          <span class="field-hint">可选，导出案例图会用到</span>
        </div>
        <ImageUploader v-model="form.coverUrl" />
      </section>

      <section class="field row">
        <div class="half">
          <div class="field-head"><span class="field-label">标准时长</span><span class="field-hint">分钟</span></div>
          <input v-model.number="form.durationMin" class="input" type="number" min="15" max="720" step="15" />
        </div>
        <div class="half">
          <div class="field-head"><span class="field-label">标准价</span><span class="field-hint">元</span></div>
          <input v-model.number="form.price" class="input" type="number" min="0" step="10" />
        </div>
      </section>

      <section class="field">
        <div class="field-head">
          <span class="field-label">标签</span>
          <span class="field-hint">英文逗号分隔，如 法式,裸色</span>
        </div>
        <input v-model="form.tags" class="input" type="text" placeholder="法式,裸色,短甲" />
      </section>

      <section class="field">
        <div class="field-head">
          <span class="field-label">材料清单</span>
          <span class="field-hint">每行一项：底胶 1</span>
        </div>
        <textarea v-model="materialText" class="textarea" rows="4" placeholder="底胶 1&#10;裸色甲油胶 #E03 1"></textarea>
      </section>

      <template #footer>
        <button v-if="editing && editing.enabled === 1" type="button" class="btn" @click="disableCurrent">
          下架
        </button>
        <button type="button" class="btn" @click="editorVisible = false">取消</button>
        <button type="button" class="btn btn-primary wide" :disabled="submitting" @click="submitStyle">
          {{ submitting ? '保存中…' : '保存' }}
        </button>
      </template>
    </SoloSheet>

    <!-- 导出案例图 -->
    <SoloSheet
      :visible="exportVisible"
      title="导出案例图"
      note="竖版 1080×1440 · 九宫格 · 带水印"
      @update:visible="exportVisible = $event"
    >
      <section class="field">
        <div class="field-head">
          <span class="field-label">按标签选</span>
          <span class="field-hint">不选＝全部在架款，最多 9 款</span>
        </div>
        <div class="tags">
          <button
            v-for="tag in allTags"
            :key="tag"
            type="button"
            class="tag"
            :class="{ 'is-active': exportTags.includes(tag) }"
            @click="toggleExportTag(tag)"
          >
            {{ tag }}
          </button>
          <span v-if="!allTags.length" class="field-hint">款式还没打标签。</span>
        </div>
      </section>

      <section class="preview">
        <div v-if="building" class="preview-empty">生成中…</div>
        <img v-else-if="exportPreview" :src="exportPreview" alt="案例图预览" class="preview-img" />
        <div v-else class="preview-empty">选好标签后会自动生成</div>
      </section>

      <p v-if="exportNote" class="note">{{ exportNote }}</p>

      <template #footer>
        <button type="button" class="btn" @click="buildPreview">重新生成</button>
        <button type="button" class="btn btn-primary wide" :disabled="!exportPreview" @click="saveImage">
          保存图片
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
  align-items: flex-start;
  gap: var(--s3);
  margin-bottom: var(--s3);
  flex-wrap: wrap;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
  flex: 1 1 auto;
}

.tag {
  padding: 2px 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
}

.tag.is-active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
}

.actions {
  display: flex;
  flex: none;
  gap: var(--s2);
}

.ghost {
  padding: 5px 10px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  white-space: nowrap;
}

.create-btn {
  padding: 5px 12px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--s3);
}

.card {
  display: flex;
  flex-direction: column;
  padding: 0;
  border: 1px solid var(--line);
  background: var(--paper-raised);
  color: var(--ink);
  cursor: pointer;
  text-align: left;
  overflow: hidden;
}

.card:hover {
  border-color: var(--line-strong);
}

.card.is-off {
  opacity: 0.55;
}

.cover {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: var(--paper-sunken);
}

.cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-fallback {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--ink-4);
  font-family: var(--font-display);
  font-size: 34px;
}

.off-flag {
  position: absolute;
  top: 6px;
  left: 6px;
  padding: 1px 5px;
  background: var(--paper-raised);
  border: 1px solid var(--line-strong);
  color: var(--ink-3);
  font-size: 9px;
}

.body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: var(--s2) var(--s3) var(--s3);
}

.name {
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  color: var(--ink-3);
  font-size: 10px;
}

.tag-line {
  overflow: hidden;
  color: var(--ink-4);
  font-size: 9px;
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

.field.row {
  display: flex;
  gap: var(--s4);
}

.half {
  flex: 1 1 0;
  min-width: 0;
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

.textarea {
  width: 100%;
  min-height: 84px;
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

.preview {
  margin-bottom: var(--s2);
}

.preview-img {
  display: block;
  width: 100%;
  max-width: 320px;
  border: 1px solid var(--line);
}

.preview-empty {
  display: grid;
  width: 100%;
  max-width: 320px;
  aspect-ratio: 3 / 4;
  place-items: center;
  border: 1px dashed var(--line);
  color: var(--ink-4);
  font-size: 11px;
}

.note {
  color: var(--ink-3);
  font-size: 11px;
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

@media (min-width: 900px) {
  .grid {
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: var(--s4);
  }

  .preview-img,
  .preview-empty {
    max-width: 360px;
  }
}
</style>
