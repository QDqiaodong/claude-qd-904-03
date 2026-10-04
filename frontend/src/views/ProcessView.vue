<template>
  <div class="tf-page">
    <div class="tf-head">
      <h2>制茶工序流转</h2>
      <span class="tf-sub">按茶类工序序列推进 · 不能跳步 · 工序参数不达标不允许结束</span>
    </div>

    <!-- ============ 批次选择 + 开单 ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        加工批次
        <el-button link type="primary" size="small" @click="showCreate = !showCreate">
          {{ showCreate ? '收起' : '新建制茶工单' }}
        </el-button>
      </div>

      <el-form size="small" inline>
        <el-form-item label="批次">
          <el-select v-model="batchNo" style="width: 300px" @change="loadDetail">
            <el-option
              v-for="o in orders"
              :key="o.batchNo"
              :label="o.batchNo + '（' + o.teaTypeName + ' ' + o.inputQtyKg + 'kg）'"
              :value="o.batchNo"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="loadAll">刷新</el-button>
        </el-form-item>
      </el-form>

      <el-form v-if="showCreate" size="small" inline class="pc-create">
        <el-form-item label="茶类">
          <el-select v-model="createForm.teaType" style="width: 120px">
            <el-option v-for="t in teaTypeList" :key="t" :label="teaName(t)" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="投料 kg">
          <el-input-number v-model="createForm.inputQtyKg" :min="0.01" :max="9999" :step="10" :precision="2" style="width: 130px" />
        </el-form-item>
        <el-form-item label="来源槽">
          <el-select v-model="createForm.sourceTroughNo" clearable style="width: 140px">
            <el-option v-for="t in troughs" :key="t.troughNo" :label="t.troughNo" :value="t.troughNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="制茶师傅">
          <el-input v-model="createForm.operator" style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="creating" @click="createOrder">开单</el-button>
        </el-form-item>
        <div class="pc-create-seq">工序序列：{{ flowPreview }}</div>
      </el-form>

      <div v-if="detail" class="pc-meta">
        <span>茶类 <b>{{ detail.teaTypeName }}</b></span>
        <span>工序序列 <b>{{ detail.flowNames.join(' → ') }}</b></span>
        <span>批次总投入 <b>{{ num(detail.inputQtyKg) }} kg</b></span>
        <span>可用在制量 <b :class="{ 'pc-wip-hold': wipHeld }">{{ num(detail.availableWipKg) }} kg</b></span>
        <span>最终产量 <b>{{ num(detail.outputQtyKg) }} kg</b></span>
        <span>数量口径 <b>{{ detail.qtyModeName }}</b></span>
        <span>来源槽 <b>{{ detail.sourceTroughNo || '-' }}</b></span>
        <span>师傅 <b>{{ detail.operator }}</b></span>
        <span>状态 <b>{{ detail.status === 'DONE' ? '已完成' : '在制' }}</b></span>
      </div>

      <!-- 数量对账条：总投入 = 最终产量 + 累计损耗 + 在制量（+ 未登记差） -->
      <div v-if="detail && detail.qtyBalance" class="pc-balance">
        <span>数量核对：</span>
        <span>总投入 <b>{{ num(detail.qtyBalance.totalInputKg) }}</b></span>
        <span>= 最终产量 <b>{{ num(detail.qtyBalance.finalOutputKg) }}</b></span>
        <span>+ 累计损耗 <b class="pc-loss">{{ num(detail.qtyBalance.accumulatedLossKg) }}</b></span>
        <span>+ 在制量 <b>{{ num(detail.qtyBalance.workInProcessKg) }}</b></span>
        <template v-if="num(detail.qtyBalance.unrecordedGapKg) !== '0.00'">
          <span>+ 未登记差 <b title="旧批次中间工序未过磅，单列不按零损耗">{{ num(detail.qtyBalance.unrecordedGapKg) }}</b></span>
        </template>
        <el-tag size="small" :type="balanceTagType(detail.qtyBalance.reconcileStatus)" effect="plain">
          {{ detail.qtyBalance.reconcileStatusName }}
        </el-tag>
        <span v-if="detail.qtyBalance.finalYieldRatePct != null">
          累计产出率 <b>{{ num(detail.qtyBalance.finalYieldRatePct) }}%</b>
        </span>
      </div>
    </section>

    <!-- ============ 横向工序链 ============ -->
    <section v-if="detail" class="tf-card">
      <div class="tf-card-title">
        工序链
        <small>当前：{{ detail.currentStepName }}</small>
      </div>
      <div class="pc-chain">
        <template v-for="(name, i) in detail.flowNames" :key="i">
          <div
            class="pc-node"
            :class="{ 'is-done': i < curIndex, 'is-current': i === curIndex, 'is-future': i > curIndex, 'is-sel': i === selIndex, 'is-hold': nodeHold(i) }"
            @click="selIndex = i"
          >
            <div class="pc-node-dot">
              <span v-if="i < curIndex">✓</span>
              <span v-else-if="i === curIndex">{{ nodeDot(i) }}</span>
              <span v-else>{{ i + 1 }}</span>
            </div>
            <div class="pc-node-name">{{ name }}</div>
            <div class="pc-node-state">{{ nodeStateText(i) }}</div>
          </div>
          <div v-if="i < detail.flowNames.length - 1" class="pc-arrow">→</div>
        </template>
      </div>
    </section>

    <!-- ============ 数量时间线（详情与产出率统计同一条） ============ -->
    <section v-if="detail" class="tf-card">
      <div class="tf-card-title">
        在制量交接时间线
        <small>下道投入＝上道已确认产出 · 损耗率＝(投入−产出)/投入 · 超范围须复核</small>
      </div>
      <el-table :data="detail.qtyTimeline" size="small" border stripe
        :row-class-name="timelineRowClass" empty-text="暂无交接记录">
        <el-table-column label="序" width="50" align="center">
          <template #default="{ row }">{{ row.stepIndex + 1 }}</template>
        </el-table-column>
        <el-table-column label="工序" width="90">
          <template #default="{ row }">{{ row.stepNameCn }}</template>
        </el-table-column>
        <el-table-column label="交接单号" width="150">
          <template #default="{ row }">{{ row.handoverNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="投入 kg" width="90" align="right">
          <template #default="{ row }">{{ num(row.inputQtyKg) }}</template>
        </el-table-column>
        <el-table-column label="实际产出 kg" width="110" align="right">
          <template #default="{ row }">
            <b v-if="row.outputQtyKg != null">{{ num(row.outputQtyKg) }}</b>
            <span v-else class="pc-muted">未过磅</span>
          </template>
        </el-table-column>
        <el-table-column label="损耗 kg" width="90" align="right">
          <template #default="{ row }">
            <span v-if="row.lossQtyKg != null" :class="{ 'pc-loss': row.overLimit }">{{ num(row.lossQtyKg) }}</span>
            <span v-else class="pc-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="损耗率" width="110" align="right">
          <template #default="{ row }">
            <el-tooltip v-if="row.lossPct != null" :content="`正常范围 ${row.limitMinPct}% ~ ${row.limitMaxPct}%`" placement="top">
              <el-tag size="small" :type="row.overLimit ? 'danger' : 'success'" effect="plain">
                {{ num(row.lossPct) }}%
              </el-tag>
            </el-tooltip>
            <span v-else class="pc-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="lossReason" label="损耗原因" min-width="130">
          <template #default="{ row }">{{ row.lossReason || '—' }}</template>
        </el-table-column>
        <el-table-column prop="handoverTo" label="交接人" width="80">
          <template #default="{ row }">{{ row.handoverTo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="submittedBy" label="提交人" width="80">
          <template #default="{ row }">{{ row.submittedBy || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="112">
          <template #default="{ row }">
            <el-tag size="small" :type="qtyTagType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="复核 / 说明" min-width="180">
          <template #default="{ row }">
            <div v-if="row.status === 'PENDING_REVIEW'" class="pc-hold-text">
              超范围，待复核人结论；下道不得开工
            </div>
            <div v-else-if="row.status === 'RETURNED'" class="pc-hold-text">
              已退回：{{ row.returnedNote }}
              <el-button link type="primary" size="small" @click="openCorrect(row)">更正重交</el-button>
            </div>
            <div v-else-if="row.reviewNote" :title="row.reviewNote">
              {{ row.reviewedBy }}：{{ row.reviewNote }}
            </div>
            <div v-else-if="row.legacyNote" class="pc-muted" :title="row.legacyNote">{{ row.legacyNote }}</div>
            <span v-else class="pc-muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="currentHoldNode" class="pc-hold-banner">
        ⛔ 当前工序「{{ currentHoldNode.stepNameCn }}」交接单 {{ currentHoldNode.handoverNo }}
        为「{{ currentHoldNode.statusName }}」，
        <template v-if="currentHoldNode.status === 'PENDING_REVIEW'">等待复核人接受异常或退回更正，</template>
        <template v-else>等待班组按退回意见更正重量与损耗说明，</template>
        在此之前下道工序不能开工（可用在制量保持 {{ num(detail.availableWipKg) }} kg 不前进）。
      </div>
    </section>

    <!-- ============ 工序参数面板 ============ -->
    <section v-if="detail" class="tf-card pc-panel">
      <div class="tf-card-title">
        {{ detail.flowNames[selIndex] }} · 参数面板
        <small>{{ panelHint }}</small>
      </div>

      <div class="pc-panel-body">
        <el-form label-width="104px" size="small" class="pc-params">
          <el-form-item label="使用设备">
            <el-select v-model="form.equipmentCode" clearable filterable style="width: 300px"
              :placeholder="equipSelectHint">
              <el-option
                v-for="e in stepEquipments"
                :key="e.equipCode"
                :label="e.equipCode + ' ' + e.equipName + (e.availableForNextBatch ? '（可开工）' : '（' + (e.unavailableReason || '不可开工') + '）')"
                :value="e.equipCode"
                :disabled="!e.availableForNextBatch"
              />
            </el-select>
            <span v-if="lockedSelected" class="pc-tip" style="color: #b3261e">
              {{ lockedSelected.equipCode }}：{{ lockedSelected.unavailableReason }}，不能开工
            </span>
          </el-form-item>

          <template v-for="f in fields" :key="f.key">
            <el-form-item :label="f.label + (f.unit ? ' ' + f.unit : '')">
              <el-input-number
                v-model="form[f.key]"
                :min="f.min"
                :max="f.max"
                :step="f.step"
                :precision="f.precision"
                style="width: 200px"
              />
              <span v-if="f.tip" class="pc-tip">{{ f.tip }}</span>
            </el-form-item>
          </template>

          <el-form-item label="参数速记">
            <el-input v-model="form.paramProfile" placeholder="如：滚筒温度 235℃，投叶 180kg" style="width: 320px" />
          </el-form-item>
          <el-form-item label="操作人">
            <el-input v-model="form.operator" style="width: 200px" />
          </el-form-item>
          <el-form-item label="异常记录">
            <el-input v-model="form.abnormalNote" placeholder="无异常可留空" style="width: 320px" />
          </el-form-item>

          <!-- 结束工序登记：实际产出 / 损耗原因 / 交接人 -->
          <template v-if="showQtyFields">
            <el-form-item label="上道转入 kg">
              <span class="pc-wip">
                {{ num(currentStepInput) }} kg
                <small>（{{ wipBasisText }}）</small>
              </span>
            </el-form-item>
            <el-form-item label="实际产出 kg">
              <el-input-number v-model="form.actualOutputQtyKg" :min="0" :max="99999" :step="1" :precision="2"
                style="width: 200px" />
              <el-button link type="primary" size="small" @click="applySuggestedOutput">按损耗范围推算</el-button>
            </el-form-item>
            <el-form-item label="损耗率">
              <span v-if="liveLoss != null" class="pc-loss-preview" :class="liveLoss.over ? 'pc-loss' : ''">
                {{ liveLoss.pctText }}%
                <small>（正常 {{ liveLoss.limitText }}%；损耗 {{ liveLoss.lossText }} kg）</small>
              </span>
              <span v-else class="pc-muted">填写实际产出后自动计算</span>
            </el-form-item>
            <el-form-item label="损耗原因">
              <el-select v-model="form.lossReason" filterable allow-create style="width: 320px"
                placeholder="选择或填写损耗原因">
                <el-option v-for="r in LOSS_REASONS" :key="r" :label="r" :value="r" />
              </el-select>
            </el-form-item>
            <el-form-item label="交接人">
              <el-input v-model="form.handoverTo" placeholder="下道工序接班人" style="width: 200px" />
            </el-form-item>
            <el-form-item v-if="liveLoss && liveLoss.over" label=" ">
              <el-alert type="warning" :closable="false" show-icon style="width: 320px"
                title="损耗超出本茶类本工序正常范围：结束后进入待复核，复核人接受异常或退回更正前，下道工序不能开工" />
            </el-form-item>
          </template>
        </el-form>

        <div class="pc-actions">
          <el-button type="primary" size="large" :loading="starting" :disabled="!canStart" @click="startStep">
            开始本工序
          </el-button>
          <el-button type="success" size="large" :loading="finishing" :disabled="!canFinish" @click="finishStep">
            结束本工序
          </el-button>
          <div class="pc-hint" :class="{ 'pc-hint-bad': !canStart && !canFinish }">{{ panelHint }}</div>
        </div>
      </div>
    </section>

    <!-- ============ 工序执行记录 ============ -->
    <section v-if="detail" class="tf-card">
      <div class="tf-card-title">
        工序执行记录
        <small>批次 {{ detail.batchNo }} · 共 {{ steps.length }} 条</small>
      </div>
      <el-table :data="steps" size="small" border stripe empty-text="该批次还没有工序记录">
        <el-table-column label="序" width="50" align="center">
          <template #default="{ row }">{{ row.stepIndex + 1 }}</template>
        </el-table-column>
        <el-table-column label="工序" width="90">
          <template #default="{ row }">{{ stepName(row) }}</template>
        </el-table-column>
        <el-table-column prop="equipmentCode" label="设备" width="100" />
        <el-table-column label="开始" width="130">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束" width="130">
          <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="温度 ℃" width="90" align="right">
          <template #default="{ row }">{{ row.paramTempC == null ? '-' : row.paramTempC }}</template>
        </el-table-column>
        <el-table-column label="二段 ℃" width="80" align="right">
          <template #default="{ row }">{{ row.paramTemp2C == null ? '-' : row.paramTemp2C }}</template>
        </el-table-column>
        <el-table-column label="时长 min" width="90" align="right">
          <template #default="{ row }">{{ row.paramMinutes == null ? '-' : row.paramMinutes }}</template>
        </el-table-column>
        <el-table-column label="转速" width="80" align="right">
          <template #default="{ row }">{{ row.paramRpm == null ? '-' : row.paramRpm }}</template>
        </el-table-column>
        <el-table-column label="压力 kg" width="80" align="right">
          <template #default="{ row }">{{ row.paramPressure == null ? '-' : row.paramPressure }}</template>
        </el-table-column>
        <el-table-column label="投叶 kg" width="90" align="right">
          <template #default="{ row }">{{ row.paramLeafQty == null ? '-' : row.paramLeafQty }}</template>
        </el-table-column>
        <el-table-column label="湿度 %" width="80" align="right">
          <template #default="{ row }">{{ row.humidityPct == null ? '-' : row.humidityPct }}</template>
        </el-table-column>
        <el-table-column prop="paramProfile" label="参数速记" min-width="150" />
        <el-table-column prop="abnormalNote" label="异常" min-width="110" />
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column label="数量交接" width="112">
          <template #default="{ row }">
            <el-tag v-if="qtyNodeOf(row.stepIndex) && qtyNodeOf(row.stepIndex).handoverNo" size="small"
              :type="qtyTagType(qtyNodeOf(row.stepIndex).status)">
              {{ qtyNodeOf(row.stepIndex).statusName }}
            </el-tag>
            <span v-else class="pc-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'RUNNING' ? 'warning' : 'success'">
              {{ row.status === 'RUNNING' ? '执行中' : '已完成' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ============ 损耗复核看板 ============ -->
    <section class="tf-card">
      <div class="tf-card-title">
        损耗待复核 / 退回更正
        <small>超限交接须复核人结论后下道才能开工</small>
        <el-button size="small" style="margin-left: 10px" @click="loadPending">刷新</el-button>
      </div>
      <el-table :data="pendingList" size="small" border stripe empty-text="暂无待复核交接单（损耗在范围内会自动确认）">
        <el-table-column prop="handoverNo" label="交接单号" width="160" />
        <el-table-column prop="batchNo" label="批次" width="210" />
        <el-table-column label="茶类/工序" width="120">
          <template #default="{ row }">{{ row.teaTypeName }} · {{ row.stepNameCn }}</template>
        </el-table-column>
        <el-table-column label="投入→产出 kg" width="170" align="right">
          <template #default="{ row }">
            {{ num(row.inputQtyKg) }} → <b>{{ num(row.outputQtyKg) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="损耗" width="120" align="right">
          <template #default="{ row }">
            <span class="pc-loss">{{ num(row.lossQtyKg) }} kg / {{ num(row.lossPct) }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="正常范围" width="110" align="center">
          <template #default="{ row }">{{ num(row.limitMinPct, 1) }}%~{{ num(row.limitMaxPct, 1) }}%</template>
        </el-table-column>
        <el-table-column prop="lossReason" label="损耗原因" min-width="120" />
        <el-table-column label="提交/交接" width="140">
          <template #default="{ row }">{{ row.submittedBy }} → {{ row.handoverTo }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'PENDING_REVIEW' ? 'danger' : 'warning'">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING_REVIEW'">
              <el-button size="small" type="success" @click="openReview(row, true)">接受异常</el-button>
              <el-button size="small" type="danger" @click="openReview(row, false)">退回更正</el-button>
            </template>
            <template v-else>
              <el-button size="small" type="warning" @click="openCorrectFromBoard(row)">班组更正重交</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ============ 复核结论弹窗 ============ -->
    <el-dialog v-model="reviewVisible" :title="reviewAccept ? '复核 · 接受异常损耗' : '复核 · 退回班组更正'" width="520px">
      <div v-if="reviewRow" class="pc-dialog-body">
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="交接单">{{ reviewRow.handoverNo }} · {{ reviewRow.batchNo }} · {{ reviewRow.teaTypeName }}{{ reviewRow.stepNameCn }}</el-descriptions-item>
          <el-descriptions-item label="投入 / 产出">{{ num(reviewRow.inputQtyKg) }} kg / {{ num(reviewRow.outputQtyKg) }} kg</el-descriptions-item>
          <el-descriptions-item label="损耗">{{ num(reviewRow.lossQtyKg) }} kg，损耗率 {{ num(reviewRow.lossPct) }}%（正常 {{ num(reviewRow.limitMinPct, 1) }}%~{{ num(reviewRow.limitMaxPct, 1) }}%）</el-descriptions-item>
          <el-descriptions-item label="班组登记原因">{{ reviewRow.lossReason }}（提交人 {{ reviewRow.submittedBy }}，交接 {{ reviewRow.handoverTo }}）</el-descriptions-item>
        </el-descriptions>
        <el-input v-model="reviewForm.reviewer" placeholder="复核人" style="margin-top: 12px" />
        <el-input v-model="reviewForm.reviewNote" type="textarea" :rows="3" style="margin-top: 10px"
          :placeholder="reviewAccept ? '说明接受该异常损耗的理由（如：设备故障已核实、当批鲜叶偏老等）' : '指明需要更正的内容（如：请复核过磅读数、补充损耗说明）'" />
      </div>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button :type="reviewAccept ? 'success' : 'danger'" :loading="reviewing" @click="submitReview">
          {{ reviewAccept ? '确认接受，下道放行' : '确认退回，挂起工序' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 更正重交弹窗 ============ -->
    <el-dialog v-model="correctVisible" title="班组更正实际产出与损耗说明" width="520px">
      <div v-if="correctRow" class="pc-dialog-body">
        <el-alert v-if="correctRow.returnedNote" type="warning" :closable="false" show-icon
          :title="'退回意见：' + correctRow.returnedNote" style="margin-bottom: 12px" />
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="交接单">{{ correctRow.handoverNo }} · {{ correctRow.batchNo }} · {{ correctRow.teaTypeName }}{{ correctRow.stepNameCn }}</el-descriptions-item>
          <el-descriptions-item label="本步投入">{{ num(correctRow.inputQtyKg) }} kg（上道已确认产出，不可改）</el-descriptions-item>
          <el-descriptions-item label="原登记产出">{{ num(correctRow.outputQtyKg) }} kg（损耗率 {{ num(correctRow.lossPct) }}%）</el-descriptions-item>
        </el-descriptions>
        <el-input-number v-model="correctForm.actualOutputQtyKg" :min="0" :max="99999" :precision="2"
          placeholder="更正后的实际产出 kg" style="margin-top: 12px; width: 100%" />
        <el-select v-model="correctForm.lossReason" filterable allow-create style="margin-top: 10px; width: 100%"
          placeholder="更正后的损耗原因 / 说明">
          <el-option v-for="r in LOSS_REASONS" :key="r" :label="r" :value="r" />
        </el-select>
        <el-input v-model="correctForm.handoverTo" placeholder="交接人" style="margin-top: 10px" />
        <el-input v-model="correctForm.operator" placeholder="更正操作人（班组）" style="margin-top: 10px" />
      </div>
      <template #footer>
        <el-button @click="correctVisible = false">取消</el-button>
        <el-button type="primary" :loading="correcting" @click="submitCorrect">更正并重新提交</el-button>
      </template>
    </el-dialog>

    <div v-if="!detail" class="tf-empty">请选择或新建加工批次</div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { equipmentApi, intakeApi, processApi, witheringApi } from '../api'

const TEA_TYPES = ['GREEN', 'RED', 'OOLONG', 'WHITE', 'PUER']
const TEA_NAMES = { GREEN: '绿茶', RED: '红茶', OOLONG: '乌龙茶', WHITE: '白茶', PUER: '普洱茶' }
const STEP_NAMES = { SPREAD: '摊青', MAKING: '做青', FIX: '杀青', ROLL: '揉捻', FERMENT: '发酵', DRY: '干燥', PICK: '拣剔' }
const ALIAS = {
  RED: { FERMENT: '发酵', SPREAD: '萎凋' },
  PUER: { FERMENT: '渥堆', SPREAD: '摊青' },
  OOLONG: { SPREAD: '晒青' },
  WHITE: { SPREAD: '摊青（自然萎凋）' }
}
/* 各工序参数字段及默认值 */
const STEP_FIELDS = {
  SPREAD: ['paramMinutes', 'humidityPct', 'paramLeafQty'],
  MAKING: ['paramMinutes', 'paramRpm'],
  FIX: ['paramTempC', 'paramMinutes', 'paramLeafQty'],
  ROLL: ['paramRpm', 'paramPressure', 'paramMinutes'],
  FERMENT: ['paramTempC', 'humidityPct', 'paramMinutes'],
  DRY: ['paramTempC', 'paramMinutes', 'paramTemp2C', 'paramMinutes2'],
  PICK: ['paramMinutes', 'paramLeafQty']
}
const FIELD_META = {
  paramTempC: { key: 'paramTempC', label: '温度', unit: '℃', min: 0, max: 400, step: 5, precision: 1 },
  paramTemp2C: { key: 'paramTemp2C', label: '足火温度', unit: '℃', min: 0, max: 400, step: 5, precision: 1 },
  paramMinutes: { key: 'paramMinutes', label: '时长', unit: 'min', min: 0, max: 100000, step: 5, precision: 0 },
  paramMinutes2: { key: 'paramMinutes2', label: '足火时长', unit: 'min', min: 0, max: 100000, step: 5, precision: 0 },
  paramRpm: { key: 'paramRpm', label: '转速', unit: 'r/min', min: 0, max: 200, step: 5, precision: 0 },
  paramPressure: { key: 'paramPressure', label: '压力', unit: 'kg', min: 0, max: 100, step: 0.5, precision: 1 },
  paramLeafQty: { key: 'paramLeafQty', label: '投叶量', unit: 'kg', min: 0, max: 99999, step: 10, precision: 2 },
  humidityPct: { key: 'humidityPct', label: '相对湿度', unit: '%', min: 0, max: 100, step: 1, precision: 1 }
}
const MIN_FIX_TEMP = { GREEN: 220, OOLONG: 200, PUER: 180 }
const MIN_FERMENT_MIN = { RED: 180, PUER: 720, OOLONG: 120 }
const EQ_OF_STEP = { FIX: '杀青机', ROLL: '揉捻机', FERMENT: '发酵室', DRY: '烘干机', PICK: '提香机' }
/* 损耗原因（与后端 Dict.LOSS_REASONS 对应，支持自填） */
const LOSS_REASONS = ['正常失水', '茶末 / 碎茶损耗', '老叶 / 黄片拣出', '焦边爆点', '渥堆 / 发酵损耗', '撒漏（操作损耗）', '水分超标复烘', '杂质 / 非茶夹杂物', '设备残留']
/* 各茶类 × 各工序正常损耗范围（%）：以后端 /intake/vocab 的 lossRanges 为准，下面是加载失败时的兜底 */
const FALLBACK_LOSS_RANGES = {
  GREEN: { SPREAD: [25, 40], FIX: [3, 12], ROLL: [1, 6], FERMENT: [1, 12], DRY: [40, 60], PICK: [0, 4], MAKING: [1, 10] },
  RED: { SPREAD: [30, 45], ROLL: [2, 8], FERMENT: [2, 10], DRY: [42, 62], PICK: [0, 5], FIX: [2, 15], MAKING: [1, 10] },
  OOLONG: { SPREAD: [8, 20], MAKING: [3, 10], FIX: [3, 12], ROLL: [1, 7], FERMENT: [1, 12], DRY: [38, 58], PICK: [1, 6] },
  WHITE: { SPREAD: [35, 48], FIX: [2, 15], ROLL: [1, 8], FERMENT: [1, 12], DRY: [30, 50], PICK: [0, 4], MAKING: [1, 10] },
  PUER: { SPREAD: [5, 15], FIX: [2, 10], ROLL: [2, 8], FERMENT: [1, 8], DRY: [35, 55], PICK: [1, 5], MAKING: [1, 10] }
}
const lossRanges = ref(FALLBACK_LOSS_RANGES)

const teaTypeList = TEA_TYPES
const props = defineProps({
  teaType: { type: String, default: 'GREEN' },
  step: { type: String, default: '' }
})

const orders = ref([])
const troughs = ref([])
const equipments = ref([])
const detail = ref(null)
const steps = ref([])
const pendingList = ref([])
const batchNo = ref('')
const selIndex = ref(0)
const showCreate = ref(false)
const creating = ref(false)
const starting = ref(false)
const finishing = ref(false)
const reviewing = ref(false)
const correcting = ref(false)
const reviewVisible = ref(false)
const correctVisible = ref(false)
const reviewAccept = ref(true)
const reviewRow = ref(null)
const correctRow = ref(null)

const createForm = reactive({ teaType: props.teaType || 'GREEN', inputQtyKg: 180, sourceTroughNo: '', operator: '' })

const reviewForm = reactive({ reviewer: '', reviewNote: '' })
const correctForm = reactive({ actualOutputQtyKg: null, lossReason: '', handoverTo: '', operator: '' })

const form = reactive({
  equipmentCode: '',
  paramTempC: null,
  paramTemp2C: null,
  paramMinutes: null,
  paramMinutes2: null,
  paramRpm: null,
  paramPressure: null,
  paramLeafQty: null,
  humidityPct: null,
  paramProfile: '',
  operator: '',
  abnormalNote: '',
  actualOutputQtyKg: null,
  lossReason: '',
  handoverTo: ''
})

const teaName = (t) => TEA_NAMES[t] || t || '-'
const num = (v, d = 2) => (v === null || v === undefined ? '-' : Number(v).toFixed(d))
const fmtTime = (s) => (s ? String(s).replace('T', ' ').slice(0, 16) : '-')

function stepName(row) {
  const tea = row.teaType || (detail.value && detail.value.teaType)
  const alias = ALIAS[tea] || {}
  return alias[row.stepName] || STEP_NAMES[row.stepName] || row.stepName
}

const curIndex = computed(() => {
  const d = detail.value
  if (!d) return 0
  return typeof d.currentStepIndex === 'number' ? d.currentStepIndex : 0
})
const selStep = computed(() => (detail.value && detail.value.flow[selIndex.value]) || '')
const selStepName = computed(() => (detail.value && detail.value.flowNames[selIndex.value]) || '')

const fields = computed(() => {
  const keys = STEP_FIELDS[selStep.value] || []
  const out = keys.map((k) => ({ ...FIELD_META[k] }))
  const tea = detail.value ? detail.value.teaType : 'GREEN'
  out.forEach((f) => {
    if (f.key === 'paramTempC' && selStep.value === 'FIX' && MIN_FIX_TEMP[tea]) {
      f.tip = `${teaName(tea)}杀青不得低于 ${MIN_FIX_TEMP[tea]}℃`
    }
    if (f.key === 'paramMinutes' && selStep.value === 'FERMENT' && MIN_FERMENT_MIN[tea]) {
      f.tip = `不少于 ${MIN_FERMENT_MIN[tea]} 分钟`
    }
  })
  return out
})

const stepEquipments = computed(() => {
  const type = EQ_OF_STEP[selStep.value]
  // 摊青等不使用制茶设备的工序：不走设备开工看板（其设备填的是摊青槽号）
  if (!type) return []
  return equipments.value.filter((e) => e.equipType === type)
})

const equipSelectHint = computed(() => {
  const list = stepEquipments.value
  if (!list.length) return '该工序暂无可选设备'
  const avail = list.filter((e) => e.availableForNextBatch).length
  return avail ? `可选 ${avail} 台` : '本工序设备均不可开工（清洗/故障/占用）'
})

const lockedSelected = computed(() => {
  const code = form.equipmentCode
  const hit = equipments.value.find((e) => e.equipCode === code)
  return hit && !hit.availableForNextBatch ? hit : null
})

const TEA_FLOW = {
  GREEN: ['SPREAD', 'FIX', 'ROLL', 'DRY', 'PICK'],
  RED: ['SPREAD', 'ROLL', 'FERMENT', 'DRY', 'PICK'],
  OOLONG: ['SPREAD', 'MAKING', 'FIX', 'ROLL', 'DRY', 'PICK'],
  WHITE: ['SPREAD', 'DRY', 'PICK'],
  PUER: ['SPREAD', 'FIX', 'ROLL', 'FERMENT', 'DRY', 'PICK']
}

const flowPreview = computed(() =>
  (TEA_FLOW[createForm.teaType] || []).map((s) => ALIAS[createForm.teaType]?.[s] || STEP_NAMES[s]).join(' → ')
)
function runningOf(step) {
  return steps.value.find((s) => s.stepName === step && s.status === 'RUNNING') || null
}

/* ---------------- 在制量交接：时间线 / 挂起 / 损耗预览 ---------------- */
function qtyNodeOf(index) {
  const list = detail.value?.qtyTimeline || []
  return list.find((n) => n.stepIndex === index) || null
}

/** 节点是否挂起（待复核 / 退回） */
function nodeHold(index) {
  const n = qtyNodeOf(index)
  return n && (n.status === 'PENDING_REVIEW' || n.status === 'RETURNED')
}

/** 当前工序挂起的交接节点（结束后超限待复核或被退回） */
const currentHoldNode = computed(() => {
  const d = detail.value
  if (!d) return null
  const n = qtyNodeOf(curIndex.value)
  return n && (n.status === 'PENDING_REVIEW' || n.status === 'RETURNED') ? n : null
})

/** 元信息条上的可用在制量是否处于挂起冻结状态 */
const wipHeld = computed(() => !!currentHoldNode.value)

function nodeDot(i) {
  if (nodeHold(i)) return '!'
  return runningOf(detail.value.flow[i]) ? '●' : i + 1
}

function nodeStateText(i) {
  const hold = nodeHold(i)
  if (hold) return qtyNodeOf(i).status === 'PENDING_REVIEW' ? '待复核' : '退回更正'
  if (i < curIndex.value) return '已完成'
  if (i === curIndex.value) return runningOf(detail.value.flow[i]) ? '执行中' : '待开工'
  return '未开始'
}

function qtyTagType(status) {
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

function balanceTagType(status) {
  return status === 'BALANCED' ? 'success' : status === 'PARTIAL' ? 'warning' : 'danger'
}

function timelineRowClass({ row }) {
  if (row.status === 'PENDING_REVIEW') return 'pc-row-hold'
  if (row.status === 'RETURNED') return 'pc-row-return'
  return ''
}

/** 本道“上道转入”的投入（时间线节点已以后端口径给出） */
const currentStepInput = computed(() => {
  const n = qtyNodeOf(selIndex.value)
  if (n && n.inputQtyKg != null) return Number(n.inputQtyKg)
  return Number(detail.value?.availableWipKg || detail.value?.inputQtyKg || 0)
})

const wipBasisText = computed(() => {
  if (selIndex.value === 0) return '首道＝批次投料基准'
  const prev = qtyNodeOf(selIndex.value - 1)
  if (!prev || !prev.handoverNo) return '上道已确认可用在制量'
  return prev.status === 'LEGACY' ? '上道旧记录派生在制量' : '上道已确认交接产出'
})

/** 只有当前正在执行的工序才显示数量交接登记（在未开始面板上展示本道将收到的在制量也无妨，但仅 RUNNING 可提交） */
const showQtyFields = computed(() => canFinish.value || !!currentHoldNode.value)

const liveLoss = computed(() => {
  const out = form.actualOutputQtyKg
  const input = currentStepInput.value
  if (out == null || !input || input <= 0) return null
  const loss = Math.round((input - out) * 100) / 100
  const pct = Math.round((((input - out) / input) * 100) * 100) / 100
  const tea = detail.value ? detail.value.teaType : 'GREEN'
  const range = (lossRanges.value[tea] && lossRanges.value[tea][selStep.value]) || [0, 30]
  const over = pct < range[0] - 0.05 || pct > range[1] + 0.05
  return {
    lossText: loss.toFixed(2),
    pctText: pct.toFixed(2),
    limitText: `${range[0]}% ~ ${range[1]}`,
    over
  }
})

/** 按当前正常范围中值给一个建议产出，便于演示 */
function applySuggestedOutput() {
  const tea = detail.value ? detail.value.teaType : 'GREEN'
  const range = (lossRanges.value[tea] && lossRanges.value[tea][selStep.value]) || [0, 30]
  const mid = (range[0] + range[1]) / 2
  form.actualOutputQtyKg = Math.round(currentStepInput.value * (1 - mid / 100) * 100) / 100
}

const panelHint = computed(() => {
  const d = detail.value
  if (!d) return ''
  if (d.status === 'DONE') return '本批次已完成全部工序'
  if (selIndex.value > curIndex.value) return `工序不能跳步，当前应先做「${d.flowNames[curIndex.value]}」`
  if (selIndex.value < curIndex.value) return `「${selStepName.value}」已完成，当前工序是「${d.flowNames[curIndex.value]}」`
  if (currentHoldNode.value) {
    return currentHoldNode.value.status === 'PENDING_REVIEW'
      ? `「${selStepName.value}」损耗超范围待复核：复核人接受异常或退回更正前工序挂起，下道不能开工`
      : `「${selStepName.value}」交接已被退回：请按退回意见更正实际产出与损耗说明后重交，不能重新开工`
  }
  return runningOf(selStep.value) ? `「${selStepName.value}」执行中，记录完参数后点「结束本工序」` : `可以开始「${selStepName.value}」`
})

const canStart = computed(() => {
  const d = detail.value
  if (!d || d.status === 'DONE') return false
  if (selIndex.value !== curIndex.value) return false
  if (currentHoldNode.value) return false // 待复核 / 退回挂起：只能等结论或更正重交，不能重复开工
  return !runningOf(selStep.value)
})

const canFinish = computed(() => {
  const d = detail.value
  if (!d || d.status === 'DONE') return false
  if (selIndex.value !== curIndex.value) return false
  return !!runningOf(selStep.value)
})

function fillForm(step) {
  form.equipmentCode = ''
  form.paramTempC = null
  form.paramTemp2C = null
  form.paramMinutes = null
  form.paramMinutes2 = null
  form.paramRpm = null
  form.paramPressure = null
  form.paramLeafQty = null
  form.humidityPct = null
  form.paramProfile = ''
  form.operator = (detail.value && detail.value.operator) || ''
  form.abnormalNote = ''
  form.actualOutputQtyKg = null
  form.lossReason = ''
  form.handoverTo = ''

  const tea = detail.value ? detail.value.teaType : 'GREEN'
  // 投叶量默认带“上道转入”（可用在制量＝上道已确认产出；首道＝批次投料）
  const wip = Number(detail.value?.availableWipKg || detail.value?.inputQtyKg || 180)
  const preset = {
    FIX: { paramTempC: Math.max(MIN_FIX_TEMP[tea] || 200, 220), paramMinutes: 4, paramLeafQty: wip },
    ROLL: { paramRpm: 45, paramPressure: 8.5, paramMinutes: 35, paramLeafQty: wip },
    FERMENT: { paramTempC: tea === 'PUER' ? 45 : 28, humidityPct: tea === 'PUER' ? 72 : 92, paramMinutes: MIN_FERMENT_MIN[tea] || 180, paramLeafQty: wip },
    DRY: { paramTempC: 110, paramMinutes: 25, paramTemp2C: 90, paramMinutes2: 40, paramLeafQty: wip },
    SPREAD: { paramMinutes: 480, humidityPct: 68, paramLeafQty: Number(detail.value?.inputQtyKg || 180) },
    MAKING: { paramMinutes: 90, paramRpm: 18, paramLeafQty: wip },
    PICK: { paramMinutes: 60, paramLeafQty: wip }
  }[step]
  Object.assign(form, preset || {})

  const rec = steps.value.find((s) => s.stepName === step)
  const stepEquipType = EQ_OF_STEP[step]
  if (rec) {
    // 仅制茶设备工序带入设备号；摊青记录的是摊青槽号，且已锁定 / 故障设备不自动带入
    const recEquip = equipments.value.find((e) => e.equipCode === rec.equipmentCode)
    if (stepEquipType && rec.equipmentCode && (!recEquip || recEquip.availableForNextBatch)) {
      form.equipmentCode = rec.equipmentCode
    }
    if (rec.paramTempC != null) form.paramTempC = Number(rec.paramTempC)
    if (rec.paramTemp2C != null) form.paramTemp2C = Number(rec.paramTemp2C)
    if (rec.paramMinutes != null) form.paramMinutes = Number(rec.paramMinutes)
    if (rec.paramMinutes2 != null) form.paramMinutes2 = Number(rec.paramMinutes2)
    if (rec.paramRpm != null) form.paramRpm = Number(rec.paramRpm)
    if (rec.paramPressure != null) form.paramPressure = Number(rec.paramPressure)
    if (rec.paramLeafQty != null) form.paramLeafQty = Number(rec.paramLeafQty)
    if (rec.humidityPct != null) form.humidityPct = Number(rec.humidityPct)
    if (rec.paramProfile) form.paramProfile = rec.paramProfile
    if (rec.operator) form.operator = rec.operator
    if (rec.abnormalNote) form.abnormalNote = rec.abnormalNote
  }
}

function payload(step) {
  return {
    stepName: step,
    equipmentCode: form.equipmentCode || undefined,
    paramTempC: form.paramTempC == null ? undefined : form.paramTempC,
    paramTemp2C: form.paramTemp2C == null ? undefined : form.paramTemp2C,
    paramMinutes: form.paramMinutes == null ? undefined : form.paramMinutes,
    paramMinutes2: form.paramMinutes2 == null ? undefined : form.paramMinutes2,
    paramRpm: form.paramRpm == null ? undefined : form.paramRpm,
    paramPressure: form.paramPressure == null ? undefined : form.paramPressure,
    paramLeafQty: form.paramLeafQty == null ? undefined : form.paramLeafQty,
    humidityPct: form.humidityPct == null ? undefined : form.humidityPct,
    paramProfile: form.paramProfile || undefined,
    operator: form.operator || undefined,
    abnormalNote: form.abnormalNote || undefined,
    actualOutputQtyKg: form.actualOutputQtyKg == null ? undefined : form.actualOutputQtyKg,
    lossReason: form.lossReason || undefined,
    handoverTo: form.handoverTo || undefined
  }
}

async function loadAll() {
  try {
    const res = await processApi.list()
    orders.value = res && res.data ? res.data : []
    if (!orders.value.length) {
      detail.value = null
      return
    }
    if (!batchNo.value || !orders.value.some((o) => o.batchNo === batchNo.value)) {
      batchNo.value = orders.value[0].batchNo
    }
    await loadDetail()
  } catch (e) {
    ElMessage.error('加载批次失败：' + e.message)
  }
}

async function loadDetail() {
  if (!batchNo.value) return
  try {
    const res = await processApi.detail(batchNo.value)
    detail.value = res && res.data ? res.data : null
    const sres = await processApi.steps(batchNo.value)
    steps.value = sres && sres.data ? sres.data : []
    if (detail.value) {
      if (selIndex.value >= detail.value.flow.length) selIndex.value = 0
      const idx = props.step ? detail.value.flow.indexOf(String(props.step).toUpperCase()) : -1
      if (idx >= 0) selIndex.value = idx
      fillForm(detail.value.flow[selIndex.value])
    }
  } catch (e) {
    ElMessage.error(e.message || '加载批次详情失败')
  }
}

async function createOrder() {
  creating.value = true
  try {
    const res = await processApi.create({
      teaType: createForm.teaType,
      inputQtyKg: createForm.inputQtyKg,
      sourceTroughNo: createForm.sourceTroughNo || undefined,
      operator: createForm.operator || undefined
    })
    ElMessage.success(res && res.message ? res.message : '已开单')
    showCreate.value = false
    batchNo.value = res && res.data ? res.data.batchNo : ''
    await loadAll()
  } catch (e) {
    ElMessage.error(e.message || '开单失败')
  } finally {
    creating.value = false
  }
}

async function startStep() {
  if (!detail.value) return
  if (lockedSelected.value) {
    ElMessage.error(lockedSelected.value.unavailableReason + '，不能开工')
    return
  }
  starting.value = true
  try {
    const res = await processApi.start(detail.value.batchNo, payload(selStep.value))
    ElMessage.success(res && res.message ? res.message : '工序已开始')
    await loadDetail()
    await loadSide()
  } catch (e) {
    await loadSide()
    ElMessage.error(e.message || '开工失败')
  } finally {
    starting.value = false
  }
}

async function finishStep() {
  if (!detail.value) return
  if (runningOf(selStep.value) && form.actualOutputQtyKg == null) {
    ElMessage.error('请登记本道工序的实际产出重量（旧批次未过磅可留空按历史口径推进）')
    return
  }
  finishing.value = true
  try {
    const res = await processApi.finish(detail.value.batchNo, payload(selStep.value))
    const d = res && res.data ? res.data : null
    await loadDetail()
    await loadSide()
    /* 收尾已自动形成清洗交接单，提示下一班交接口径 */
    if (d && d.lastHandoverNo) {
      ElMessage({
        type: 'warning',
        duration: 6000,
        message: `本工序完成，已形成清洗交接单 ${d.lastHandoverNo}；设备清洗并经负责人确认前不能接下一批次`
      })
    }
    if (d && d.stepAdvanced === false) {
      /* 超损耗范围：工序挂起，不切换面板，保留待复核提示 */
      ElMessage({ type: 'error', duration: 6000, message: res.message || '损耗超范围，进入待复核' })
    } else if (!d?.lastHandoverNo) {
      ElMessage.success(res && res.message ? res.message : '本工序完成')
    }
    await loadPending()
    /* 结束后自动把面板切到新的当前工序 */
    if (d && d.stepAdvanced !== false && typeof d.currentStepIndex === 'number' && d.flow && d.currentStepIndex < d.flow.length) {
      selIndex.value = d.currentStepIndex
      fillForm(d.flow[d.currentStepIndex])
    }
  } catch (e) {
    ElMessage.error(e.message || '结束工序失败')
  } finally {
    finishing.value = false
  }
}

/* ---------------- 复核结论 / 更正重交 ---------------- */
async function loadPending() {
  try {
    const res = await processApi.qtyPending()
    pendingList.value = res && res.data ? res.data : []
  } catch (e) {
    /* 看板加载失败不阻断操作 */
  }
}

function openReview(row, accept) {
  reviewRow.value = row
  reviewAccept.value = accept
  reviewForm.reviewer = ''
  reviewForm.reviewNote = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (!reviewRow.value) return
  if (!reviewForm.reviewer) {
    ElMessage.error('请填写复核人')
    return
  }
  if (!reviewForm.reviewNote) {
    ElMessage.error(reviewAccept.value ? '请填写接受异常的理由' : '请填写退回更正的原因')
    return
  }
  reviewing.value = true
  try {
    const res = await processApi.qtyReview(reviewRow.value.handoverNo, {
      reviewer: reviewForm.reviewer,
      accept: reviewAccept.value,
      reviewNote: reviewForm.reviewNote
    })
    ElMessage.success(res.message || '复核结论已提交')
    reviewVisible.value = false
    await loadPending()
    if (detail.value && res.data && res.data.batchNo === detail.value.batchNo) {
      await loadDetail()
    }
  } catch (e) {
    ElMessage.error(e.message || '复核操作失败（可能已有人先给出结论，请刷新）')
    await loadPending()
  } finally {
    reviewing.value = false
  }
}

function fillCorrectForm(row) {
  correctRow.value = row
  correctForm.actualOutputQtyKg = row.outputQtyKg != null ? Number(row.outputQtyKg) : null
  correctForm.lossReason = row.lossReason && !row.lossReason.startsWith('旧记录') ? row.lossReason : ''
  correctForm.handoverTo = row.handoverTo || ''
  correctForm.operator = ''
  correctVisible.value = true
}

function openCorrect(node) {
  fillCorrectForm(node)
}

function openCorrectFromBoard(row) {
  fillCorrectForm(row)
}

async function submitCorrect() {
  if (!correctRow.value) return
  if (correctForm.actualOutputQtyKg == null || correctForm.actualOutputQtyKg <= 0) {
    ElMessage.error('请填写更正后的实际产出重量（大于 0）')
    return
  }
  if (!correctForm.lossReason) {
    ElMessage.error('请填写更正后的损耗原因与说明')
    return
  }
  if (!correctForm.handoverTo || !correctForm.operator) {
    ElMessage.error('请填写交接人与更正操作人')
    return
  }
  correcting.value = true
  try {
    const res = await processApi.qtyCorrect(correctRow.value.handoverNo, { ...correctForm })
    ElMessage.success(res.message || '更正已提交')
    correctVisible.value = false
    await loadPending()
    await loadDetail()
    await loadSide()
  } catch (e) {
    ElMessage.error(e.message || '更正重交失败')
  } finally {
    correcting.value = false
  }
}

async function loadSide() {
  try {
    const [b, t] = await Promise.all([equipmentApi.board(), witheringApi.ready()])
    equipments.value = b && b.data ? b.data : []
    troughs.value = t && t.data ? t.data : []
  } catch (err) {
    /* 设备/槽位列表失败不影响主流程 */
  }
}

watch(selIndex, (i) => {
  if (detail.value && detail.value.flow[i]) fillForm(detail.value.flow[i])
})

watch(
  () => props.step,
  (v) => {
    if (!detail.value || !v) return
    const idx = detail.value.flow.indexOf(String(v).toUpperCase())
    if (idx >= 0 && idx !== selIndex.value) selIndex.value = idx
  }
)

onMounted(async () => {
  await loadSide()
  await loadAll()
  await loadPending()
  loadVocab()
})

/** 从后端词典取各茶类×工序的损耗范围（{minPct,maxPct} → [min,max]），失败用兜底表 */
async function loadVocab() {
  try {
    const res = await intakeApi.vocab()
    const raw = res && res.data ? res.data.lossRanges : null
    if (!raw) return
    const out = {}
    for (const tea of Object.keys(raw)) {
      out[tea] = {}
      for (const step of Object.keys(raw[tea])) {
        const b = raw[tea][step]
        out[tea][step] = [Number(b.minPct), Number(b.maxPct)]
      }
    }
    lossRanges.value = out
  } catch (e) {
    /* 词典不可用时沿用兜底范围，结束时仍以后端判定为准 */
  }
}
</script>

<style scoped>
.pc-create {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.pc-create-seq {
  font-size: 12px;
  color: #5d6353;
  padding-bottom: 18px;
}

.pc-meta {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #5d6353;
  border-top: 1px dashed #c4b99f;
  padding-top: 8px;
}

.pc-meta b {
  color: #33691e;
}

/* ---- 工序链 ---- */
.pc-chain {
  display: flex;
  align-items: stretch;
  gap: 4px;
  overflow-x: auto;
  padding: 8px 2px 12px;
}

.pc-node {
  flex: 0 0 92px;
  text-align: center;
  border: 1px solid #ddd5c2;
  border-radius: 6px;
  padding: 10px 4px 8px;
  background: #fff;
  cursor: pointer;
  transition: all 0.25s ease;
}

.pc-node:hover {
  border-color: #5b8b3f;
}

.pc-node.is-done {
  background: #e7efdd;
  border-color: #a6c088;
}

.pc-node.is-current {
  background: #33691e;
  border-color: #33691e;
  transform: scale(1.14);
  box-shadow: 0 4px 14px rgba(51, 105, 30, 0.28);
  z-index: 2;
}

.pc-node.is-future {
  color: #9a9788;
  background: #f4efe4;
}

.pc-node.is-sel {
  outline: 2px dashed #b45309;
  outline-offset: 2px;
}

.pc-node-dot {
  width: 26px;
  height: 26px;
  line-height: 24px;
  margin: 0 auto 6px;
  border-radius: 50%;
  border: 1px solid currentColor;
  font-size: 12px;
  font-weight: 700;
}

.pc-node.is-current .pc-node-dot {
  color: #fff;
  border-color: #fff;
}

.pc-node.is-done .pc-node-dot {
  color: #33691e;
}

.pc-node-name {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
}

.pc-node.is-current .pc-node-name {
  color: #fff;
}

.pc-node-state {
  margin-top: 3px;
  font-size: 10px;
  opacity: 0.75;
}

.pc-arrow {
  align-self: center;
  color: #c4b99f;
  font-size: 16px;
}

/* ---- 参数面板 ---- */
.pc-panel-body {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  align-items: flex-start;
}

.pc-params {
  flex: 1 1 460px;
  min-width: 340px;
}

.pc-tip {
  margin-left: 10px;
  font-size: 11px;
  color: #b45309;
}

.pc-actions {
  flex: 0 0 240px;
  border-left: 1px dashed #c4b99f;
  padding-left: 18px;
}

.pc-actions .el-button {
  width: 100%;
  margin: 0 0 10px 0;
}

.pc-hint {
  font-size: 12px;
  color: #5d6353;
  line-height: 1.7;
}

.pc-hint-bad {
  color: #b45309;
}

/* ---- 数量交接 / 对账 ---- */
.pc-balance {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 12px;
  color: #5d6353;
  border-top: 1px dashed #c4b99f;
  padding-top: 8px;
  margin-top: 8px;
}

.pc-balance b {
  color: #33691e;
}

.pc-loss {
  color: #b3261e;
  font-weight: 700;
}

.pc-wip-hold {
  color: #b45309 !important;
}

.pc-muted {
  color: #9a9788;
}

.pc-node.is-hold {
  background: #fbeaea;
  border-color: #c76d68;
  box-shadow: 0 0 0 2px rgba(179, 38, 30, 0.15);
}

.pc-node.is-hold .pc-node-dot {
  color: #b3261e;
}

.pc-node.is-hold .pc-node-name {
  color: #b3261e;
}

:deep(.pc-row-hold) {
  background: #fdecea !important;
}

:deep(.pc-row-return) {
  background: #fdf6ec !important;
}

.pc-hold-text {
  color: #b3261e;
  font-size: 12px;
}

.pc-hold-banner {
  margin-top: 10px;
  padding: 8px 12px;
  background: #fdecea;
  border: 1px solid #f3b9b4;
  border-radius: 6px;
  color: #8a1c16;
  font-size: 12px;
}

.pc-wip {
  font-weight: 700;
  color: #33691e;
}

.pc-wip small {
  font-weight: 400;
  color: #7a7f6e;
}

.pc-loss-preview {
  color: #33691e;
  font-weight: 700;
}

.pc-loss-preview.pc-loss {
  color: #b3261e;
}

.pc-dialog-body {
  font-size: 13px;
}
</style>
