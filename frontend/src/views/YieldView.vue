<template>
  <div class="tf-page">
    <div class="tf-head">
      <h2>产出率与损耗统计</h2>
      <span class="tf-sub">与工单详情同一条数量时间线：总投入 = 最终产量 + 累计损耗 + 当前在制量（+ 旧批未登记差）</span>
    </div>

    <section class="tf-card">
      <div class="tf-card-title">
        按茶类汇总
        <el-button size="small" style="margin-left: 10px" @click="load">刷新</el-button>
      </div>
      <el-table :data="rows" size="small" border stripe empty-text="暂无数据">
        <el-table-column label="茶类" width="100">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="toggle(row.teaType)">
              {{ row.teaTypeName }}<span class="yd-arrow">{{ expanded[row.teaType] ? ' ▾' : ' ▸' }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="batchCount" label="批次数" width="80" align="center" />
        <el-table-column label="在制 / 完成" width="100" align="center">
          <template #default="{ row }">{{ row.processingBatchCount }} / {{ row.doneBatchCount }}</template>
        </el-table-column>
        <el-table-column label="总投入 kg" width="110" align="right">
          <template #default="{ row }">{{ num(row.inputQtyKg) }}</template>
        </el-table-column>
        <el-table-column label="最终产量 kg" width="120" align="right">
          <template #default="{ row }"><b>{{ num(row.finalOutputQtyKg) }}</b></template>
        </el-table-column>
        <el-table-column label="累计损耗 kg" width="120" align="right">
          <template #default="{ row }"><span class="pc-loss">{{ num(row.accumulatedLossQtyKg) }}</span></template>
        </el-table-column>
        <el-table-column label="当前在制 kg" width="120" align="right">
          <template #default="{ row }">{{ num(row.workInProcessQtyKg) }}</template>
        </el-table-column>
        <el-table-column label="旧批未登记差 kg" width="140" align="right">
          <template #default="{ row }">
            <span v-if="num(row.unrecordedGapQtyKg) !== '0.00'" title="旧批次中间工序未过磅，单列而不是按零损耗">
              {{ num(row.unrecordedGapQtyKg) }}
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="核对差额 kg" width="110" align="right">
          <template #default="{ row }">
            <el-tag size="small" :type="num(row.reconcileGapQtyKg) === '0.00' ? 'success' : 'danger'" effect="plain">
              {{ num(row.reconcileGapQtyKg) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="产出率 %" width="100" align="right">
          <template #default="{ row }"><b>{{ num(row.yieldRatePct) }}%</b></template>
        </el-table-column>
        <el-table-column label="损耗率 %" width="100" align="right">
          <template #default="{ row }">{{ num(row.lossRatePct) }}%</template>
        </el-table-column>
        <el-table-column label="对账" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.reconcileStatus === 'BALANCED' ? 'success' : 'warning'" effect="plain">
              {{ row.reconcileStatus === 'BALANCED' ? '已对平' : '部分未登记' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="yd-note">
        口径说明：产出率＝最终产量/总投入（在制批次只投未产，随完工进度上升）；损耗率＝累计损耗/（最终产量+累计损耗+在制量）。
        所有数字逐批取自工序在制量交接时间线，点开茶类可核对每个批次的逐道投入、产出与损耗。
      </div>
    </section>

    <section v-for="tea in expandedTeaList" :key="tea" v-show="expanded[tea]" class="tf-card">
      <div class="tf-card-title">{{ teaName(tea) }} · 批次数量时间线</div>
      <el-table :data="batchRows(tea)" size="small" border stripe empty-text="暂无批次"
        row-key="batchNo" :span-method="spanTimeline">
        <el-table-column prop="batchNo" label="批次" width="215" />
        <el-table-column label="口径" width="92">
          <template #default="{ row }">
            <el-tag v-if="row.stepIndex === 0" size="small" :type="modeType(row)" effect="plain">{{ modeText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="序" width="45" align="center">
          <template #default="{ row }">{{ row.stepIndex + 1 }}</template>
        </el-table-column>
        <el-table-column prop="stepNameCn" label="工序" width="78" />
        <el-table-column label="投入 kg" width="85" align="right">
          <template #default="{ row }">{{ num(row.inputQtyKg) }}</template>
        </el-table-column>
        <el-table-column label="产出 kg" width="90" align="right">
          <template #default="{ row }">
            <b v-if="row.outputQtyKg != null">{{ num(row.outputQtyKg) }}</b>
            <span v-else class="yd-muted">未过磅</span>
          </template>
        </el-table-column>
        <el-table-column label="损耗 kg" width="85" align="right">
          <template #default="{ row }">
            <span v-if="row.lossQtyKg != null" :class="{ 'pc-loss': row.overLimit }">{{ num(row.lossQtyKg) }}</span>
            <span v-else class="yd-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="损耗率" width="90" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.lossPct != null" size="small" :type="row.overLimit ? 'danger' : 'success'" effect="plain">
              {{ num(row.lossPct) }}%
            </el-tag>
            <span v-else class="yd-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="交接/状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="批次对账" min-width="260">
          <template #default="{ row }">
            <template v-if="row.stepIndex === 0">
              总投入 <b>{{ num(row.balance.totalInputKg) }}</b>
              = 产量 <b>{{ num(row.balance.finalOutputKg) }}</b>
              + 损耗 <b :class="{ 'pc-loss': true }">{{ num(row.balance.accumulatedLossKg) }}</b>
              + 在制 <b>{{ num(row.balance.workInProcessKg) }}</b>
              <template v-if="num(row.balance.unrecordedGapKg) !== '0.00'">
                + 未登记 <b>{{ num(row.balance.unrecordedGapKg) }}</b>
              </template>
              ·
              <el-tag size="small" :type="row.balance.reconcileStatus === 'BALANCED' ? 'success' : row.balance.reconcileStatus === 'PARTIAL' ? 'warning' : 'danger'" effect="plain">
                {{ row.balance.reconcileStatusName }}
              </el-tag>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { processApi } from '../api'

const TEA_NAMES = { GREEN: '绿茶', RED: '红茶', OOLONG: '乌龙茶', WHITE: '白茶', PUER: '普洱茶' }
const TEA_ORDER = ['GREEN', 'RED', 'OOLONG', 'WHITE', 'PUER']

const rows = ref([])
const orders = ref([])
const expanded = reactive({ GREEN: true })

const num = (v, d = 2) => (v === null || v === undefined ? '-' : Number(v).toFixed(d))
const teaName = (t) => TEA_NAMES[t] || t

async function load() {
  try {
    const [sumRes, listRes] = await Promise.all([processApi.yieldByTeaType(), processApi.list()])
    rows.value = sumRes && sumRes.data ? sumRes.data : []
    orders.value = listRes && listRes.data ? listRes.data : []
  } catch (e) {
    ElMessage.error(e.message || '统计加载失败')
  }
}

function toggle(tea) {
  expanded[tea] = !expanded[tea]
}

const expandedTeaList = TEA_ORDER

function batchRows(tea) {
  const flat = []
  for (const o of orders.value.filter((x) => x.teaType === tea)) {
    for (const n of o.qtyTimeline || []) {
      flat.push({ ...n, batchNo: o.batchNo, balance: o.qtyBalance, qtyMode: o.qtyMode,
        balanceStatus: o.qtyBalance ? o.qtyBalance.reconcileStatus : null,
        rowSpan: (o.qtyTimeline || []).length })
    }
  }
  return flat
}

function spanTimeline({ column, row }) {
  if (['batchNo', '口径', '批次对账'].includes(column.property)) {
    if (row.stepIndex === 0) return { rowspan: row.rowSpan, colspan: 1 }
    return { rowspan: 0, colspan: 0 }
  }
  return { rowspan: 1, colspan: 1 }
}

function modeType(row) {
  const mode = row.balance ? row.balance.qtyMode : row.qtyMode
  return mode === 'TRACKED' ? 'success' : mode === 'MIXED' ? 'warning' : 'info'
}

function modeText(row) {
  const mode = row.balance ? row.balance.qtyMode : row.qtyMode
  return { TRACKED: '逐道实称', LEGACY: '旧批派生', MIXED: '混合' }[mode] || mode
}

function statusType(status) {
  return {
    CONFIRMED: 'success',
    PENDING_REVIEW: 'danger',
    RETURNED: 'warning',
    LEGACY: 'info',
    NOT_HANDED: 'info',
    RUNNING: 'warning',
    NOT_STARTED: 'info'
  }[status] || 'info'
}

onMounted(load)
</script>

<style scoped>
.yd-arrow {
  font-size: 11px;
}

.yd-note {
  margin-top: 10px;
  font-size: 12px;
  color: #7a7f6e;
  line-height: 1.8;
}

.yd-muted {
  color: #9a9788;
}

.pc-loss {
  color: #b3261e;
  font-weight: 700;
}
</style>
