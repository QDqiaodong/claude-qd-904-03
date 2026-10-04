<template>
  <div class="tf-page">
    <div class="tf-head">
      <h2>设备清洗交接台</h2>
      <span class="tf-sub">工序收尾自动出单 · 清洗登记与报障 · 负责人确认后才能接下一批次</span>
    </div>

    <!-- ============ 交接口径说明 ============ -->
    <section class="tf-card ch-rule">
      <div class="ch-rule-item"><b>开工红线</b>清洗未开始 / 进行中 / 等待确认 / 故障冻结 / 转维修，一律不能用于下一批次开工。</div>
      <div class="ch-rule-item"><b>旧设备</b>没有历史交接单的设备沿用原状态（待机可开、故障不可），不统一锁死。</div>
      <div class="ch-rule-item"><b>报障取舍</b>确认前报障 → 立即冻结、本次清洗作废、修后重洗；确认后报障 → 保留已确认结果并转维修、复验放行。</div>
      <div class="ch-rule-item"><b>并发口径</b>多人同时确认 / 报障只保留一个有效结论，后到者操作被驳回，留痕可查。</div>
    </section>

    <!-- ============ 设备列表（开工可用性） ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        设备列表 · 能否接新批次
        <small>口径与工序台、交接详情一致</small>
      </div>
      <el-table :data="boards" size="small" border stripe empty-text="暂无设备" @row-click="openByEquip" row-class-name="ch-row">
        <el-table-column prop="equipCode" label="编号" width="80" />
        <el-table-column prop="equipName" label="设备" min-width="150" />
        <el-table-column prop="equipType" label="类型" width="90" />
        <el-table-column prop="workshop" label="车间" width="90" />
        <el-table-column label="原状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="equipTagType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交接单" width="150">
          <template #default="{ row }">
            <el-link v-if="row.activeHandoverNo" type="primary" :underline="false" @click.stop="openDetail(row.activeHandoverNo)">
              {{ row.activeHandoverNo }} · {{ row.handoverStatusName }}
            </el-link>
            <span v-else-if="row.hasHandover" class="ch-muted">上次已确认</span>
            <el-tag v-else size="small" type="info">旧设备 · 沿用原状态</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handoverBatchNo" label="上次批次" width="200">
          <template #default="{ row }">{{ row.handoverBatchNo || row.currentBatchNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="可否开工" width="240">
          <template #default="{ row }">
            <el-tag v-if="row.availableForNextBatch" size="small" type="success">可接新批次</el-tag>
            <el-tag v-else size="small" type="danger">{{ row.unavailableReason }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ============ 交接单列表 ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        清洗交接单
        <div>
          <el-radio-group v-model="statusFilter" size="small" @change="loadHandovers">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button v-for="s in statusFilters" :key="s.key" :label="s.key">{{ s.name }}</el-radio-button>
          </el-radio-group>
          <el-button size="small" style="margin-left: 10px" @click="loadAll">刷新</el-button>
        </div>
      </div>
      <el-table :data="handovers" size="small" border stripe empty-text="暂无交接单（工序结束后自动生成）"
        @row-click="(row) => openDetail(row.handoverNo)" row-class-name="ch-row">
        <el-table-column prop="handoverNo" label="交接单号" width="170" />
        <el-table-column prop="equipCode" label="设备" width="80" />
        <el-table-column prop="equipName" label="设备名称" min-width="140" />
        <el-table-column prop="batchNo" label="刚完成批次" width="200" />
        <el-table-column prop="stepNameCn" label="工序" width="70" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="handoverTagType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="residualRisk" label="残留风险" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.residualRisk" size="small" :type="riskType(row.residualRisk)" effect="plain">{{ row.residualRisk }}</el-tag>
            <span v-else class="ch-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="cleanMethod" label="清洗方式" min-width="120">
          <template #default="{ row }">{{ row.cleanMethod || '-' }}</template>
        </el-table-column>
        <el-table-column prop="cleaner" label="清洗人" width="80" />
        <el-table-column prop="confirmedBy" label="负责人" width="80" />
        <el-table-column label="开工" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.locked" size="small" type="danger">锁定不可开</el-tag>
            <el-tag v-else size="small" type="success">可以开工</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="形成时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ============ 交接详情抽屉 ============ -->
    <el-drawer v-model="detailVisible" size="620px" :title="detail ? '交接单 ' + detail.handoverNo : '交接详情'">
      <div v-if="detail" class="ch-detail">
        <el-alert :title="detail.lockReason" :type="detail.locked ? 'error' : 'success'" :closable="false" show-icon style="margin-bottom: 12px" />

        <div class="ch-d-sec">
          <div class="ch-d-title">交接对象</div>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="设备">{{ detail.equipCode }} {{ detail.equipName }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ detail.equipType }}</el-descriptions-item>
            <el-descriptions-item label="刚完成批次">{{ detail.batchNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="收尾工序">{{ detail.stepNameCn || '-' }}</el-descriptions-item>
            <el-descriptions-item label="单据状态">
              <el-tag size="small" :type="handoverTagType(detail.status)">{{ detail.statusName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="形成人 / 时间">{{ detail.createdBy || '-' }} · {{ fmtTime(detail.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="ch-d-sec">
          <div class="ch-d-title">清洗登记</div>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="残留风险">
              <el-tag v-if="detail.residualRisk" size="small" :type="riskType(detail.residualRisk)" effect="plain">{{ detail.residualRisk }}</el-tag>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="清洗方式">{{ detail.cleanMethod || '-' }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ fmtTime(detail.cleanStartTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ fmtTime(detail.cleanEndTime) }}</el-descriptions-item>
            <el-descriptions-item label="清洗人员">{{ detail.cleaner || '-' }}</el-descriptions-item>
            <el-descriptions-item label="负责人 / 确认时间">{{ detail.confirmedBy || '-' }} · {{ fmtTime(detail.confirmedAt) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 冲突 / 故障 / 维修 -->
        <div v-if="detail.faultNote || detail.decisionNote || detail.repairNote" class="ch-d-sec">
          <div class="ch-d-title">故障与取舍</div>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item v-if="detail.faultNote" label="故障现象">{{ detail.faultNote }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.decisionNote" label="本次取舍与开工影响">
              <span class="ch-decision">{{ detail.decisionNote }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.repairNote" label="维修 / 复验">{{ detail.repairNote }}（{{ detail.repairer || '-' }} · {{ fmtTime(detail.repairedAt) }}）</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 操作区：按状态渲染 -->
        <div class="ch-d-sec">
          <div class="ch-d-title">操作</div>

          <!-- PENDING：开始清洗 -->
          <el-form v-if="detail.status === 'PENDING'" label-width="92px" size="small">
            <el-form-item label="清洗人员">
              <el-input v-model="actionForm.operator" placeholder="姓名" style="width: 200px" />
            </el-form-item>
            <el-form-item label="残留风险" required>
              <el-radio-group v-model="actionForm.residualRisk">
                <el-radio v-for="r in RISKS" :key="r" :label="r">{{ r }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="清洗方式" required>
              <el-select v-model="actionForm.cleanMethod" placeholder="选择清洗方式" style="width: 240px">
                <el-option v-for="m in METHODS" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
            <el-form-item label="开始时间">
              <el-date-picker v-model="actionForm.cleanStartTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="留空取现在" style="width: 240px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="acting" @click="doAction('start')">开始清洗</el-button>
              <el-button type="danger" plain @click="showFault = !showFault">报故障</el-button>
            </el-form-item>
          </el-form>

          <!-- IN_PROGRESS：完成 -->
          <el-form v-else-if="detail.status === 'IN_PROGRESS'" label-width="92px" size="small">
            <el-form-item label="清洗人员">
              <el-input v-model="actionForm.operator" :placeholder="detail.cleaner || '姓名'" style="width: 200px" />
            </el-form-item>
            <el-form-item label="结束时间" required>
              <el-date-picker v-model="actionForm.cleanEndTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="留空取现在" style="width: 240px" />
            </el-form-item>
            <el-form-item>
              <el-button type="success" :loading="acting" @click="doAction('complete')">标记清洗完成</el-button>
              <el-button type="danger" plain @click="showFault = !showFault">报故障</el-button>
            </el-form-item>
          </el-form>

          <!-- AWAIT_CONFIRM：负责人确认 -->
          <div v-else-if="detail.status === 'AWAIT_CONFIRM'">
            <el-input v-model="actionForm.operator" placeholder="负责人姓名" style="width: 200px; margin-bottom: 10px" size="small" />
            <el-button type="primary" :loading="acting" @click="doAction('confirm')">确认设备恢复可用</el-button>
            <el-button type="danger" plain @click="showFault = !showFault">报故障</el-button>
          </div>

          <!-- FAULT_FREEZE：修后重洗 -->
          <el-form v-else-if="detail.status === 'FAULT_FREEZE'" label-width="92px" size="small">
            <el-alert title="确认前报障已立即冻结，本次清洗结论作废；维修合格后须重新清洗并重新确认，期间不能开工。"
              type="error" :closable="false" show-icon style="margin-bottom: 10px" />
            <el-form-item label="维修 / 填报人">
              <el-input v-model="actionForm.operator" placeholder="姓名" style="width: 200px" />
            </el-form-item>
            <el-form-item label="维修情况" required>
              <el-input v-model="actionForm.repairNote" type="textarea" :rows="2" placeholder="故障件、处理措施、试机情况" style="max-width: 380px" />
            </el-form-item>
            <el-form-item>
              <el-button type="warning" :loading="acting" @click="doAction('reopen')">维修合格，重新清洗</el-button>
            </el-form-item>
          </el-form>

          <!-- KEEP_AND_REPAIR：复验放行 -->
          <el-form v-else-if="detail.status === 'KEEP_AND_REPAIR'" label-width="92px" size="small">
            <el-alert title="已保留此前确认的清洗结果并转维修；复验合格后设备恢复待机可开工，不推翻既往确认。"
              type="warning" :closable="false" show-icon style="margin-bottom: 10px" />
            <el-form-item label="复验负责人">
              <el-input v-model="actionForm.operator" placeholder="姓名" style="width: 200px" />
            </el-form-item>
            <el-form-item label="复验情况" required>
              <el-input v-model="actionForm.repairNote" type="textarea" :rows="2" placeholder="复验项目与结论" style="max-width: 380px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="acting" @click="doAction('recheck')">复验合格放行</el-button>
            </el-form-item>
          </el-form>

          <!-- CONFIRMED -->
          <div v-else>
            <el-alert title="负责人已确认，设备恢复可用。若此后再发现故障，报障将保留本次确认结果并转维修。"
              type="success" :closable="false" show-icon style="margin-bottom: 10px" />
            <el-button type="danger" plain size="small" @click="showFault = !showFault">确认后再报故障</el-button>
          </div>

          <!-- 报故障表单（各状态通用） -->
          <el-form v-if="showFault" label-width="92px" size="small" style="margin-top: 12px; border-top: 1px dashed var(--line); padding-top: 12px">
            <el-form-item label="报告人">
              <el-input v-model="actionForm.operator" placeholder="姓名" style="width: 200px" />
            </el-form-item>
            <el-form-item label="故障现象" required>
              <el-input v-model="actionForm.faultNote" type="textarea" :rows="2" placeholder="如：揉桶异响、温控失灵、漏料" style="max-width: 380px" />
            </el-form-item>
            <el-form-item>
              <el-button type="danger" :loading="acting" @click="doAction('fault')">提交报障</el-button>
              <el-button @click="showFault = false">取消</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 操作留痕 -->
        <div class="ch-d-sec">
          <div class="ch-d-title">操作留痕（{{ detail.logs.length }}）</div>
          <el-timeline>
            <el-timeline-item v-for="(l, i) in detail.logs" :key="i" :timestamp="fmtTime(l.createdAt)" placement="top"
              :type="l.result === 'REJECTED' ? 'info' : 'success'">
              <el-tag size="small" :type="l.result === 'REJECTED' ? 'info' : 'success'" effect="plain" style="margin-right: 6px">
                {{ l.result === 'REJECTED' ? '已驳回' : '已生效' }}
              </el-tag>
              <b>{{ actionName(l.action) }}</b> · {{ l.operator }}
              <div class="ch-log-detail">{{ l.detail }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cleaningApi } from '../api'

const RISKS = ['低', '中', '高']
const METHODS = ['清水冲洗', '食品级清洁剂', '高温蒸汽', '人工拆卸清扫', '专项清洗']
const statusFilters = [
  { key: 'PENDING', name: '未开始' },
  { key: 'IN_PROGRESS', name: '进行中' },
  { key: 'AWAIT_CONFIRM', name: '等待确认' },
  { key: 'FAULT_FREEZE', name: '故障冻结' },
  { key: 'KEEP_AND_REPAIR', name: '转维修' },
  { key: 'CONFIRMED', name: '已确认' }
]

const boards = ref([])
const handovers = ref([])
const statusFilter = ref('')
const detailVisible = ref(false)
const detail = ref(null)
const acting = ref(false)
const showFault = ref(false)
const actionForm = reactive({
  operator: '',
  residualRisk: '',
  cleanMethod: '',
  cleanStartTime: '',
  cleanEndTime: '',
  faultNote: '',
  repairNote: ''
})
let timer = null

const fmtTime = (s) => (s ? String(s).replace('T', ' ').slice(0, 16) : '-')

function equipTagType(status) {
  return { RUNNING: '', IDLE: 'success', CLEANING: 'warning', PENDING_CONFIRM: 'warning', FAULT: 'danger' }[status] || 'info'
}
function handoverTagType(status) {
  return {
    PENDING: 'info',
    IN_PROGRESS: 'warning',
    AWAIT_CONFIRM: 'warning',
    FAULT_FREEZE: 'danger',
    KEEP_AND_REPAIR: 'danger',
    CONFIRMED: 'success'
  }[status] || 'info'
}
function riskType(r) {
  return { 低: 'success', 中: 'warning', 高: 'danger' }[r] || 'info'
}
function actionName(a) {
  return {
    CREATE: '工序收尾形成交接单',
    START: '开始清洗',
    COMPLETE: '清洗完成',
    REPORT_FAULT: '报故障',
    CONFIRM: '负责人确认',
    REOPEN: '修复后重新清洗',
    RECHECK: '复验放行'
  }[a] || a
}

async function loadBoards() {
  try {
    const res = await cleaningApi.board()
    boards.value = res?.data || []
  } catch (e) {
    // 看板刷新失败不阻断
  }
}

async function loadHandovers() {
  try {
    const res = await cleaningApi.list(statusFilter.value)
    handovers.value = res?.data || []
  } catch (e) {
    ElMessage.error('加载交接单失败：' + e.message)
  }
}

async function loadAll() {
  await Promise.all([loadBoards(), loadHandovers()])
}

function resetForm() {
  Object.assign(actionForm, {
    operator: detail.value?.cleaner || detail.value?.confirmedBy || '',
    residualRisk: detail.value?.residualRisk || '',
    cleanMethod: detail.value?.cleanMethod || '',
    cleanStartTime: '',
    cleanEndTime: '',
    faultNote: '',
    repairNote: ''
  })
  showFault.value = false
}

async function openDetail(no) {
  try {
    const res = await cleaningApi.detail(no)
    detail.value = res?.data || null
    resetForm()
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载交接详情失败')
  }
}

function openByEquip(row) {
  if (row.activeHandoverNo) openDetail(row.activeHandoverNo)
}

const ACTION_LABEL = {
  start: '开始清洗',
  complete: '标记清洗完成',
  fault: '提交报障',
  confirm: '确认恢复可用',
  reopen: '维修合格重新清洗',
  recheck: '复验放行'
}
const NEEDS_CONFIRM = { fault: true, confirm: true, reopen: true, recheck: true }

function payload(action) {
  const base = { operator: actionForm.operator }
  if (action === 'start') {
    Object.assign(base, {
      residualRisk: actionForm.residualRisk,
      cleanMethod: actionForm.cleanMethod,
      cleanStartTime: actionForm.cleanStartTime || undefined
    })
  }
  if (action === 'complete') {
    Object.assign(base, {
      cleanEndTime: actionForm.cleanEndTime || undefined,
      residualRisk: actionForm.residualRisk || undefined,
      cleanMethod: actionForm.cleanMethod || undefined
    })
  }
  if (action === 'fault') base.faultNote = actionForm.faultNote
  if (action === 'reopen' || action === 'recheck') base.repairNote = actionForm.repairNote
  return base
}

async function doAction(action) {
  if (!actionForm.operator) {
    ElMessage.warning('请填写操作人')
    return
  }
  if (NEEDS_CONFIRM[action]) {
    try {
      await ElMessageBox.confirm(`确认执行「${ACTION_LABEL[action]}」？多人同时操作时只有先到达的结论生效。`, '操作确认', {
        type: 'warning',
        confirmButtonText: '确认',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }
  acting.value = true
  try {
    const res = await cleaningApi[action](detail.value.handoverNo, payload(action))
    ElMessage.success(res?.message || '操作已生效')
    detailVisible.value = false
    await loadAll()
  } catch (e) {
    ElMessage.error(e.message || '操作未生效')
    // 冲突后到者：刷新详情，让其看到先前已生效状态与驳回留痕
    try {
      const fresh = await cleaningApi.detail(detail.value.handoverNo)
      detail.value = fresh?.data || detail.value
    } catch {
      /* ignore */
    }
  } finally {
    acting.value = false
  }
}

onMounted(() => {
  loadAll()
  timer = setInterval(loadAll, 15000)
})
onUnmounted(() => timer && clearInterval(timer))
</script>

<style scoped>
.ch-rule {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #5d6353;
  line-height: 1.6;
}

.ch-rule-item {
  flex: 1 1 300px;
}

.ch-rule b {
  display: inline-block;
  color: #33691e;
  margin-right: 6px;
}

.ch-row {
  cursor: pointer;
}

.ch-muted {
  color: #9a9788;
  font-size: 12px;
}

.ch-detail {
  padding: 0 4px;
}

.ch-d-sec {
  margin-bottom: 16px;
}

.ch-d-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
  border-left: 3px solid #33691e;
  padding-left: 8px;
}

.ch-decision {
  color: #b45309;
  line-height: 1.7;
}

.ch-log-detail {
  font-size: 12px;
  color: #5d6353;
  margin-top: 2px;
}
</style>
