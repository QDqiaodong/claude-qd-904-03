package com.tea.factory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.StepQtyHandover;
import com.tea.factory.domain.StepQtyHandoverLog;
import com.tea.factory.domain.TeaProcessOrder;
import com.tea.factory.dto.LossReviewRequest;
import com.tea.factory.dto.PendingReviewDTO;
import com.tea.factory.dto.QtyBalanceDTO;
import com.tea.factory.dto.QtyCorrectRequest;
import com.tea.factory.dto.StepQtyNodeDTO;
import com.tea.factory.generator.NoSequenceStore;
import com.tea.factory.repository.StepQtyHandoverLogRepository;
import com.tea.factory.repository.StepQtyHandoverRepository;
import com.tea.factory.repository.TeaProcessOrderRepository;

/**
 * 工序在制量交接与损耗复核。
 *
 * 设计取舍（在“实际产出直接作为后续投入”与“保留原投料基准、另建损耗账”之间）：
 * 取后者为骨架、以前者为执行口径的混合方案——
 *   1. 下道工序的可用在制量 = 上道【已确认】交接的实际产出（链式实称，不再用批次投料假设）；
 *   2. 批次原投料基准 input_qty_kg 全程不被改写，损耗不做会漂移的手工余额，
 *      而是按“本步投入 − 本步产出”逐道派生，
 *      恒有 总投入 = 最终产量 + 累计损耗 + 当前在制量（+ 旧记录未登记差）；
 *   3. 超茶类×工序损耗范围 → PENDING_REVIEW，工序停在原地、不得开工下一道；
 *      复核人可接受异常（写明理由，CONFIRMED 后工序前进）或退回（RETURNED，班组更正同一张单后重交）。
 *
 * 并发：所有改变在制量的动作都要求调用方先持有 tea_process_order 行级写锁（本服务不再重复加锁），
 * 同批串行；uk_qty_batch_step 唯一键兜底。后到者不产生任何推进，只留 REJECTED 痕。
 */
@Service
public class StepQtyHandoverService {

    /** 损耗率核对允许误差（百分比点） */
    private static final BigDecimal PCT_TOLERANCE = BigDecimal.valueOf(0.05);
    /** 重量核对允许误差 kg（过磅误差 + 四舍五入） */
    private static final BigDecimal KG_TOLERANCE = BigDecimal.valueOf(0.05);

    private final StepQtyHandoverRepository handoverRepository;
    private final StepQtyHandoverLogRepository logRepository;
    private final TeaProcessOrderRepository orderRepository;
    private final com.tea.factory.repository.ProcessStepRecordRepository stepRecordRepository;
    private final QtyHandoverAuditRecorder auditRecorder;

    public StepQtyHandoverService(StepQtyHandoverRepository handoverRepository,
            StepQtyHandoverLogRepository logRepository,
            TeaProcessOrderRepository orderRepository,
            com.tea.factory.repository.ProcessStepRecordRepository stepRecordRepository,
            QtyHandoverAuditRecorder auditRecorder) {
        this.handoverRepository = handoverRepository;
        this.logRepository = logRepository;
        this.orderRepository = orderRepository;
        this.stepRecordRepository = stepRecordRepository;
        this.auditRecorder = auditRecorder;
    }

    // ============================== 查询：时间线与对账 ==============================

    /** 某批次的完整数量时间线（每道工序一个节点，含未开始节点） */
    @Transactional(readOnly = true)
    public List<StepQtyNodeDTO> timeline(String batchNo) {
        TeaProcessOrder order = orderRepository.findById(batchNo)
                .orElseThrow(() -> new BizException("批次号 " + batchNo + " 不存在"));
        return timeline(order);
    }

    /** 组装时间线：调用方可传瞬态（已加锁）或托管实体 */
    public List<StepQtyNodeDTO> timeline(TeaProcessOrder order) {
        List<String> flow = Dict.flow(order.getTeaType());
        List<StepQtyHandover> handovers = handoverRepository.findByBatchNoOrderByStepIndexAsc(order.getBatchNo());
        List<com.tea.factory.domain.ProcessStepRecord> records =
                stepRecordRepository.findByBatchNoOrderByStepIndexAsc(order.getBatchNo());
        List<StepQtyNodeDTO> nodes = new ArrayList<>();
        for (int i = 0; i < flow.size(); i++) {
            String step = flow.get(i);
            final int idx = i;
            StepQtyHandover h = handovers.stream()
                    .filter(x -> x.getStepIndex() != null && x.getStepIndex() == idx)
                    .findFirst().orElse(null);
            com.tea.factory.domain.ProcessStepRecord record = records.stream()
                    .filter(r -> r.getStepIndex() != null && r.getStepIndex() == idx)
                    .findFirst().orElse(null);
            nodes.add(toNode(order, idx, step, h, record, records));
        }
        return nodes;
    }

    /** 批次数量对账汇总（与时间线同源） */
    @Transactional(readOnly = true)
    public QtyBalanceDTO balance(String batchNo) {
        TeaProcessOrder order = orderRepository.findById(batchNo)
                .orElseThrow(() -> new BizException("批次号 " + batchNo + " 不存在"));
        return balance(order, timeline(order));
    }

    public QtyBalanceDTO balance(TeaProcessOrder order, List<StepQtyNodeDTO> nodes) {
        QtyBalanceDTO b = new QtyBalanceDTO();
        BigDecimal input = nz(order.getInputQtyKg());
        b.setTotalInputKg(input.setScale(2, RoundingMode.HALF_UP));
        boolean done = "DONE".equals(order.getStatus()) && nz(order.getOutputQtyKg()).signum() > 0;
        b.setFinalOutputKg(done ? order.getOutputQtyKg().setScale(2, RoundingMode.HALF_UP) : null);

        BigDecimal loss = BigDecimal.ZERO;
        boolean tracked = false;
        boolean legacy = false;
        for (StepQtyNodeDTO n : nodes) {
            if (Dict.QH_LEGACY.equals(n.getStatus())) {
                legacy = true;
            }
            if (Dict.QH_CONFIRMED.equals(n.getStatus())
                    || Dict.QH_PENDING_REVIEW.equals(n.getStatus())
                    || Dict.QH_RETURNED.equals(n.getStatus())) {
                tracked = true;
            }
            // 损耗只认派生账：CONFIRMED / 挂起交接单，以及旧记录派生（含估列，节点上已标注）
            if (n.getLossQtyKg() != null && !Dict.QH_LEGACY.equals(n.getStatus())) {
                loss = loss.add(n.getLossQtyKg());
            }
            if (Dict.QH_LEGACY.equals(n.getStatus()) && n.getLossQtyKg() != null) {
                loss = loss.add(n.getLossQtyKg());
            }
        }
        b.setAccumulatedLossKg(loss.setScale(2, RoundingMode.HALF_UP));

        // 旧批次中间工序没称产出：估列差额已在节点损耗里；若仍有无法解释的差额（数据本身缺投叶量），
        // 用剩余法兜底单列为“未登记差”，绝不按 0 或按损耗吞掉
        BigDecimal wip = done ? BigDecimal.ZERO : nz(order.getAvailableWipKg());
        b.setWorkInProcessKg(wip.setScale(2, RoundingMode.HALF_UP));

        BigDecimal explained = nz(b.getFinalOutputKg()).add(loss).add(wip);
        BigDecimal unrecorded = input.subtract(explained).setScale(2, RoundingMode.HALF_UP);
        if (unrecorded.signum() < 0) {
            unrecorded = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        b.setUnrecordedGapKg(unrecorded);

        BigDecimal gap = input.subtract(nz(b.getFinalOutputKg())).subtract(loss)
                .subtract(wip).subtract(unrecorded).setScale(2, RoundingMode.HALF_UP);
        if (gap.abs().compareTo(KG_TOLERANCE) <= 0) {
            gap = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        b.setReconcileGapKg(gap);

        String mode;
        if (legacy && tracked) {
            mode = "MIXED";
        } else if (legacy) {
            mode = "LEGACY";
        } else {
            mode = "TRACKED";
        }
        b.setQtyMode(mode);
        b.setQtyModeName(modeName(mode));

        if (gap.abs().compareTo(KG_TOLERANCE) > 0) {
            b.setReconcileStatus("UNBALANCED");
            b.setReconcileStatusName("对不平");
        } else if (unrecorded.signum() > 0) {
            b.setReconcileStatus("PARTIAL");
            b.setReconcileStatusName("部分重量未登记（差异已单列）");
        } else {
            b.setReconcileStatus("BALANCED");
            b.setReconcileStatusName("已对平");
        }

        if (b.getFinalOutputKg() != null && input.signum() > 0) {
            b.setFinalYieldRatePct(b.getFinalOutputKg().multiply(BigDecimal.valueOf(100))
                    .divide(input, 2, RoundingMode.HALF_UP));
        } else {
            b.setFinalYieldRatePct(null);
        }
        return b;
    }

    /** 待复核看板：超限待复核 + 退回待更正 */
    @Transactional(readOnly = true)
    public List<PendingReviewDTO> pendingList() {
        List<StepQtyHandover> list = handoverRepository
                .findByStatusInOrderBySubmittedAtDesc(List.of(Dict.QH_PENDING_REVIEW, Dict.QH_RETURNED));
        List<PendingReviewDTO> result = new ArrayList<>();
        for (StepQtyHandover h : list) {
            PendingReviewDTO dto = new PendingReviewDTO();
            dto.setHandoverNo(h.getHandoverNo());
            dto.setBatchNo(h.getBatchNo());
            dto.setTeaType(h.getTeaType());
            dto.setTeaTypeName(Dict.teaTypeName(h.getTeaType()));
            dto.setStepName(h.getStepName());
            dto.setStepNameCn(Dict.stepName(h.getTeaType(), h.getStepName()));
            dto.setStepIndex(h.getStepIndex());
            dto.setInputQtyKg(h.getInputQtyKg());
            dto.setOutputQtyKg(h.getOutputQtyKg());
            dto.setLossQtyKg(h.getLossQtyKg());
            dto.setLossPct(h.getLossPct());
            dto.setLimitMinPct(h.getLimitMinPct());
            dto.setLimitMaxPct(h.getLimitMaxPct());
            dto.setOverLimit(h.getOverLimit());
            dto.setLossReason(h.getLossReason());
            dto.setHandoverTo(h.getHandoverTo());
            dto.setSubmittedBy(h.getSubmittedBy());
            dto.setSubmittedAt(h.getSubmittedAt());
            dto.setStatus(h.getStatus());
            dto.setStatusName(Dict.qtyHandoverStatusName(h.getStatus()));
            dto.setReturnedNote(h.getReturnedNote());
            dto.setCorrectedCount(h.getCorrectedCount());
            result.add(dto);
        }
        return result;
    }

    // ============================== 班组：结束工序提交交接 ==============================

    /**
     * 结束工序时提交在制量交接（调用方须已持有订单行锁、且已通过工艺参数校验）。
     * 未提供实际产出（旧批次沿用旧操作）→ 返回 null，不建立新交接、不拦截推进（兼容口径）。
     */
    @Transactional
    public StepQtyHandover submitOnFinish(TeaProcessOrder order, String step, int stepIndex,
            BigDecimal output, String lossReason, String handoverTo, String operator) {
        if (output == null) {
            return null; // 旧批次 / 旧操作：不补称、不伪造损耗
        }
        BigDecimal input = inputForStep(order, stepIndex);
        validateWeight(input, output);
        if (!Dict.isValidLossReason(lossReason)) {
            throw new BizException("请登记本道工序的损耗原因（失水、碎茶、拣出等）");
        }
        if (handoverTo == null || handoverTo.isBlank()) {
            throw new BizException("请登记交接人（下道工序接班人）");
        }
        handoverRepository.findByBatchNoAndStepIndex(order.getBatchNo(), stepIndex).ifPresent(existing -> {
            // 唯一键兜底之外的友好拦截：并发双提交时后到者在这里被拒
            throw new BizException("「" + Dict.stepName(order.getTeaType(), step) + "」已存在交接单 "
                    + existing.getHandoverNo() + "（" + Dict.qtyHandoverStatusName(existing.getStatus())
                    + "），不能重复登记，请刷新查看");
        });

        LocalDateTime now = LocalDateTime.now();
        if ("LEGACY".equals(order.getQtyMode())) {
            // 旧批次从本道起逐道实称：标记为混合口径（旧节点仍可解释，新节点逐道交接）
            order.setQtyMode("MIXED");
        }
        StepQtyHandover h = new StepQtyHandover();
        h.setHandoverNo(NoSequenceStore.qtyHandoverNo());
        h.setBatchNo(order.getBatchNo());
        h.setTeaType(order.getTeaType());
        h.setStepName(step);
        h.setStepIndex(stepIndex);
        h.setInputQtyKg(input);
        h.setOutputQtyKg(output);
        applyLossAndLimit(h);
        h.setLossReason(lossReason.trim());
        h.setHandoverTo(handoverTo.trim());
        h.setSubmittedBy(operator);
        h.setSubmittedAt(now);
        h.setCorrectedCount(0);
        h.setCreatedAt(now);
        h.setUpdatedAt(now);

        boolean within = isWithinLimit(h);
        h.setOverLimit(!within);
        if (within) {
            h.setStatus(Dict.QH_CONFIRMED);
            handoverRepository.save(h);
            applyConfirmedToOrder(order, h);
            writeLog(h, "AUTO_CONFIRM", null, Dict.QH_CONFIRMED, operator, "EFFECTIVE",
                    "结束工序提交：实际产出 " + output + "kg，损耗率 " + pct(h.getLossPct())
                            + "% 在正常范围 " + rangeText(h) + " 内，自动确认并作为下道可用在制量");
        } else {
            h.setStatus(Dict.QH_PENDING_REVIEW);
            handoverRepository.save(h);
            writeLog(h, "SUBMIT", null, Dict.QH_PENDING_REVIEW, operator, "EFFECTIVE",
                    "结束工序提交：实际产出 " + output + "kg，损耗率 " + pct(h.getLossPct())
                            + "% 超出正常范围 " + rangeText(h) + "，工序挂起待复核，复核结论出来前下道不得开工");
        }
        return h;
    }

    /** 复核接受后把交接产出落到工单：可用在制量前进；末道则定最终产量、在制量归零、工单完工 */
    public void applyConfirmedToOrder(TeaProcessOrder order, StepQtyHandover h) {
        List<String> flow = Dict.flow(order.getTeaType());
        if (h.getStepIndex() >= flow.size() - 1) {
            order.setOutputQtyKg(h.getOutputQtyKg());
            order.setAvailableWipKg(BigDecimal.ZERO); // 末道交接确认：在制量全部转为成品
        } else {
            order.setAvailableWipKg(h.getOutputQtyKg());
        }
    }

    /**
     * 交接确认后推进工序游标：下道成为当前工序；末道确认则工单完工。
     * 范围内自动确认（结束工序）、复核接受、退回后更正确认三条路径共用，
     * 保证“确认产出 = 在制量前进 = 工序前进”永远一起发生或一起不发生。
     */
    public void advanceAfterConfirmed(TeaProcessOrder order, StepQtyHandover h) {
        applyConfirmedToOrder(order, h);
        List<String> flow = Dict.flow(order.getTeaType());
        int next = h.getStepIndex() + 1;
        if (next >= flow.size()) {
            order.setStatus("DONE");
            order.setCurrentStep("DONE");
            order.setCurrentStepIndex(next);
        } else {
            order.setCurrentStep(flow.get(next));
            order.setCurrentStepIndex(next);
        }
    }

    // ============================== 复核人：接受异常 / 退回更正 ==============================

    /** 复核结论：接受异常（推进）或退回更正（不推进）。先锁交接单行，再锁订单行。 */
    @Transactional
    public StepQtyHandover review(String handoverNo, LossReviewRequest req) {
        // 第一把锁：交接单行。并发复核结论在此串行——只有第一个事务能读到待处理状态
        StepQtyHandover h = handoverRepository.lockByHandoverNo(handoverNo)
                .orElseThrow(() -> new BizException("交接单 " + handoverNo + " 不存在"));
        if (!Dict.QH_PENDING_REVIEW.equals(h.getStatus())) {
            return reject(h, "REVIEW_" + (Boolean.TRUE.equals(req.getAccept()) ? "ACCEPT" : "RETURN"),
                    req.getReviewer(), "只有待复核的交接单能给出复核结论，当前为「"
                            + Dict.qtyHandoverStatusName(h.getStatus()) + "」，已有他人的结论先生效");
        }
        // 第二把锁：订单行，与几乎同时发生的开工 / 结束工序串行（锁序固定：先交接单后订单，避免死锁）
        TeaProcessOrder order = orderRepository.lockByBatchNo(h.getBatchNo())
                .orElseThrow(() -> new BizException("批次不存在"));
        boolean accept = Boolean.TRUE.equals(req.getAccept());
        LocalDateTime now = LocalDateTime.now();
        String from = h.getStatus();
        if (accept) {
            h.setStatus(Dict.QH_CONFIRMED);
            h.setReviewedBy(req.getReviewer().trim());
            h.setReviewedAt(now);
            h.setReviewNote(req.getReviewNote().trim());
            h.setUpdatedAt(now);
            handoverRepository.save(h);
            // 复核接受 = 交接产出生效 + 工序游标前进，与结束工序自动确认走同一个推进方法
            advanceAfterConfirmed(order, h);
            orderRepository.save(order);
            writeLog(h, "REVIEW_ACCEPT", from, Dict.QH_CONFIRMED, req.getReviewer(), "EFFECTIVE",
                    "复核接受异常损耗：" + req.getReviewNote() + "；交接产出 " + h.getOutputQtyKg()
                            + "kg 生效，工序推进到「" + currentStepName(order) + "」，下道工序可以开工");
        } else {
            h.setStatus(Dict.QH_RETURNED);
            h.setReturnedNote(req.getReviewNote().trim());
            h.setUpdatedAt(now);
            handoverRepository.save(h);
            // 不调用 applyConfirmedToOrder：可用在制量保持上道旧值，工序不前进
            writeLog(h, "REVIEW_RETURN", from, Dict.QH_RETURNED, req.getReviewer(), "EFFECTIVE",
                    "复核退回更正：" + req.getReviewNote() + "；请班组更正实际产出重量与损耗说明后重交，期间下道不得开工");
        }
        return h;
    }

    /** 班组更正重交（仅 RETURNED 可更正）；仍超范围则回到待复核。先锁交接单行，再锁订单行。 */
    @Transactional
    public StepQtyHandover correct(String handoverNo, QtyCorrectRequest req) {
        StepQtyHandover h = handoverRepository.lockByHandoverNo(handoverNo)
                .orElseThrow(() -> new BizException("交接单 " + handoverNo + " 不存在"));
        if (!Dict.QH_RETURNED.equals(h.getStatus())) {
            return reject(h, "CORRECT", req.getOperator(), "只有被退回的交接单能更正重交，当前为「"
                    + Dict.qtyHandoverStatusName(h.getStatus()) + "」");
        }
        TeaProcessOrder order = orderRepository.lockByBatchNo(h.getBatchNo())
                .orElseThrow(() -> new BizException("批次不存在"));
        validateWeight(h.getInputQtyKg(), req.getActualOutputQtyKg());

        String from = h.getStatus();
        LocalDateTime now = LocalDateTime.now();
        h.setOutputQtyKg(req.getActualOutputQtyKg());
        applyLossAndLimit(h);
        h.setLossReason(req.getLossReason().trim());
        h.setHandoverTo(req.getHandoverTo().trim());
        h.setSubmittedBy(req.getOperator().trim());
        h.setSubmittedAt(now);
        h.setCorrectedCount((h.getCorrectedCount() == null ? 0 : h.getCorrectedCount()) + 1);
        h.setReturnedNote(null);
        h.setUpdatedAt(now);

        boolean within = isWithinLimit(h);
        h.setOverLimit(!within);
        if (within) {
            h.setStatus(Dict.QH_CONFIRMED);
            h.setReviewedBy(null);
            h.setReviewedAt(null);
            h.setReviewNote(null);
            handoverRepository.save(h);
            advanceAfterConfirmed(order, h);
            orderRepository.save(order);
            writeLog(h, "CORRECT", from, Dict.QH_CONFIRMED, req.getOperator(), "EFFECTIVE",
                    "第 " + h.getCorrectedCount() + " 次更正重交：实际产出改为 " + h.getOutputQtyKg()
                            + "kg，损耗率 " + pct(h.getLossPct()) + "% 在范围 " + rangeText(h)
                            + " 内，自动确认，工序推进到「" + currentStepName(order) + "」");
        } else {
            h.setStatus(Dict.QH_PENDING_REVIEW);
            handoverRepository.save(h);
            writeLog(h, "CORRECT", from, Dict.QH_PENDING_REVIEW, req.getOperator(), "EFFECTIVE",
                    "第 " + h.getCorrectedCount() + " 次更正重交：实际产出改为 " + h.getOutputQtyKg()
                            + "kg，损耗率 " + pct(h.getLossPct()) + "% 仍超范围 " + rangeText(h)
                            + "，重新进入待复核");
        }
        return h;
    }

    // ============================== 开工闸门 ==============================

    /**
     * 下道开工前的在制量闸门（调用方须持有订单行锁）。
     * 前置交接未确认（待复核 / 退回）一律拒绝；返回本道可用在制量（首道未交接时回退批次投料）。
     */
    public BigDecimal assertReadyAndInput(TeaProcessOrder order, int stepIndex) {
        if (stepIndex <= 0) {
            return order.getAvailableWipKg() != null ? order.getAvailableWipKg() : order.getInputQtyKg();
        }
        StepQtyHandover prev = handoverRepository
                .findByBatchNoAndStepIndex(order.getBatchNo(), stepIndex - 1)
                .orElse(null);
        if (prev == null) {
            // 逐道实称批次：上道没有交接单说明班组还没登记实际产出，不允许“先开工后补交接”
            if ("TRACKED".equals(order.getQtyMode())) {
                throw new BizException("上道工序还没有登记实际产出重量与交接人，请先结束上道工序并完成交接确认");
            }
            // 旧批次：以工单当前可用在制量（旧记录回填）为准，不锁死
            return order.getAvailableWipKg() != null ? order.getAvailableWipKg() : order.getInputQtyKg();
        }
        switch (prev.getStatus()) {
            case Dict.QH_CONFIRMED, Dict.QH_LEGACY -> {
                if (prev.getOutputQtyKg() != null) {
                    return prev.getOutputQtyKg();
                }
                // 旧批次未过磅的中间节点：用工单锁定的可用在制量
                return order.getAvailableWipKg() != null ? order.getAvailableWipKg() : order.getInputQtyKg();
            }
            case Dict.QH_PENDING_REVIEW -> throw new BizException("上道「"
                    + Dict.stepName(order.getTeaType(), prev.getStepName()) + "」实际产出损耗率 "
                    + pct(prev.getLossPct()) + "% 超出正常范围，正在等待复核人结论，结论确认前不能开始本工序");
            case Dict.QH_RETURNED -> throw new BizException("上道「"
                    + Dict.stepName(order.getTeaType(), prev.getStepName()) + "」交接已被复核退回，"
                    + "须由班组按退回意见更正实际产出重量与损耗说明并重新提交后，才能开始本工序");
            default -> {
                return order.getAvailableWipKg() != null ? order.getAvailableWipKg() : order.getInputQtyKg();
            }
        }
    }

    /**
     * 挂起闸门：当前工序的交接单处于待复核 / 退回时，不允许对本工序重复开工
     * （避免产生第二条工序记录；退回只能走“更正重交”，待复核只能等结论）。
     */
    public void assertNotHeld(TeaProcessOrder order, int stepIndex) {
        StepQtyHandover h = handoverRepository
                .findByBatchNoAndStepIndex(order.getBatchNo(), stepIndex).orElse(null);
        if (h == null) {
            return;
        }
        if (Dict.QH_PENDING_REVIEW.equals(h.getStatus())) {
            throw new BizException("本工序交接单 " + h.getHandoverNo() + " 损耗超范围，正在等待复核人结论；"
                    + "请等待接受 / 退回结论，不能重复开工");
        }
        if (Dict.QH_RETURNED.equals(h.getStatus())) {
            throw new BizException("本工序交接单 " + h.getHandoverNo() + " 已被复核退回；"
                    + "请按退回意见“更正实际产出重量与损耗说明”后重交，不能重新开工本工序");
        }
    }

    // ============================== 内部工具 ==============================

    private StepQtyNodeDTO toNode(TeaProcessOrder order, int index, String step,
            StepQtyHandover h, com.tea.factory.domain.ProcessStepRecord record,
            List<com.tea.factory.domain.ProcessStepRecord> allRecords) {
        StepQtyNodeDTO n = new StepQtyNodeDTO();
        n.setStepIndex(index);
        n.setStepName(step);
        n.setStepNameCn(Dict.stepName(order.getTeaType(), step));
        Dict.LossRange range = Dict.lossRange(order.getTeaType(), step);
        n.setLimitMinPct(range.minPct());
        n.setLimitMaxPct(range.maxPct());

        BigDecimal input = null;
        if (h != null) {
            // 交接单（CONFIRMED / 挂起 / LEGACY 派生）以单上锁定的本步投入为准
            input = h.getInputQtyKg();
        } else if (record != null && record.getParamLeafQty() != null) {
            // 旧口径已执行但无交接单：本步投入取该道记录登记的投叶量
            input = record.getParamLeafQty();
        } else if (index > 0) {
            StepQtyHandover prev = handoverRepository
                    .findByBatchNoAndStepIndex(order.getBatchNo(), index - 1).orElse(null);
            if (prev != null && prev.getOutputQtyKg() != null
                    && (Dict.QH_CONFIRMED.equals(prev.getStatus()) || Dict.QH_LEGACY.equals(prev.getStatus()))) {
                input = prev.getOutputQtyKg();
            } else {
                // 上道无交接单，或旧批在制尾单当时未过磅：显示工单锁定的可用在制量
                input = order.getAvailableWipKg() != null ? order.getAvailableWipKg() : order.getInputQtyKg();
            }
        } else {
            input = order.getInputQtyKg();
        }
        n.setInputQtyKg(input);

        if (h == null) {
            String status;
            if (record != null && "RUNNING".equals(record.getStatus())) {
                status = "RUNNING";
            } else if (record != null && "DONE".equals(record.getStatus())) {
                status = "NOT_HANDED"; // 旧口径已推进但无数量交接
            } else {
                status = "NOT_STARTED";
            }
            n.setStatus(status);
            n.setStatusName(nodeStatusName(status));
            return n;
        }

        n.setHandoverNo(h.getHandoverNo());
        n.setOutputQtyKg(h.getOutputQtyKg());
        n.setLossQtyKg(h.getLossQtyKg());
        n.setLossPct(h.getLossPct());
        n.setLossReason(h.getLossReason());
        n.setHandoverTo(h.getHandoverTo());
        n.setSubmittedBy(h.getSubmittedBy());
        n.setSubmittedAt(h.getSubmittedAt());
        n.setLimitMinPct(h.getLimitMinPct() == null ? range.minPct() : h.getLimitMinPct());
        n.setLimitMaxPct(h.getLimitMaxPct() == null ? range.maxPct() : h.getLimitMaxPct());
        n.setOverLimit(h.getOverLimit());
        n.setStatus(h.getStatus());
        n.setStatusName(Dict.qtyHandoverStatusName(h.getStatus()));
        n.setReviewedBy(h.getReviewedBy());
        n.setReviewedAt(h.getReviewedAt());
        n.setReviewNote(h.getReviewNote());
        n.setReturnedNote(h.getReturnedNote());
        n.setCorrectedCount(h.getCorrectedCount());
        n.setLegacyNote(h.getLegacyNote());
        return n;
    }

    private String nodeStatusName(String status) {
        return switch (status) {
            case "NOT_STARTED" -> "未开始";
            case "RUNNING" -> "执行中";
            case "NOT_HANDED" -> "已结束（未登记数量）";
            default -> Dict.qtyHandoverStatusName(status);
        };
    }

    /** 本步投入（开工默认投叶量 / 结束交接锁定投入的统一推导口径） */
    public BigDecimal resolveStepInput(TeaProcessOrder order, List<String> flow, int stepIndex) {
        return inputForStep(order, stepIndex);
    }

    /** 本步投入：上道已确认/旧派生产出；旧批次首次实称时取本道投叶量；首道取批次投料 */
    private BigDecimal inputForStep(TeaProcessOrder order, int stepIndex) {
        if (stepIndex <= 0) {
            return order.getAvailableWipKg() != null ? order.getAvailableWipKg() : order.getInputQtyKg();
        }
        StepQtyHandover prev = handoverRepository
                .findByBatchNoAndStepIndex(order.getBatchNo(), stepIndex - 1).orElse(null);
        if (prev != null && prev.getOutputQtyKg() != null
                && (Dict.QH_CONFIRMED.equals(prev.getStatus()) || Dict.QH_LEGACY.equals(prev.getStatus()))) {
            return prev.getOutputQtyKg();
        }
        if (prev == null && "LEGACY".equals(order.getQtyMode())) {
            // 旧批次第一道开始实称：本步投入取该道工序记录登记的投叶量（旧口径下收到的在制量）
            BigDecimal leaf = stepRecordRepository
                    .findByBatchNoOrderByStepIndexAsc(order.getBatchNo()).stream()
                    .filter(r -> r.getStepIndex() != null && r.getStepIndex() == stepIndex)
                    .map(com.tea.factory.domain.ProcessStepRecord::getParamLeafQty)
                    .findFirst().orElse(null);
            if (leaf != null && leaf.signum() > 0) {
                return leaf;
            }
        }
        // 旧派生前序的产出未过磅（在制批次尾节点）：用工单锁定的可用在制量承接
        if (prev != null && prev.getOutputQtyKg() == null
                && (Dict.QH_LEGACY.equals(prev.getStatus()) || Dict.QH_CONFIRMED.equals(prev.getStatus()))) {
            return order.getAvailableWipKg() != null ? order.getAvailableWipKg() : order.getInputQtyKg();
        }
        return order.getAvailableWipKg() != null ? order.getAvailableWipKg() : order.getInputQtyKg();
    }

    private void validateWeight(BigDecimal input, BigDecimal output) {
        if (output == null || output.signum() <= 0) {
            throw new BizException("实际产出重量必须大于 0");
        }
        if (input != null && output.compareTo(input.add(KG_TOLERANCE)) > 0) {
            throw new BizException("实际产出 " + output + "kg 大于本步投入 " + input
                    + "kg（制茶只有失水/损耗，重量不可能增加），请核对过磅读数后重新登记");
        }
    }

    private void applyLossAndLimit(StepQtyHandover h) {
        BigDecimal loss = h.getInputQtyKg().subtract(h.getOutputQtyKg()).setScale(2, RoundingMode.HALF_UP);
        h.setLossQtyKg(loss);
        h.setLossPct(loss.multiply(BigDecimal.valueOf(100))
                .divide(h.getInputQtyKg(), 2, RoundingMode.HALF_UP));
        Dict.LossRange range = Dict.lossRange(h.getTeaType(), h.getStepName());
        h.setLimitMinPct(range.minPct());
        h.setLimitMaxPct(range.maxPct());
    }

    private boolean isWithinLimit(StepQtyHandover h) {
        BigDecimal pct = h.getLossPct();
        if (pct == null) {
            return true;
        }
        return pct.compareTo(h.getLimitMinPct().subtract(PCT_TOLERANCE)) >= 0
                && pct.compareTo(h.getLimitMaxPct().add(PCT_TOLERANCE)) <= 0;
    }

    private StepQtyHandover mustGet(String handoverNo) {
        return handoverRepository.findByHandoverNo(handoverNo)
                .orElseThrow(() -> new BizException("交接单 " + handoverNo + " 不存在"));
    }

    /**
     * 状态冲突：后到者操作驳回——主事务回滚（不推进任何状态），另起独立事务留 REJECTED 痕。
     */
    private StepQtyHandover reject(StepQtyHandover h, String action, String operator, String reason) {
        auditRecorder.rejected(h.getHandoverNo(), h.getBatchNo(), h.getStepName(), action,
                h.getStatus(), operator == null || operator.isBlank() ? "未知" : operator, reason);
        throw new BizException(reason + "，本次操作未生效，请刷新查看已生效的交接结论");
    }

    private void writeLog(StepQtyHandover h, String action, String from, String to,
            String operator, String result, String detail) {
        StepQtyHandoverLog log = new StepQtyHandoverLog();
        log.setHandoverNo(h.getHandoverNo());
        log.setBatchNo(h.getBatchNo());
        log.setStepName(h.getStepName());
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperator(operator);
        log.setResult(result);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);
    }

    private String pct(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private String rangeText(StepQtyHandover h) {
        return pct(h.getLimitMinPct()) + "%~" + pct(h.getLimitMaxPct()) + "%";
    }

    private String modeName(String mode) {
        return switch (mode) {
            case "TRACKED" -> "逐道实称交接";
            case "LEGACY" -> "旧批历史派生";
            case "MIXED" -> "旧批中途转实称";
            default -> mode;
        };
    }

    private String currentStepName(TeaProcessOrder order) {
        return "DONE".equals(order.getCurrentStep()) ? "已完成（工单闭环）"
                : Dict.stepName(order.getTeaType(), order.getCurrentStep());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
