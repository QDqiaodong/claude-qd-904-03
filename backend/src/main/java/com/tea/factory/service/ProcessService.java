package com.tea.factory.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.Equipment;
import com.tea.factory.domain.ProcessStepRecord;
import com.tea.factory.domain.StepQtyHandover;
import com.tea.factory.domain.TeaProcessOrder;
import com.tea.factory.dto.OrderCreateRequest;
import com.tea.factory.dto.OrderDetailDTO;
import com.tea.factory.dto.StepRequest;
import com.tea.factory.dto.StepQtyNodeDTO;
import com.tea.factory.dto.TeaTypeYieldDTO;
import com.tea.factory.repository.EquipmentRepository;
import com.tea.factory.repository.ProcessStepRecordRepository;
import com.tea.factory.repository.TeaProcessOrderRepository;

/**
 * 制茶工序流转：工单按茶类的工序序列推进，不能跳步；
 * 杀青温度、发酵 / 渥堆时长不达标不允许结束本工序；
 * 每道工序结束须登记实际产出、损耗原因与交接人，下道以上道已确认产出为可用在制量，
 * 超损耗范围待复核期间工序不前进、下道不得开工。
 */
@Service
public class ProcessService {

    private final TeaProcessOrderRepository orderRepository;
    private final ProcessStepRecordRepository stepRepository;
    private final EquipmentRepository equipmentRepository;
    private final CleaningHandoverService cleaningHandoverService;
    private final StepQtyHandoverService qtyHandoverService;
    private final QtyStatsService qtyStatsService;

    public ProcessService(TeaProcessOrderRepository orderRepository,
            ProcessStepRecordRepository stepRepository,
            EquipmentRepository equipmentRepository,
            CleaningHandoverService cleaningHandoverService,
            StepQtyHandoverService qtyHandoverService,
            QtyStatsService qtyStatsService) {
        this.orderRepository = orderRepository;
        this.stepRepository = stepRepository;
        this.equipmentRepository = equipmentRepository;
        this.cleaningHandoverService = cleaningHandoverService;
        this.qtyHandoverService = qtyHandoverService;
        this.qtyStatsService = qtyStatsService;
    }

    /** 新建工单，批次号由 Hibernate 自定义生成器生成 */
    @Transactional
    public TeaProcessOrder createOrder(OrderCreateRequest request) {
        if (!Dict.isValidTeaType(request.getTeaType())) {
            throw new BizException("茶类不合法，可选：" + String.join("、", Dict.TEA_TYPES));
        }
        List<String> flow = Dict.flow(request.getTeaType());
        TeaProcessOrder order = new TeaProcessOrder();
        order.setTeaType(request.getTeaType());
        order.setProcessSeq(String.join(">", flow));
        order.setCurrentStep(flow.get(0));
        order.setCurrentStepIndex(0);
        order.setInputQtyKg(request.getInputQtyKg());
        order.setOutputQtyKg(BigDecimal.ZERO);
        // 新工单逐道实称：开工前可用在制量先锁为批次投料（原投料基准），随每道确认产出滚动
        order.setAvailableWipKg(request.getInputQtyKg());
        order.setQtyMode("TRACKED");
        order.setSourceTroughNo(request.getSourceTroughNo());
        order.setOperator(request.getOperator() == null ? "周师傅" : request.getOperator());
        order.setStatus("PROCESSING");
        order.setCreatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public List<OrderDetailDTO> orderList() {
        List<OrderDetailDTO> result = new ArrayList<>();
        for (TeaProcessOrder order : orderRepository.findTop20ByOrderByCreatedAtDesc()) {
            result.add(toDetail(order));
        }
        return result;
    }

    public OrderDetailDTO orderDetail(String batchNo) {
        TeaProcessOrder order = orderRepository.findById(batchNo)
                .orElseThrow(() -> new BizException("批次号 " + batchNo + " 不存在"));
        return toDetail(order);
    }

    /**
     * 开始某道工序：必须正好是当前工序，不能跳步；上道交接未确认（待复核 / 退回）不得开工。
     */
    @Transactional
    public ProcessStepRecord startStep(String batchNo, StepRequest request) {
        // 先抢订单行锁：与几乎同时发生的结束工序 / 复核结论串行，避免读到“工序已前进却仍待复核”
        TeaProcessOrder order = orderRepository.lockByBatchNo(batchNo)
                .orElseThrow(() -> new BizException("批次号 " + batchNo + " 不存在"));
        List<String> flow = Dict.flow(order.getTeaType());
        String step = requiredStep(request.getStepName());
        int index = flow.indexOf(step);
        if (index < 0) {
            throw new BizException("「" + Dict.stepName(order.getTeaType(), step) + "」不属于"
                    + Dict.teaTypeName(order.getTeaType()) + "的工序序列：" + joinFlow(order.getTeaType(), flow));
        }
        if ("DONE".equals(order.getStatus())) {
            throw new BizException("批次 " + batchNo + " 已经完成全部工序，不能再开工");
        }
        if (index > order.getCurrentStepIndex()) {
            throw new BizException("工序不能跳步，当前应先做「"
                    + Dict.stepName(order.getTeaType(), flow.get(order.getCurrentStepIndex())) + "」");
        }
        if (index < order.getCurrentStepIndex()) {
            throw new BizException("「" + Dict.stepName(order.getTeaType(), step) + "」已经做过了，当前工序是「"
                    + Dict.stepName(order.getTeaType(), flow.get(order.getCurrentStepIndex())) + "」");
        }
        Optional<ProcessStepRecord> running = stepRepository.findByBatchNoAndStepNameAndStatus(batchNo, step, "RUNNING");
        if (running.isPresent()) {
            throw new BizException("「" + Dict.stepName(order.getTeaType(), step) + "」正在执行中，请先结束本工序");
        }
        // 挂起闸门：本工序交接待复核 / 已退回时不允许重复开工（退回只能更正重交）
        qtyHandoverService.assertNotHeld(order, index);
        // 在制量闸门：待复核 / 已退回时这里直接拒绝，下道拿不到可用在制量；返回值即上道已确认在制量
        BigDecimal availableWip = qtyHandoverService.assertReadyAndInput(order, index);
        // 本步默认投叶量：旧批混合口径下可能取本道记录 / 工单可用在制量，由交接服务统一推导
        BigDecimal stepInput = qtyHandoverService.resolveStepInput(order, flow, index);

        ProcessStepRecord record = new ProcessStepRecord();
        record.setBatchNo(batchNo);
        record.setTeaType(order.getTeaType());
        record.setStepName(step);
        record.setStepIndex(index);
        record.setEquipmentCode(request.getEquipmentCode());
        record.setStartTime(LocalDateTime.now());
        record.setParamTempC(request.getParamTempC());
        record.setParamTemp2C(request.getParamTemp2C());
        record.setParamMinutes(request.getParamMinutes());
        record.setParamMinutes2(request.getParamMinutes2());
        record.setParamRpm(request.getParamRpm());
        record.setParamPressure(request.getParamPressure());
        // 投叶量默认带本步可用在制量（新批次＝上道已确认产出；旧批混合口径见 resolveStepInput）
        record.setParamLeafQty(request.getParamLeafQty() == null ? stepInput : request.getParamLeafQty());
        record.setHumidityPct(request.getHumidityPct());
        record.setParamProfile(request.getParamProfile());
        record.setOperator(request.getOperator() == null ? order.getOperator() : request.getOperator());
        record.setAbnormalNote(request.getAbnormalNote());
        record.setStatus("RUNNING");

        if (request.getEquipmentCode() != null && !request.getEquipmentCode().isBlank()) {
            // 先确认编号确实是制茶设备（摊青工序记录的 TQ-xx 是摊青槽号，不在设备表，保持只记录不上设备锁）。
            // 命中设备后取行锁再做开工校验：未开始/进行中/等待确认/故障（含未闭环交接单）一律不能开工；
            // 行锁保证与几乎同时发生的清洗确认 / 报障结论一致，不会读到互相矛盾的状态。
            Equipment equipment = equipmentRepository.findByEquipCode(request.getEquipmentCode());
            if (equipment != null) {
                equipment = equipmentRepository.lockByEquipCode(request.getEquipmentCode()).orElseThrow();
                cleaningHandoverService.assertReadyForNextBatch(equipment);
                equipment.setStatus("RUNNING");
                equipment.setCurrentBatchNo(batchNo);
                if (request.getParamTempC() != null) {
                    equipment.setCurrentTempC(request.getParamTempC());
                }
                equipmentRepository.save(equipment);
            }
        }
        return stepRepository.save(record);
    }

    /**
     * 结束本工序：校验工艺参数后登记在制量交接。
     * 损耗在正常范围 → 交接自动确认、工序推进到下一道；
     * 超范围 → 交接进入待复核，工序停在本道（下道开工被在制量闸门拦截）。
     */
    @Transactional
    public OrderDetailDTO finishStep(String batchNo, StepRequest request) {
        TeaProcessOrder order = orderRepository.lockByBatchNo(batchNo)
                .orElseThrow(() -> new BizException("批次号 " + batchNo + " 不存在"));
        List<String> flow = Dict.flow(order.getTeaType());
        String step = requiredStep(request.getStepName());
        int index = flow.indexOf(step);
        if (index != order.getCurrentStepIndex()) {
            throw new BizException("只能结束当前工序「"
                    + Dict.stepName(order.getTeaType(), flow.get(order.getCurrentStepIndex())) + "」");
        }
        ProcessStepRecord record = stepRepository.findByBatchNoAndStepNameAndStatus(batchNo, step, "RUNNING")
                .orElseThrow(() -> new BizException("「" + Dict.stepName(order.getTeaType(), step)
                        + "」还没有开始，请先点「开始本工序」"));

        // 参数回填
        if (request.getParamTempC() != null) {
            record.setParamTempC(request.getParamTempC());
        }
        if (request.getParamTemp2C() != null) {
            record.setParamTemp2C(request.getParamTemp2C());
        }
        if (request.getParamMinutes() != null) {
            record.setParamMinutes(request.getParamMinutes());
        }
        if (request.getParamMinutes2() != null) {
            record.setParamMinutes2(request.getParamMinutes2());
        }
        if (request.getParamRpm() != null) {
            record.setParamRpm(request.getParamRpm());
        }
        if (request.getParamPressure() != null) {
            record.setParamPressure(request.getParamPressure());
        }
        if (request.getParamLeafQty() != null) {
            record.setParamLeafQty(request.getParamLeafQty());
        }
        if (request.getHumidityPct() != null) {
            record.setHumidityPct(request.getHumidityPct());
        }
        if (request.getParamProfile() != null && !request.getParamProfile().isBlank()) {
            record.setParamProfile(request.getParamProfile());
        }
        if (request.getAbnormalNote() != null && !request.getAbnormalNote().isBlank()) {
            record.setAbnormalNote(request.getAbnormalNote());
        }

        // ---- 工艺规则校验 ----
        validateStepParams(order, step, record);

        record.setEndTime(LocalDateTime.now());
        record.setStatus("DONE");
        stepRepository.save(record);

        // ---- 在制量交接：实际产出 / 损耗原因 / 交接人 ----
        // 未提供实际产出（旧批次沿用旧操作）返回 null，不拦截推进；旧批从登记实称的第一道起自动进入逐道实称口径。
        String operator = record.getOperator() == null ? order.getOperator() : record.getOperator();
        StepQtyHandover handover = qtyHandoverService.submitOnFinish(order, step, index,
                request.getActualOutputQtyKg(), request.getLossReason(), request.getHandoverTo(), operator);

        com.tea.factory.domain.CleaningHandover cleaning = null;
        if (record.getEquipmentCode() != null && !record.getEquipmentCode().isBlank()
                && equipmentRepository.findByEquipCode(record.getEquipmentCode()) != null) {
            // 工序收尾（仅制茶设备）：对设备取行锁，形成清洗交接单（带出刚完成的批次与设备），设备转清洗中。
            // 下一班能否开工由交接单状态决定，不再只看设备上的“清洗中”标签。
            Equipment equipment = equipmentRepository.lockByEquipCode(record.getEquipmentCode()).orElseThrow();
            cleaning = cleaningHandoverService.createOnFinish(equipment, batchNo, step, operator);
        }

        boolean advanced = false;
        if (handover != null && Dict.QH_CONFIRMED.equals(handover.getStatus())) {
            // 范围内自动确认：交接产出落账并推进工序（与复核接受 / 更正确认同一条路径）
            qtyHandoverService.advanceAfterConfirmed(order, handover);
            advanced = true;
        } else if (handover == null) {
            // 旧批次未登记数量：沿用旧口径推进（最终产量仍按历史成茶率估算，仅旧批次）
            int next = index + 1;
            if (next >= flow.size()) {
                order.setStatus("DONE");
                order.setCurrentStep("DONE");
                order.setCurrentStepIndex(next);
                order.setOutputQtyKg(order.getInputQtyKg().multiply(Dict.yieldRate(order.getTeaType()))
                        .setScale(2, java.math.RoundingMode.HALF_UP));
                order.setAvailableWipKg(order.getOutputQtyKg());
            } else {
                order.setCurrentStep(flow.get(next));
                order.setCurrentStepIndex(next);
            }
            advanced = true;
        }
        // 待复核（PENDING_REVIEW）：currentStep/currentStepIndex/可用在制量一律不动，工序挂起
        orderRepository.save(order);

        OrderDetailDTO detail = toDetail(order);
        if (cleaning != null) {
            detail.setLastHandoverNo(cleaning.getHandoverNo());
        }
        if (handover != null) {
            detail.setQtyHandoverNo(handover.getHandoverNo());
            detail.setQtyHandoverStatus(handover.getStatus());
            detail.setQtyHandoverStatusName(Dict.qtyHandoverStatusName(handover.getStatus()));
            detail.setStepAdvanced(advanced);
        }
        return detail;
    }

    /** 工序参数校验 */
    private void validateStepParams(TeaProcessOrder order, String step, ProcessStepRecord record) {
        if (Dict.STEP_FIX.equals(step)) {
            BigDecimal min = Dict.minFixTemp(order.getTeaType());
            BigDecimal temp = record.getParamTempC();
            if (temp == null) {
                throw new BizException("杀青必须记录温度");
            }
            if (temp.compareTo(min) < 0) {
                throw new BizException(Dict.teaTypeName(order.getTeaType()) + "杀青温度不得低于 " + min
                        + "℃，当前仅 " + temp + "℃，不允许进入下一工序");
            }
        }
        if (Dict.STEP_FERMENT.equals(step)) {
            int min = Dict.minFermentMinutes(order.getTeaType());
            Integer minutes = record.getParamMinutes();
            if (minutes == null) {
                throw new BizException("发酵 / 渥堆必须记录时长");
            }
            if (minutes < min) {
                throw new BizException(Dict.stepName(order.getTeaType(), step) + "时长不得少于 " + min
                        + " 分钟，当前仅 " + minutes + " 分钟，不允许进入干燥");
            }
        }
        if (Dict.STEP_ROLL.equals(step) && record.getParamRpm() == null) {
            throw new BizException("揉捻必须记录转速");
        }
        if (Dict.STEP_DRY.equals(step) && record.getParamTempC() == null) {
            throw new BizException("干燥必须记录毛火温度");
        }
    }

    public List<TeaTypeYieldDTO> yieldByTeaType() {
        return qtyStatsService.aggregateByTeaType();
    }

    public List<ProcessStepRecord> stepsOf(String batchNo) {
        return stepRepository.findByBatchNoOrderByStepIndexAsc(batchNo);
    }

    private String requiredStep(String step) {
        if (step == null || step.isBlank()) {
            throw new BizException("工序名不能为空");
        }
        return step;
    }

    private String joinFlow(String teaType, List<String> flow) {
        List<String> names = new ArrayList<>();
        for (String item : flow) {
            names.add(Dict.stepName(teaType, item));
        }
        return String.join("→", names);
    }

    private OrderDetailDTO toDetail(TeaProcessOrder order) {
        List<String> flow = Dict.flow(order.getTeaType());
        OrderDetailDTO detail = new OrderDetailDTO();
        detail.setBatchNo(order.getBatchNo());
        detail.setTeaType(order.getTeaType());
        detail.setTeaTypeName(Dict.teaTypeName(order.getTeaType()));
        detail.setFlow(flow);
        List<String> names = new ArrayList<>();
        for (String item : flow) {
            names.add(Dict.stepName(order.getTeaType(), item));
        }
        detail.setFlowNames(names);
        detail.setCurrentStep(order.getCurrentStep());
        detail.setCurrentStepName("DONE".equals(order.getCurrentStep())
                ? "已完成"
                : Dict.stepName(order.getTeaType(), order.getCurrentStep()));
        detail.setCurrentStepIndex(order.getCurrentStepIndex());
        detail.setInputQtyKg(order.getInputQtyKg());
        detail.setOutputQtyKg(order.getOutputQtyKg());
        detail.setAvailableWipKg(order.getAvailableWipKg());
        detail.setQtyMode(order.getQtyMode());
        detail.setQtyModeName(Dict.qtyModeName(order.getQtyMode()));
        detail.setStatus(order.getStatus());
        detail.setSourceTroughNo(order.getSourceTroughNo());
        detail.setOperator(order.getOperator());
        detail.setSteps(stepRepository.findByBatchNoOrderByStepIndexAsc(order.getBatchNo()));
        // 同一条数量时间线 + 对账汇总，工单详情与产出率统计共用
        List<StepQtyNodeDTO> timeline = qtyHandoverService.timeline(order);
        detail.setQtyTimeline(timeline);
        detail.setQtyBalance(qtyHandoverService.balance(order, timeline));
        return detail;
    }
}
