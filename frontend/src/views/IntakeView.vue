<template>
  <div class="tf-page">
    <div class="tf-head">
      <h2>鲜叶验收台</h2>
      <span class="tf-sub">过秤复检 · 含水量 / 杂质率超标自动扣重 · 按鲜叶等级单价结算</span>
    </div>

    <div class="iv-body">
      <!-- ================= 左：验收盘秤 ================= -->
      <section class="tf-card iv-scale">
        <div class="tf-card-title">
          验收盘秤
          <small>含水量 &gt; 78% 或 杂质率 &gt; 5% 按比例扣重</small>
        </div>

        <svg class="iv-svg" viewBox="0 0 260 330">
          <!-- 立柱与底座 -->
          <line x1="130" y1="72" x2="130" y2="286" stroke="#8a8570" stroke-width="4" />
          <path d="M96 300 L164 300 L150 286 L110 286 Z" fill="#e7efdd" stroke="#33691e" />
          <rect x="92" y="300" width="76" height="10" rx="3" fill="#33691e" />
          <polygon points="130,72 122,86 138,86" fill="#33691e" />

          <!-- 横梁（按扣重比例倾斜） -->
          <g class="iv-anim" :style="beamStyle">
            <line x1="60" y1="72" x2="200" y2="72" stroke="#33691e" stroke-width="3" />
            <circle cx="60" cy="72" r="4" fill="#fff" stroke="#33691e" />
            <circle cx="200" cy="72" r="4" fill="#fff" stroke="#33691e" />

            <!-- 左秤盘：毛重（鲜叶） -->
            <line x1="60" y1="72" x2="44" y2="104" stroke="#8a8570" />
            <line x1="60" y1="72" x2="76" y2="104" stroke="#8a8570" />
            <path d="M40 104 Q60 122 80 104 Z" fill="#e7efdd" stroke="#33691e" />
            <g v-for="(leaf, i) in leaves" :key="'L' + i">
              <ellipse
                :cx="leaf.x"
                :cy="leaf.y"
                :rx="leaf.rx"
                :ry="leaf.ry"
                :transform="`rotate(${leaf.rot} ${leaf.x} ${leaf.y})`"
                :fill="leaf.fill"
                stroke="#33691e"
                stroke-width="0.6"
              />
            </g>

            <!-- 右秤盘：净重（标准砝码） -->
            <line x1="200" y1="72" x2="184" y2="104" stroke="#8a8570" />
            <line x1="200" y1="72" x2="216" y2="104" stroke="#8a8570" />
            <path d="M180 104 Q200 122 220 104 Z" fill="#f4efe4" stroke="#8a8570" />
            <rect x="188" y="90" width="8" height="10" fill="#c4b99f" stroke="#8a8570" />
            <rect x="198" y="86" width="8" height="14" fill="#c4b99f" stroke="#8a8570" />
            <rect x="208" y="92" width="8" height="8" fill="#c4b99f" stroke="#8a8570" />
          </g>

          <!-- 刻度表盘 -->
          <path d="M70 232 A60 60 0 0 1 190 232" fill="none" stroke="#c4b99f" stroke-width="1" />
          <line
            v-for="tick in ticks"
            :key="tick.v"
            :x1="tick.x1"
            :y1="tick.y1"
            :x2="tick.x2"
            :y2="tick.y2"
            :stroke="tick.v >= 24 ? '#b3261e' : '#8a8570'"
            :stroke-width="tick.v % 10 === 0 ? 1.6 : 0.8"
          />
          <text v-for="lab in labels" :key="'lab' + lab.v" :x="lab.x" :y="lab.y" class="iv-tick-text">{{ lab.v }}</text>

          <g class="iv-anim" :style="needleStyle">
            <line x1="130" y1="232" x2="86" y2="232" stroke="#33691e" stroke-width="2.4" />
            <circle cx="130" cy="232" r="5" fill="#33691e" />
          </g>
          <circle cx="130" cy="232" r="3" fill="#faf7f0" stroke="#33691e" />

          <text x="130" y="212" class="iv-dial-value" :fill="rateColor">{{ deductRate.toFixed(2) }}%</text>
          <text x="130" y="252" class="iv-dial-label">扣重比例</text>
        </svg>

        <div class="iv-readout">
          <div><span>毛重</span><b>{{ num(form.grossWeightKg) }} kg</b></div>
          <div><span>扣重</span><b class="tf-red">-{{ num(deductWeight) }} kg</b></div>
          <div><span>净重</span><b class="tf-green">{{ num(netWeight) }} kg</b></div>
          <div><span>等级单价</span><b>{{ num(unitPrice) }} 元/kg</b></div>
          <div class="iv-readout-total"><span>预估结算价</span><b>{{ num(settlePrice) }} 元</b></div>
        </div>
        <div class="iv-reason" :class="{ 'iv-reason-warn': deductReason }">{{ deductReason || '各项指标均在限值内，验收合格不扣重' }}</div>
      </section>

      <!-- ================= 右：验收单录入 ================= -->
      <section class="tf-card iv-form">
        <div class="tf-card-title">
          验收单录入
          <small>进厂单号由系统自动生成</small>
        </div>

        <el-form label-width="96px" size="small">
          <el-form-item label="供应商/茶农">
            <el-input v-model="form.supplierName" placeholder="如：易武麻黑 李阿芳" />
          </el-form-item>
          <el-form-item label="采摘地块">
            <el-input v-model="form.plotCode" placeholder="如：B-11 半坡地" />
          </el-form-item>
          <el-form-item label="采摘日期">
            <el-date-picker v-model="form.pickDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="鲜叶等级">
            <el-select v-model="form.leafGrade" style="width: 100%">
              <el-option
                v-for="g in grades"
                :key="g"
                :label="g + '（' + num(priceOf(g)) + ' 元/kg）'"
                :value="g"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="毛重 kg">
            <el-input-number v-model="form.grossWeightKg" :min="0.01" :max="9999" :step="10" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="'含水量 ' + num(form.moisturePct, 1) + '%'">
            <el-slider v-model="form.moisturePct" :min="60" :max="90" :step="0.1" />
          </el-form-item>
          <el-form-item :label="'杂质率 ' + num(form.impurityPct, 1) + '%'">
            <el-slider v-model="form.impurityPct" :min="0" :max="15" :step="0.1" />
          </el-form-item>
          <el-form-item label="验收员">
            <el-input v-model="form.inspector" placeholder="默认陈质检" />
          </el-form-item>
        </el-form>

        <el-alert v-if="deductRate > 30" type="error" :closable="false" show-icon class="iv-alert">
          扣重比例 {{ deductRate.toFixed(2) }}% 已超上限 30%，该车鲜叶应整批退回
        </el-alert>

        <div class="iv-actions">
          <el-button type="primary" :loading="submitting" @click="submit">过秤验收并生成单号</el-button>
          <el-button @click="resetForm">重置</el-button>
        </div>
      </section>
    </div>

    <!-- ================= 当日进厂流水 ================= -->
    <section class="tf-card">
      <div class="tf-card-title">
        当日进厂流水
        <small>共 {{ rows.length }} 车 · 扣重合计 {{ num(totalDeduct) }} kg</small>
      </div>
      <el-table :data="rows" size="small" border stripe empty-text="当日暂无进厂记录">
        <el-table-column prop="intakeNo" label="进厂单号" width="150" />
        <el-table-column prop="supplierName" label="供应商/茶农" min-width="150" />
        <el-table-column prop="plotCode" label="地块" width="120" />
        <el-table-column prop="leafGrade" label="等级" width="100" />
        <el-table-column label="毛重 kg" width="100" align="right">
          <template #default="{ row }">{{ num(row.grossWeightKg) }}</template>
        </el-table-column>
        <el-table-column label="含水量" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'tf-red': Number(row.moisturePct) > 78 }">{{ num(row.moisturePct, 1) }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="杂质率" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'tf-red': Number(row.impurityPct) > 5 }">{{ num(row.impurityPct, 1) }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="扣重" width="150">
          <template #default="{ row }">
            <span v-if="Number(row.deductWeightKg) > 0" class="tf-red">
              {{ num(row.deductWeightKg) }} kg / {{ num(row.deductRatePct, 2) }}%
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="净重 kg" width="100" align="right">
          <template #default="{ row }">{{ num(row.netWeightKg) }}</template>
        </el-table-column>
        <el-table-column label="结算价 元" width="120" align="right">
          <template #default="{ row }">{{ num(row.settlePrice) }}</template>
        </el-table-column>
        <el-table-column label="验收结果" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'DEDUCTED' ? 'warning' : 'success'">{{ statusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { intakeApi } from '../api'

/* 扣重规则阈值（与后端 Dict 一致） */
const MAX_MOISTURE = 78
const MAX_IMPURITY = 5
const MOISTURE_PER_PCT = 1.5
const IMPURITY_PER_PCT = 2.0
const MAX_DEDUCT_RATE = 30

const DEFAULT_GRADES = ['单芽', '一芽一叶', '一芽二叶', '对夹叶']
const DEFAULT_PRICE = { 单芽: 118, 一芽一叶: 86, 一芽二叶: 52, 对夹叶: 24 }

const grades = ref([...DEFAULT_GRADES])
const priceMap = ref({ ...DEFAULT_PRICE })
const rows = ref([])
const submitting = ref(false)

const today = new Date()
const pad = (n) => String(n).padStart(2, '0')
const todayStr = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`

const form = reactive({
  supplierName: '',
  plotCode: '',
  pickDate: todayStr,
  leafGrade: '一芽一叶',
  grossWeightKg: 180,
  moisturePct: 76,
  impurityPct: 2.4,
  inspector: ''
})

const nz = (v) => (v === null || v === undefined || v === '' ? 0 : Number(v))
const num = (v, d = 2) => (v === null || v === undefined ? '-' : Number(v).toFixed(d))
const fmtTime = (s) => (s ? String(s).replace('T', ' ').slice(0, 16) : '-')
const priceOf = (g) => (priceMap.value && priceMap.value[g] !== undefined ? priceMap.value[g] : DEFAULT_PRICE[g] || 0)

/** 扣重比例 % */
const deductRate = computed(() => {
  let rate = 0
  const m = nz(form.moisturePct)
  const p = nz(form.impurityPct)
  if (m > MAX_MOISTURE) rate += (m - MAX_MOISTURE) * MOISTURE_PER_PCT
  if (p > MAX_IMPURITY) rate += (p - MAX_IMPURITY) * IMPURITY_PER_PCT
  return rate
})

const deductWeight = computed(() => {
  const gross = nz(form.grossWeightKg)
  return Math.round(gross * (deductRate.value / 100) * 100) / 100
})
const netWeight = computed(() => nz(form.grossWeightKg) - deductWeight.value)
const unitPrice = computed(() => priceOf(form.leafGrade))
const settlePrice = computed(() => Math.round(netWeight.value * unitPrice.value * 100) / 100)

const deductReason = computed(() => {
  const list = []
  const m = nz(form.moisturePct)
  const p = nz(form.impurityPct)
  if (m > MAX_MOISTURE) list.push(`含水量 ${num(m, 1)}% 超上限 ${MAX_MOISTURE}%`)
  if (p > MAX_IMPURITY) list.push(`杂质率 ${num(p, 1)}% 超上限 ${MAX_IMPURITY}%`)
  return list.join('；')
})

const rateColor = computed(() => (deductRate.value > 24 ? '#b3261e' : deductRate.value > 0 ? '#b45309' : '#33691e'))

/* ---- 秤：横梁倾角（-1 ~ -9 度，扣重越多左边压得越低）---- */
const beamStyle = computed(() => ({
  transform: `rotate(${Math.min(deductRate.value / MAX_DEDUCT_RATE, 1) * -9}deg)`,
  transformBox: 'view-box',
  transformOrigin: '130px 72px'
}))

/* ---- 秤：指针（0 → 左，180 → 右）---- */
const needleStyle = computed(() => ({
  transform: `rotate(${Math.min(deductRate.value / MAX_DEDUCT_RATE, 1) * 180}deg)`,
  transformBox: 'view-box',
  transformOrigin: '130px 232px'
}))

/* ---- 刻度 ---- */
const CENTER = { x: 130, y: 232 }
const RADIUS = 60
function polar(v, r) {
  const deg = 180 + (v / MAX_DEDUCT_RATE) * 180
  const rad = (deg * Math.PI) / 180
  return { x: CENTER.x + r * Math.cos(rad), y: CENTER.y + r * Math.sin(rad) }
}
const ticks = computed(() => {
  const list = []
  for (let v = 0; v <= MAX_DEDUCT_RATE; v += 2.5) {
    const outer = polar(v, RADIUS)
    const inner = polar(v, RADIUS - (v % 10 === 0 ? 10 : 5))
    list.push({ v, x1: inner.x, y1: inner.y, x2: outer.x, y2: outer.y })
  }
  return list
})
const labels = computed(() =>
  [0, 10, 20, 30].map((v) => {
    const p = polar(v, RADIUS + 14)
    return { v, x: p.x, y: p.y + 4 }
  })
)

/* ---- 左秤盘上的鲜叶（数量随毛重变化）---- */
const leaves = computed(() => {
  const count = Math.max(3, Math.min(12, Math.round(nz(form.grossWeightKg) / 32)))
  const list = []
  for (let i = 0; i < count; i++) {
    const t = (i + 1) / (count + 1)
    list.push({
      x: 44 + t * 32 + (i % 3) * 2,
      y: 100 - (i % 4) * 3 - t * 2,
      rx: 5.2,
      ry: 2.4,
      rot: (i % 2 === 0 ? -1 : 1) * (18 + (i % 5) * 9),
      fill: i % 3 === 0 ? '#7ea55f' : i % 3 === 1 ? '#5b8b3f' : '#a6c088'
    })
  }
  return list
})

const totalDeduct = computed(() => rows.value.reduce((s, r) => s + nz(r.deductWeightKg), 0))

function statusName(s) {
  return { ACCEPTED: '合格入厂', DEDUCTED: '扣重验收', REJECTED: '退回' }[s] || s || '-'
}

async function load() {
  try {
    const res = await intakeApi.list()
    rows.value = res && res.data ? res.data : []
  } catch (e) {
    ElMessage.error('加载当日流水失败：' + e.message)
  }
}

async function loadVocab() {
  try {
    const res = await intakeApi.vocab()
    const v = res && res.data ? res.data : null
    if (!v) return
    grades.value = v.leafGrades || grades.value
    priceMap.value = v.leafGradePrice || priceMap.value
  } catch (e) {
    /* 词典加载失败用内置默认值 */
  }
}

async function submit() {
  if (!form.supplierName || !String(form.supplierName).trim()) {
    ElMessage.warning('请填写供应商 / 茶农')
    return
  }
  if (nz(form.grossWeightKg) <= 0) {
    ElMessage.warning('毛重必须大于 0')
    return
  }
  if (deductRate.value > MAX_DEDUCT_RATE) {
    ElMessage.error(`扣重比例 ${deductRate.value.toFixed(2)}% 超过上限 ${MAX_DEDUCT_RATE}%，请整批退回`)
    return
  }
  submitting.value = true
  try {
    const res = await intakeApi.create({
      supplierName: form.supplierName,
      plotCode: form.plotCode,
      pickDate: form.pickDate,
      leafGrade: form.leafGrade,
      grossWeightKg: nz(form.grossWeightKg),
      moisturePct: nz(form.moisturePct),
      impurityPct: nz(form.impurityPct),
      inspector: form.inspector || undefined
    })
    const saved = res && res.data ? res.data : null
    ElMessage.success(res && res.message ? res.message : '验收完成')
    if (saved) rows.value.unshift(saved)
  } catch (e) {
    ElMessage.error(e.message || '验收失败')
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.supplierName = ''
  form.plotCode = ''
  form.pickDate = todayStr
  form.leafGrade = '一芽一叶'
  form.grossWeightKg = 180
  form.moisturePct = 76
  form.impurityPct = 2.4
  form.inspector = ''
}

onMounted(() => {
  loadVocab()
  load()
})
</script>

<style scoped>
.iv-body {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.iv-scale {
  flex: 0 0 320px;
}

.iv-form {
  flex: 1 1 380px;
  min-width: 320px;
}

.iv-svg {
  width: 100%;
  height: auto;
  display: block;
}

.iv-anim {
  transition: transform 0.5s cubic-bezier(0.34, 1.32, 0.5, 1);
}

.iv-tick-text {
  font-size: 9px;
  fill: #5d6353;
  text-anchor: middle;
}

.iv-dial-value {
  font-size: 18px;
  font-weight: 700;
  text-anchor: middle;
}

.iv-dial-label {
  font-size: 10px;
  fill: #5d6353;
  text-anchor: middle;
}

.iv-readout {
  margin-top: 6px;
  border-top: 1px dashed #c4b99f;
  padding-top: 8px;
  font-size: 12px;
  color: #5d6353;
}

.iv-readout > div {
  display: flex;
  justify-content: space-between;
  line-height: 2;
}

.iv-readout b {
  font-size: 15px;
  color: #33691e;
}

.iv-readout-total {
  border-top: 1px solid #ddd5c2;
  margin-top: 4px;
  padding-top: 4px;
}

.iv-readout-total b {
  font-size: 20px;
}

.iv-reason {
  margin-top: 8px;
  padding: 6px 8px;
  font-size: 12px;
  color: #33691e;
  background: #e7efdd;
  border: 1px solid #bcd0a6;
  border-radius: 4px;
}

.iv-reason-warn {
  color: #b45309;
  background: #fdf3e3;
  border-color: #e0c48f;
}

.iv-alert {
  margin-bottom: 10px;
}

.iv-actions {
  display: flex;
  gap: 8px;
}
</style>
