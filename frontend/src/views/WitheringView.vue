<template>
  <div class="tf-page">
    <div class="tf-head">
      <h2>摊青槽失水监控</h2>
      <span class="tf-sub">一排摊青槽 · 叶片铺层随摊叶厚度变化 · 失水率达目标即可放行杀青</span>
    </div>

    <!-- ============ 开槽登记 ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        开槽登记
        <el-button link type="primary" size="small" @click="showOpen = !showOpen">
          {{ showOpen ? '收起' : '登记一槽鲜叶' }}
        </el-button>
      </div>
      <el-form v-if="showOpen" label-width="92px" size="small" inline class="wt-open">
        <el-form-item label="槽号">
          <el-input v-model="openForm.troughNo" placeholder="如 TQ-09" style="width: 120px" />
        </el-form-item>
        <el-form-item label="茶类">
          <el-select v-model="openForm.teaType" style="width: 120px">
            <el-option v-for="t in teaTypeList" :key="t" :label="teaName(t)" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="摊青方式">
          <el-select v-model="openForm.witherMethod" style="width: 130px">
            <el-option v-for="m in witherMethods" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="摊叶厚度 cm">
          <el-input-number v-model="openForm.leafThicknessCm" :min="1" :max="15" :step="0.5" :precision="1" style="width: 120px" />
        </el-form-item>
        <el-form-item label="在槽 kg">
          <el-input-number v-model="openForm.leafWeightKg" :min="1" :max="9999" :step="10" :precision="2" style="width: 130px" />
        </el-form-item>
        <el-form-item label="进厂单号">
          <el-select v-model="openForm.intakeNo" clearable filterable style="width: 170px">
            <el-option v-for="r in intakes" :key="r.intakeNo" :label="r.intakeNo" :value="r.intakeNo" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="small" :loading="opening" @click="openTrough">开槽</el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- ============ 摊青槽一排卡片（仅当前在槽） ============ -->
    <div class="wt-rail">
      <div
        v-for="t in troughs"
        :key="t.id"
        class="wt-card"
        :class="{ 'is-ready': t.status === 'READY', 'is-selected': selected === t.id }"
        @click="selectCard(t)"
      >
        <div class="wt-card-head">
          <b>{{ t.troughNo }}</b>
          <el-tag size="small" :type="t.status === 'READY' ? 'success' : t.status === 'DONE' ? 'info' : 'warning'">
            {{ statusName(t.status) }}
          </el-tag>
        </div>
        <div class="wt-card-sub">{{ teaName(t.teaType) }} · {{ t.witherMethod || '-' }}</div>

        <!-- 叶片铺层 SVG -->
        <svg class="wt-layer" viewBox="0 0 160 66" preserveAspectRatio="none">
          <path d="M8 58 L152 58 L146 64 L14 64 Z" fill="#c4b99f" />
          <line x1="6" y1="58" x2="154" y2="58" stroke="#8a8570" stroke-width="1" />
          <g v-for="(leaf, i) in layer(t)" :key="i">
            <ellipse
              :cx="leaf.cx"
              :cy="leaf.cy"
              :rx="leaf.rx"
              :ry="leaf.ry"
              :transform="`rotate(${leaf.rot} ${leaf.cx} ${leaf.cy})`"
              :fill="leaf.fill"
              stroke="#33691e"
              stroke-width="0.5"
            />
          </g>
        </svg>

        <div class="wt-metrics">
          <span>厚度 {{ num(t.leafThicknessCm, 1) }} cm</span>
          <span>在槽 {{ num(t.leafWeightKg, 0) }} kg</span>
        </div>

        <div class="wt-bar">
          <div class="wt-bar-fill" :style="{ width: progress(t) + '%' }"></div>
          <div class="wt-bar-target"></div>
        </div>
        <div class="wt-bar-label">
          失水 <b :class="{ 'tf-green': t.status === 'READY' }">{{ num(t.currentLossPct, 2) }}%</b>
          / 目标 {{ num(t.targetLossPct, 2) }}%
        </div>

        <div v-if="t.status === 'READY'" class="wt-ready">可杀青</div>
        <div v-else class="wt-need">还差 {{ num(gap(t), 2) }}%</div>
      </div>
      <div v-if="!troughs.length" class="tf-empty">暂无在槽摊青记录，先在上方登记一槽（已下槽槽号可直接复用）</div>
    </div>

    <!-- ============ 历史完成批次履历 ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        下槽批次履历
        <small>历史完成记录保留可追溯，不计入当前占用与达标槽数</small>
        <el-button link type="primary" size="small" style="float: right" @click="loadHistory">刷新</el-button>
      </div>
      <el-table :data="historyRows" size="small" stripe empty-text="暂无已下槽历史批次">
        <el-table-column prop="troughNo" label="槽号" width="80" />
        <el-table-column label="茶类" width="90">
          <template #default="{ row }">{{ teaName(row.teaType) }}</template>
        </el-table-column>
        <el-table-column prop="witherMethod" label="摊青方式" width="100" />
        <el-table-column prop="intakeNo" label="进厂单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="工单批次" min-width="170" show-overflow-tooltip />
        <el-table-column label="在槽 kg" width="90" align="right">
          <template #default="{ row }">{{ num(row.leafWeightKg, 0) }}</template>
        </el-table-column>
        <el-table-column label="下槽失水率" width="100" align="right">
          <template #default="{ row }">{{ num(row.currentLossPct, 2) }}%</template>
        </el-table-column>
        <el-table-column label="开槽时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.openedAt) }}</template>
        </el-table-column>
        <el-table-column label="下槽时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.releasedAt) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ============ 选中槽：登记 / 放行 ============ -->
    <section v-if="current" class="tf-card wt-detail">
      <div class="tf-card-title">
        {{ current.troughNo }} 环境登记
        <small>{{ teaName(current.teaType) }} · {{ statusName(current.status) }} · 更新于 {{ fmtTime(current.updatedAt) }}</small>
      </div>

      <div class="wt-detail-body">
        <div class="wt-gauge">
          <svg viewBox="0 0 180 180" class="wt-gauge-svg">
            <circle cx="90" cy="90" r="70" fill="#faf7f0" stroke="#ddd5c2" />
            <circle
              cx="90"
              cy="90"
              r="70"
              fill="none"
              stroke="#33691e"
              stroke-width="8"
              stroke-linecap="round"
              :stroke-dasharray="String(ringDash) + ' 999'"
              transform="rotate(-90 90 90)"
            />
            <text x="90" y="84" class="wt-gauge-num">{{ num(current.currentLossPct, 1) }}%</text>
            <text x="90" y="104" class="wt-gauge-cap">当前失水率</text>
            <text x="90" y="124" class="wt-gauge-cap2">目标 {{ num(current.targetLossPct, 1) }}%</text>
          </svg>
        </div>

        <el-form label-width="86px" size="small" class="wt-form">
          <el-form-item label="环境温度 ℃">
            <el-input-number v-model="logForm.tempC" :min="0" :max="60" :step="0.5" :precision="1" style="width: 140px" />
          </el-form-item>
          <el-form-item label="相对湿度 %">
            <el-input-number v-model="logForm.humidityPct" :min="0" :max="100" :step="1" :precision="1" style="width: 140px" />
          </el-form-item>
          <el-form-item :label="'失水率 ' + num(logForm.currentLossPct, 1) + '%'">
            <el-slider v-model="logForm.currentLossPct" :min="0" :max="Math.max(Number(current.targetLossPct) + 10, 40)" :step="0.1" style="width: 220px" />
          </el-form-item>
          <el-form-item label="摊青时长 h">
            <el-input-number v-model="logForm.witherHours" :min="0" :max="72" :step="0.5" :precision="1" style="width: 140px" />
          </el-form-item>
          <el-form-item label="摊叶厚度 cm">
            <el-input-number v-model="logForm.leafThicknessCm" :min="1" :max="15" :step="0.5" :precision="1" style="width: 140px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="small" :loading="logging" :disabled="current.status === 'DONE'" @click="saveLog">
              登记温湿度与失水率
            </el-button>
            <el-button
              size="small"
              type="success"
              :loading="releasing"
              :disabled="current.status === 'DONE'"
              @click="releaseCard"
            >
              放行下槽
            </el-button>
            <span class="wt-hint">
              失水率未达 {{ num(current.targetLossPct, 2) }}% 时，后端会拒绝放行
            </span>
          </el-form-item>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { intakeApi, witheringApi } from '../api'

const TEA_TYPES = ['GREEN', 'RED', 'OOLONG', 'WHITE', 'PUER']
const TEA_NAMES = { GREEN: '绿茶', RED: '红茶', OOLONG: '乌龙茶', WHITE: '白茶', PUER: '普洱茶' }
const WITHER_METHODS = ['日光晒青', '室内萎凋']
const teaTypeList = TEA_TYPES
const witherMethods = WITHER_METHODS

const props = defineProps({
  teaType: { type: String, default: 'GREEN' }
})

const troughs = ref([])
const historyRows = ref([])
const intakes = ref([])
const selected = ref(null)
const showOpen = ref(false)
const opening = ref(false)
const logging = ref(false)
const releasing = ref(false)

const openForm = reactive({
  troughNo: '',
  teaType: props.teaType || 'GREEN',
  witherMethod: '室内萎凋',
  leafThicknessCm: 3,
  leafWeightKg: 180,
  intakeNo: ''
})

const logForm = reactive({
  tempC: 24,
  humidityPct: 68,
  currentLossPct: 0,
  witherHours: 6,
  leafThicknessCm: 3
})

const teaName = (t) => TEA_NAMES[t] || t || '-'
const num = (v, d = 2) => (v === null || v === undefined ? '-' : Number(v).toFixed(d))
const fmtTime = (s) => (s ? String(s).replace('T', ' ').slice(0, 16) : '-')
const statusName = (s) => ({ WITHERING: '摊青中', READY: '可杀青', DONE: '已下槽' }[s] || s || '-')

const current = computed(() => troughs.value.find((t) => t.id === selected.value) || null)

/** 失水完成百分比 */
function progress(t) {
  const cur = Number(t.currentLossPct || 0)
  const target = Number(t.targetLossPct || 0)
  if (!target) return 0
  return Math.max(0, Math.min(100, (cur / target) * 100))
}

function gap(t) {
  return Math.max(0, Number(t.targetLossPct || 0) - Number(t.currentLossPct || 0))
}

/** 槽内层叶片：数量随摊叶厚度变化 */
function layer(t) {
  const thickness = Number(t.leafThicknessCm || 3)
  const count = Math.max(4, Math.min(26, Math.round(thickness * 4)))
  const rowsNum = Math.max(1, Math.min(4, Math.round(thickness / 4) + 1))
  const list = []
  for (let i = 0; i < count; i++) {
    const row = i % rowsNum
    const col = Math.floor(i / rowsNum)
    list.push({
      cx: 12 + col * 22 + (row % 2) * 9,
      cy: 54 - row * 9,
      rx: 8,
      ry: 3.4,
      rot: (i % 2 === 0 ? -1 : 1) * (12 + (i % 6) * 11),
      fill: i % 4 === 0 ? '#7ea55f' : i % 4 === 1 ? '#5b8b3f' : i % 4 === 2 ? '#a6c088' : '#8fa870'
    })
  }
  return list
}

const ringDash = computed(() => {
  const p = current.value ? progress(current.value) : 0
  return (p / 100) * 2 * Math.PI * 70
})

async function load() {
  try {
    const res = await witheringApi.list()
    troughs.value = res && res.data ? res.data : []
    if (selected.value == null && troughs.value.length) selectCard(troughs.value[0])
    else if (selected.value != null) {
      const hit = troughs.value.find((t) => t.id === selected.value)
      if (hit) fillLog(hit)
      else selectCard(troughs.value[0] || null)
    }
  } catch (e) {
    ElMessage.error('加载摊青槽失败：' + e.message)
  }
}

async function loadHistory() {
  try {
    const res = await witheringApi.history()
    historyRows.value = res && res.data ? res.data : []
  } catch (e) {
    ElMessage.error('加载下槽履历失败：' + e.message)
  }
}

async function loadIntakes() {
  try {
    const res = await intakeApi.all()
    intakes.value = res && res.data ? res.data : []
  } catch (e) {
    /* 关联进厂单列表加载失败不影响主流程 */
  }
}

function selectCard(t) {
  if (!t) {
    selected.value = null
    return
  }
  selected.value = t.id
  fillLog(t)
}

function fillLog(t) {
  logForm.tempC = Number(t.tempC || 0)
  logForm.humidityPct = Number(t.humidityPct || 0)
  logForm.currentLossPct = Number(t.currentLossPct || 0)
  logForm.witherHours = Number(t.witherHours || 0)
  logForm.leafThicknessCm = Number(t.leafThicknessCm || 3)
}

async function openTrough() {
  if (!openForm.troughNo || !String(openForm.troughNo).trim()) {
    ElMessage.warning('请填写槽号')
    return
  }
  opening.value = true
  try {
    await witheringApi.open({
      troughNo: String(openForm.troughNo).trim(),
      teaType: openForm.teaType,
      witherMethod: openForm.witherMethod,
      leafThicknessCm: openForm.leafThicknessCm,
      leafWeightKg: openForm.leafWeightKg,
      intakeNo: openForm.intakeNo || undefined
    })
    const no = String(openForm.troughNo).trim()
    ElMessage.success(`已开槽 ${no}`)
    showOpen.value = false
    openForm.troughNo = ''
    await load()
    await loadHistory()
    const hit = troughs.value.find((t) => t.troughNo === no)
    if (hit) selectCard(hit)
  } catch (e) {
    ElMessage.error(e.message || '开槽失败')
  } finally {
    opening.value = false
  }
}

async function saveLog() {
  if (!current.value) return
  logging.value = true
  try {
    const res = await witheringApi.log(current.value.troughNo, {
      tempC: logForm.tempC,
      humidityPct: logForm.humidityPct,
      currentLossPct: logForm.currentLossPct,
      witherHours: logForm.witherHours,
      leafThicknessCm: logForm.leafThicknessCm
    })
    ElMessage.success(res && res.message ? res.message : '登记成功')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '登记失败')
  } finally {
    logging.value = false
  }
}

async function releaseCard() {
  if (!current.value) return
  releasing.value = true
  try {
    const res = await witheringApi.release(current.value.troughNo)
    ElMessage.success(res && res.message ? res.message : '已下槽')
    await load()
    await loadHistory()
  } catch (e) {
    ElMessage.error(e.message || '放行失败')
  } finally {
    releasing.value = false
  }
}

watch(
  () => props.teaType,
  (v) => {
    openForm.teaType = v || 'GREEN'
  }
)

onMounted(() => {
  load()
  loadHistory()
  loadIntakes()
})
</script>

<style scoped>
.wt-open {
  display: flex;
  flex-wrap: wrap;
}

.wt-rail {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding: 4px 2px 12px;
}

.wt-card {
  flex: 0 0 210px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #ddd5c2;
  border-radius: 6px;
  padding: 10px 12px 12px;
  cursor: pointer;
  transition: all 0.22s ease;
}

.wt-card:hover {
  border-color: #5b8b3f;
  box-shadow: 0 3px 12px rgba(51, 105, 30, 0.14);
}

.wt-card.is-selected {
  border-color: #33691e;
  box-shadow: 0 0 0 2px rgba(51, 105, 30, 0.18);
}

.wt-card.is-ready {
  background: linear-gradient(180deg, #eef5e6 0%, #ffffff 60%);
  border-color: #33691e;
}

.wt-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wt-card-head b {
  font-size: 15px;
  color: #33691e;
  letter-spacing: 1px;
}

.wt-card-sub {
  font-size: 11px;
  color: #5d6353;
  margin: 2px 0 6px;
}

.wt-layer {
  width: 100%;
  height: 66px;
  display: block;
  border-bottom: 1px solid #ddd5c2;
}

.wt-metrics {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #5d6353;
  margin: 6px 0 5px;
}

.wt-bar {
  position: relative;
  height: 9px;
  border: 1px solid #c4b99f;
  border-radius: 5px;
  background: #f4efe4;
  overflow: hidden;
}

.wt-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #7ea55f 0%, #33691e 100%);
  transition: width 0.5s ease;
}

.wt-bar-target {
  position: absolute;
  right: 0;
  top: 0;
  width: 2px;
  height: 100%;
  background: #b45309;
}

.wt-bar-label {
  font-size: 11px;
  color: #5d6353;
  margin-top: 5px;
}

.wt-ready {
  margin-top: 6px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #33691e;
  border-radius: 4px;
  padding: 3px 0;
  letter-spacing: 2px;
}

.wt-need {
  margin-top: 6px;
  text-align: center;
  font-size: 11px;
  color: #b45309;
}

.wt-detail-body {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  align-items: flex-start;
}

.wt-gauge-svg {
  width: 180px;
  height: 180px;
}

.wt-gauge-num {
  font-size: 24px;
  font-weight: 700;
  fill: #33691e;
  text-anchor: middle;
}

.wt-gauge-cap {
  font-size: 11px;
  fill: #5d6353;
  text-anchor: middle;
}

.wt-gauge-cap2 {
  font-size: 11px;
  fill: #b45309;
  text-anchor: middle;
}

.wt-form {
  flex: 1 1 380px;
  min-width: 320px;
}

.wt-hint {
  margin-left: 10px;
  font-size: 11px;
  color: #5d6353;
}
</style>
