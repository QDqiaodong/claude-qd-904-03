<template>
  <div class="tf-page">
    <div class="tf-head">
      <h2>审评与成品入库</h2>
      <span class="tf-sub">五项评分加权定级 · 雷达图实时联动 · 出厂水分超 7% 不许入库</span>
    </div>

    <!-- ============ 雷达 + 评分录入 ============ -->
    <div class="ev-body">
      <section class="tf-card ev-radar">
        <div class="tf-card-title">
          五维审评雷达
          <small>{{ batchNo ? '批次 ' + batchNo : '未选批次' }}</small>
        </div>
        <svg viewBox="0 0 320 300" class="ev-svg">
          <!-- 背景网格 -->
          <polygon v-for="(ring, i) in rings" :key="'r' + i" :points="ring" fill="none" stroke="#ddd5c2" :stroke-width="i === rings.length - 1 ? 1.2 : 0.7" />
          <line v-for="(ax, i) in axes" :key="'a' + i" :x1="150" :y1="146" :x2="ax.x" :y2="ax.y" stroke="#ddd5c2" />
          <!-- 得分多边形 -->
          <polygon :points="polygon" fill="rgba(51,105,30,0.22)" stroke="#33691e" stroke-width="2" class="ev-poly" />
          <circle v-for="(p, i) in points" :key="'p' + i" :cx="p.x" :cy="p.y" r="3.6" fill="#33691e" />
          <!-- 轴标签 -->
          <g v-for="(dim, i) in dims" :key="dim.label">
            <text :x="axes[i].lx" :y="axes[i].ly" class="ev-axis-label" :text-anchor="axes[i].anchor">{{ dim.label }}</text>
            <text :x="axes[i].vx" :y="axes[i].vy" class="ev-axis-value" :text-anchor="axes[i].anchor">{{ valueOf(dim.key) }}</text>
          </g>
        </svg>
        <div class="ev-total">
          <div class="ev-total-score" :style="{ color: gradeColor }">{{ complete ? total.toFixed(2) : '--' }}</div>
          <div class="ev-total-grade">加权总分</div>
          <el-tag v-if="complete" :type="gradeTag" size="large" effect="dark" class="ev-grade-tag">{{ grade }}</el-tag>
          <div v-else class="ev-total-tip">五项评分齐全后才能定级</div>
        </div>
      </section>

      <section class="tf-card ev-scores">
        <div class="tf-card-title">
          审评录入
          <small>权重 外形25% 汤色10% 香气25% 滋味30% 叶底10%</small>
        </div>

        <el-form label-width="92px" size="small">
          <el-form-item label="审评批次">
            <el-select v-model="batchNo" filterable style="width: 300px">
              <el-option
                v-for="o in orders"
                :key="o.batchNo"
                :label="o.batchNo + '（' + o.teaTypeName + '）'"
                :value="o.batchNo"
              />
            </el-select>
          </el-form-item>
        </el-form>

        <div v-for="dim in dims" :key="dim.key" class="ev-dim">
          <span class="ev-dim-label">{{ dim.label }}</span>
          <el-slider v-model="scores[dim.key]" :min="0" :max="100" :step="1" class="ev-dim-slider" />
          <el-input-number v-model="scores[dim.key]" :min="0" :max="100" :step="1" size="small" style="width: 110px" />
          <span class="ev-dim-weight">×{{ dim.weight }}</span>
        </div>

        <el-form label-width="92px" size="small" class="ev-extra">
          <el-form-item label="评语">
            <el-input v-model="comment" type="textarea" :rows="2" placeholder="如：条索紧结，栗香高长，滋味醇厚回甘" />
          </el-form-item>
          <el-form-item label="缺陷">
            <el-select v-model="defect" clearable style="width: 240px">
              <el-option v-for="d in defects" :key="d" :label="d" :value="d" />
            </el-select>
          </el-form-item>
          <el-form-item label="审评员">
            <el-input v-model="evaluator" style="width: 200px" />
          </el-form-item>
        </el-form>

        <el-button type="primary" :loading="savingEval" :disabled="!canSubmit" @click="submitEval">提交审评并定级</el-button>
        <span class="ev-hint">{{ canSubmit ? '' : missingHint }}</span>
      </section>
    </div>

    <!-- ============ 成品入库 ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        成品入库
        <small>仅「已完成全部工序」的批次可入库</small>
      </div>
      <el-form size="small" inline class="ev-store">
        <el-form-item label="批次">
          <el-select v-model="prodForm.batchNo" filterable style="width: 290px">
            <el-option
              v-for="o in orders"
              :key="o.batchNo"
              :label="o.batchNo + (o.status === 'DONE' ? '（已完成）' : '（在制）')"
              :value="o.batchNo"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="prodForm.grade" style="width: 110px">
            <el-option v-for="g in finishGrades" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="件数">
          <el-input-number v-model="prodForm.packages" :min="1" :max="9999" :step="1" style="width: 120px" />
        </el-form-item>
        <el-form-item label="净重 kg">
          <el-input-number v-model="prodForm.netWeightKg" :min="0.01" :max="99999" :step="5" :precision="2" style="width: 130px" />
        </el-form-item>
        <el-form-item label="仓位">
          <el-input v-model="prodForm.storageBin" style="width: 130px" placeholder="如 A-12" />
        </el-form-item>
        <el-form-item :label="'水分 ' + num(prodForm.moisturePct, 1) + '%'">
          <el-slider v-model="prodForm.moisturePct" :min="0" :max="12" :step="0.1" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="success" :loading="storing" @click="storeProduct">入库</el-button>
        </el-form-item>
      </el-form>

      <el-table
        :data="products"
        size="small"
        border
        stripe
        :row-class-name="rowClass"
        empty-text="暂无成品入库记录"
      >
        <el-table-column prop="productNo" label="成品编号" width="160" />
        <el-table-column prop="batchNo" label="批次" min-width="220" />
        <el-table-column label="茶类" width="90">
          <template #default="{ row }">{{ teaName(row.teaType) }}</template>
        </el-table-column>
        <el-table-column prop="grade" label="等级" width="80" />
        <el-table-column prop="packages" label="件数" width="80" align="right" />
        <el-table-column label="净重 kg" width="100" align="right">
          <template #default="{ row }">{{ num(row.netWeightKg) }}</template>
        </el-table-column>
        <el-table-column prop="storageBin" label="仓位" width="90" />
        <el-table-column label="水分 %" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'tf-red': Number(row.moisturePct) > 7 }">{{ num(row.moisturePct, 2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="入库判定" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="Number(row.moisturePct) > 7 ? 'danger' : 'success'">
              {{ Number(row.moisturePct) > 7 ? '水分超标退回' : '已入库' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入库时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.storedAt) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ============ 审评排行 ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        审评排行
        <small>按加权总分降序</small>
      </div>
      <el-table :data="ranking" size="small" border stripe empty-text="暂无审评记录">
        <el-table-column label="名次" width="80" align="center">
          <template #default="{ $index }">
            <span :class="'ev-rank-' + Math.min($index + 1, 4)">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" min-width="240" />
        <el-table-column label="茶类" width="90">
          <template #default="{ row }">{{ teaName(row.teaType) }}</template>
        </el-table-column>
        <el-table-column label="总分" width="90" align="right">
          <template #default="{ row }"><b class="tf-green">{{ Number(row.totalScore).toFixed(2) }}</b></template>
        </el-table-column>
        <el-table-column prop="grade" label="等级" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="gradeTagOf(row.totalScore)">{{ row.grade }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="评语" min-width="220" />
        <el-table-column prop="evaluator" label="审评员" width="100" />
        <el-table-column label="审评时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.evaluatedAt) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { evaluationApi, processApi } from '../api'

const TEA_NAMES = { GREEN: '绿茶', RED: '红茶', OOLONG: '乌龙茶', WHITE: '白茶', PUER: '普洱茶' }
const MAX_MOISTURE = 7

/* 五维：轴顺序与权重（与后端 Dict.EVAL_WEIGHT 一致） */
const dims = [
  { key: 'shapeScore', label: '外形', weight: 0.25 },
  { key: 'soupScore', label: '汤色', weight: 0.1 },
  { key: 'aromaScore', label: '香气', weight: 0.25 },
  { key: 'tasteScore', label: '滋味', weight: 0.3 },
  { key: 'leafBaseScore', label: '叶底', weight: 0.1 }
]
const DEFAULT_DEFECTS = ['红变叶', '焦边爆点', '断碎', '水闷味', '霉变', '老叶夹杂', '虫咬叶', '异味吸附']
const DEFAULT_GRADES = ['特级', '一级', '二级', '三级']

const CX = 150
const CY = 146
const R = 108

const orders = ref([])
const products = ref([])
const ranking = ref([])
const batchNo = ref('')
const defects = ref([...DEFAULT_DEFECTS])
const finishGrades = ref([...DEFAULT_GRADES])
const comment = ref('')
const defect = ref('')
const evaluator = ref('沈审评')
const savingEval = ref(false)
const storing = ref(false)

const scores = reactive({
  shapeScore: 92,
  soupScore: 90,
  aromaScore: 91,
  tasteScore: 93,
  leafBaseScore: 88
})

const prodForm = reactive({
  batchNo: '',
  grade: '一级',
  packages: 12,
  netWeightKg: 36,
  storageBin: 'A-12',
  moisturePct: 5.5
})

const teaName = (t) => TEA_NAMES[t] || t || '-'
const num = (v, d = 2) => (v === null || v === undefined ? '-' : Number(v).toFixed(d))
const fmtTime = (s) => (s ? String(s).replace('T', ' ').slice(0, 16) : '-')

const filled = (v) => v !== null && v !== undefined && v !== ''
const complete = computed(() => dims.every((d) => filled(scores[d.key])))
const missing = computed(() => dims.filter((d) => !filled(scores[d.key])).map((d) => d.label))

const total = computed(() => {
  if (!complete.value) return 0
  const sum = dims.reduce((s, d) => s + Number(scores[d.key]) * d.weight, 0)
  return Math.round(sum * 100) / 100
})

function gradeOf(v) {
  if (v >= 90) return '特级'
  if (v >= 80) return '一级'
  if (v >= 70) return '二级'
  if (v >= 60) return '三级'
  return '不入等'
}
const grade = computed(() => gradeOf(total.value))
const gradeTag = computed(() => ({ 特级: 'success', 一级: 'success', 二级: 'warning', 三级: 'warning' }[grade.value] || 'info'))
function gradeTagOf(v) {
  const g = gradeOf(Number(v))
  return { 特级: 'success', 一级: 'success', 二级: 'warning', 三级: 'warning' }[g] || 'info'
}
const gradeColor = computed(() => ({ 特级: '#33691e', 一级: '#5b8b3f', 二级: '#b45309', 三级: '#b45309' }[grade.value] || '#8a8570'))

const canSubmit = computed(() => !!batchNo.value && complete.value)
const missingHint = computed(() => {
  if (!batchNo.value) return '请先选择审评批次'
  return '缺「' + missing.value.join('、') + '」评分，不能定级'
})

/* ---- 雷达图坐标：从正上方开始顺时针 ---- */
const RAD = Math.PI / 180
function axisPoint(i, ratio) {
  const deg = -90 + i * (360 / dims.length)
  return { x: CX + R * ratio * Math.cos(deg * RAD), y: CY + R * ratio * Math.sin(deg * RAD) }
}
const axes = computed(() =>
  dims.map((d, i) => {
    const p = axisPoint(i, 1)
    const lp = axisPoint(i, 1.16)
    const vp = axisPoint(i, 0.9)
    const anchor = p.x > CX + 1 ? 'start' : p.x < CX - 1 ? 'end' : 'middle'
    return { x: p.x, y: p.y, lx: lp.x, ly: lp.y + 4, vx: vp.x, vy: vp.y, anchor }
  })
)
const rings = computed(() =>
  [0.25, 0.5, 0.75, 1].map((r) => dims.map((d, i) => { const p = axisPoint(i, r); return `${p.x},${p.y}` }).join(' '))
)
const points = computed(() =>
  dims.map((d, i) => {
    const v = filled(scores[d.key]) ? Number(scores[d.key]) : 0
    return axisPoint(i, Math.max(0, Math.min(100, v)) / 100)
  })
)
const polygon = computed(() => points.value.map((p) => `${p.x},${p.y}`).join(' '))
const valueOf = (key) => (filled(scores[key]) ? Number(scores[key]) : '--')

function rowClass({ row }) {
  return Number(row.moisturePct) > MAX_MOISTURE ? 'tf-row-warn' : ''
}

async function loadOrders() {
  try {
    const res = await processApi.list()
    orders.value = res && res.data ? res.data : []
    if (!batchNo.value && orders.value.length) batchNo.value = orders.value[0].batchNo
    const done = orders.value.find((o) => o.status === 'DONE')
    if (!prodForm.batchNo) prodForm.batchNo = done ? done.batchNo : batchNo.value
  } catch (e) {
    ElMessage.error('加载批次失败：' + e.message)
  }
}

async function loadProducts() {
  try {
    const res = await evaluationApi.products()
    products.value = res && res.data ? res.data : []
  } catch (e) {
    /* 列表失败不阻断 */
  }
}

async function loadRanking() {
  try {
    const res = await evaluationApi.ranking()
    ranking.value = res && res.data ? res.data : []
  } catch (e) {
    /* 列表失败不阻断 */
  }
}

async function submitEval() {
  if (!canSubmit.value) {
    ElMessage.warning(missingHint.value)
    return
  }
  savingEval.value = true
  try {
    const res = await evaluationApi.create({
      batchNo: batchNo.value,
      shapeScore: Number(scores.shapeScore),
      soupScore: Number(scores.soupScore),
      aromaScore: Number(scores.aromaScore),
      tasteScore: Number(scores.tasteScore),
      leafBaseScore: Number(scores.leafBaseScore),
      comment: comment.value || undefined,
      defect: defect.value || undefined,
      evaluator: evaluator.value || undefined
    })
    ElMessage.success(res && res.message ? res.message : '审评完成')
    await loadRanking()
  } catch (e) {
    ElMessage.error(e.message || '审评提交失败')
  } finally {
    savingEval.value = false
  }
}

async function storeProduct() {
  if (!prodForm.batchNo) {
    ElMessage.warning('请选择入库批次')
    return
  }
  if (Number(prodForm.moisturePct) > MAX_MOISTURE) {
    ElMessage.error(`出厂水分 ${num(prodForm.moisturePct, 2)}% 超过上限 ${MAX_MOISTURE}%，不允许入库，请回烘复火`)
    return
  }
  storing.value = true
  try {
    const res = await evaluationApi.store({
      batchNo: prodForm.batchNo,
      grade: prodForm.grade,
      packages: Number(prodForm.packages),
      netWeightKg: Number(prodForm.netWeightKg),
      storageBin: prodForm.storageBin,
      moisturePct: Number(prodForm.moisturePct)
    })
    ElMessage.success(res && res.message ? res.message : '入库成功')
    await loadProducts()
  } catch (e) {
    ElMessage.error(e.message || '入库失败')
  } finally {
    storing.value = false
  }
}

onMounted(() => {
  loadOrders()
  loadProducts()
  loadRanking()
})
</script>

<style scoped>
.ev-body {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.ev-radar {
  flex: 0 0 360px;
}

.ev-scores {
  flex: 1 1 460px;
  min-width: 360px;
}

.ev-svg {
  width: 100%;
  height: auto;
  display: block;
}

.ev-poly {
  transition: all 0.35s ease;
}

.ev-axis-label {
  font-size: 13px;
  fill: #33691e;
  font-weight: 600;
}

.ev-axis-value {
  font-size: 12px;
  fill: #5d6353;
}

.ev-total {
  text-align: center;
  border-top: 1px dashed #c4b99f;
  padding-top: 10px;
  margin-top: 4px;
}

.ev-total-score {
  font-size: 40px;
  font-weight: 700;
  line-height: 1.1;
}

.ev-total-grade {
  font-size: 12px;
  color: #5d6353;
  margin-bottom: 8px;
}

.ev-grade-tag {
  letter-spacing: 2px;
}

.ev-total-tip {
  font-size: 12px;
  color: #b45309;
}

.ev-dim {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.ev-dim-label {
  flex: 0 0 46px;
  font-size: 13px;
  color: #33691e;
  font-weight: 600;
}

.ev-dim-slider {
  flex: 1 1 180px;
}

.ev-dim-weight {
  flex: 0 0 44px;
  font-size: 11px;
  color: #5d6353;
}

.ev-extra {
  margin-top: 8px;
}

.ev-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #b45309;
}

.ev-store {
  display: flex;
  flex-wrap: wrap;
}

.ev-rank-1 {
  color: #b45309;
  font-weight: 700;
}

.ev-rank-2,
.ev-rank-3 {
  color: #33691e;
  font-weight: 600;
}
</style>
