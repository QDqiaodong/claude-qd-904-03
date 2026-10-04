package com.tea.factory.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.CleaningHandover;
import com.tea.factory.domain.CleaningHandoverLog;
import com.tea.factory.domain.Equipment;
import com.tea.factory.dto.CleaningActionRequest;
import com.tea.factory.dto.CleaningHandoverDTO;
import com.tea.factory.dto.EquipmentBoardDTO;
import com.tea.factory.generator.NoSequenceStore;
import com.tea.factory.repository.CleaningHandoverLogRepository;
import com.tea.factory.repository.CleaningHandoverRepository;
import com.tea.factory.repository.EquipmentRepository;

/**
 * 设备清洗交接：
 * 1. 工序收尾自动形成交接单，带出刚完成的批次与设备；
 * 2. 清洗人员登记残留风险、清洗方式、起止时间，可标记完成或报障；
 * 3. 负责人确认后设备才恢复 IDLE；未开始 / 进行中 / 等待确认 / 故障一律不能开工；
 * 4. 没有交接单的旧设备沿用原状态，不统一锁死；
 * 5. 完成与报障冲突的取舍（本系统口径，交接详情中写明）：
 *    - 尚未经负责人确认：报障立即冻结，本次清洗结论作废，修复后重新清洗；
 *    - 已经负责人确认：保留已确认结果并转维修，复验合格后恢复，不推翻既往责任；
 * 6. 所有状态变更都以 equipment 行级写锁串行化，多人同时确认 / 报障只有一个结论生效，
 *    后到者操作被驳回并能在留痕与刷新后的页面上看到先前已生效状态。
 */
@Service
public class CleaningHandoverService {

    private final CleaningHandoverRepository handoverRepository;
    private final CleaningHandoverLogRepository logRepository;
    private final EquipmentRepository equipmentRepository;
    private final CleaningAuditRecorder auditRecorder;

    public CleaningHandoverService(CleaningHandoverRepository handoverRepository,
            CleaningHandoverLogRepository logRepository,
            EquipmentRepository equipmentRepository,
            CleaningAuditRecorder auditRecorder) {
        this.handoverRepository = handoverRepository;
        this.logRepository = logRepository;
        this.equipmentRepository = equipmentRepository;
        this.auditRecorder = auditRecorder;
    }

    // ============================== 工序收尾：形成交接单 ==============================

    /**
     * 工序收尾形成交接单（由工序结束事务调用，与工序推进同事务；调用方须已持有设备行锁）。
     * 已存在未闭环交接单时不再重复开单（防止重复收尾把刚形成的单冲掉）。
     */
    @Transactional
    public CleaningHandover createOnFinish(Equipment lockedEquipment, String batchNo, String stepName, String operator) {
        CleaningHandover existing = handoverRepository
                .findFirstByActiveKeyOrderByCreatedAtDesc(lockedEquipment.getEquipCode())
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        CleaningHandover handover = new CleaningHandover();
        handover.setHandoverNo(NoSequenceStore.handoverNo());
        handover.setEquipCode(lockedEquipment.getEquipCode());
        handover.setEquipName(lockedEquipment.getEquipName());
        handover.setEquipType(lockedEquipment.getEquipType());
        handover.setBatchNo(batchNo);
        handover.setStepName(stepName);
        handover.setStatus(Dict.HO_PENDING);
        handover.setActiveKey(lockedEquipment.getEquipCode());
        handover.setCreatedBy(operator);
        handover.setCreatedAt(now);
        handover.setUpdatedAt(now);
        CleaningHandover saved = handoverRepository.save(handover);

        lockedEquipment.setStatus("CLEANING");
        lockedEquipment.setCurrentBatchNo(null);
        equipmentRepository.save(lockedEquipment);

        writeLog(saved, "CREATE", null, Dict.HO_PENDING, operator, "EFFECTIVE",
                "工序收尾形成交接单：批次 " + nvl(batchNo) + "、设备 " + lockedEquipment.getEquipCode()
                        + " 待清洗，清洗完成并经负责人确认前不能用于下一批次");
        return saved;
    }

    // ============================== 清洗人员操作 ==============================

    /** 开始清洗：未开始 -> 进行中，登记残留风险、清洗方式、开始时间与清洗人员 */
    @Transactional
    public CleaningHandover start(String handoverNo, CleaningActionRequest req) {
        if (!Dict.isValidResidualRisk(req.getResidualRisk())) {
            throw new BizException("请选择残留风险：低 / 中 / 高");
        }
        if (!Dict.isValidCleanMethod(req.getCleanMethod())) {
            throw new BizException("请选择清洗方式");
        }
        Equipment locked = lockEquipmentOf(handoverNo);
        CleaningHandover handover = mustGet(handoverNo);
        guardStatus(handover, Dict.HO_PENDING, "开始清洗", req.getOperator());

        LocalDateTime start = req.getCleanStartTime() == null ? LocalDateTime.now() : req.getCleanStartTime();
        String from = handover.getStatus();
        handover.setStatus(Dict.HO_IN_PROGRESS);
        handover.setResidualRisk(req.getResidualRisk());
        handover.setCleanMethod(req.getCleanMethod());
        handover.setCleanStartTime(start);
        handover.setCleaner(req.getOperator());
        handover.setUpdatedAt(LocalDateTime.now());
        handoverRepository.save(handover);

        locked.setStatus("CLEANING");
        equipmentRepository.save(locked);

        writeLog(handover, "START", from, Dict.HO_IN_PROGRESS, req.getOperator(), "EFFECTIVE",
                "开始清洗：残留风险" + req.getResidualRisk() + "，方式「" + req.getCleanMethod()
                        + "」，开始 " + start + "。设备仍锁定，不能开工");
        return handover;
    }

    /** 标记清洗完成：进行中 -> 等待负责人确认（设备 PENDING_CONFIRM，仍不能开工） */
    @Transactional
    public CleaningHandover complete(String handoverNo, CleaningActionRequest req) {
        Equipment locked = lockEquipmentOf(handoverNo);
        CleaningHandover handover = mustGet(handoverNo);
        guardStatus(handover, Dict.HO_IN_PROGRESS, "标记清洗完成", req.getOperator());

        LocalDateTime start = handover.getCleanStartTime();
        LocalDateTime end = req.getCleanEndTime() == null ? LocalDateTime.now() : req.getCleanEndTime();
        if (end.isBefore(start)) {
            throw new BizException("清洗结束时间不能早于开始时间（" + start + "）");
        }
        if (req.getResidualRisk() != null && !Dict.isValidResidualRisk(req.getResidualRisk())) {
            throw new BizException("残留风险不合法：低 / 中 / 高");
        }
        if (req.getCleanMethod() != null && !Dict.isValidCleanMethod(req.getCleanMethod())) {
            throw new BizException("清洗方式不合法");
        }

        String from = handover.getStatus();
        handover.setStatus(Dict.HO_AWAIT_CONFIRM);
        if (req.getResidualRisk() != null) {
            handover.setResidualRisk(req.getResidualRisk());
        }
        if (req.getCleanMethod() != null) {
            handover.setCleanMethod(req.getCleanMethod());
        }
        handover.setCleanEndTime(end);
        handover.setCleaner(req.getOperator());
        handover.setUpdatedAt(LocalDateTime.now());
        handoverRepository.save(handover);

        locked.setStatus("PENDING_CONFIRM");
        equipmentRepository.save(locked);

        writeLog(handover, "COMPLETE", from, Dict.HO_AWAIT_CONFIRM, req.getOperator(), "EFFECTIVE",
                "清洗完成：" + start + " 至 " + end + "，等待负责人确认；确认前设备不能用于下一批次");
        return handover;
    }

    /**
     * 报故障。按是否已经负责人确认自动取舍：
     * - 清洗未开始 / 进行中 / 等待确认：立即冻结（FAULT_FREEZE），清洗结论作废，修复后重新清洗；
     * - 已确认：保留已确认结果并转维修（KEEP_AND_REPAIR），复验合格后恢复。
     */
    @Transactional
    public CleaningHandover reportFault(String handoverNo, CleaningActionRequest req) {
        if (req.getFaultNote() == null || req.getFaultNote().isBlank()) {
            throw new BizException("请填写故障现象");
        }
        Equipment locked = lockEquipmentOf(handoverNo);
        CleaningHandover handover = mustGet(handoverNo);

        String status = handover.getStatus();
        if (Dict.HO_FAULT_FREEZE.equals(status)) {
            throw new BizException("该交接单已处于故障冻结状态，请维修合格后点「修复后重新清洗」");
        }
        if (Dict.HO_KEEP_AND_REPAIR.equals(status)) {
            throw new BizException("该交接单已转维修，请负责人复验合格后放行");
        }

        LocalDateTime now = LocalDateTime.now();
        handover.setFaultNote(req.getFaultNote());
        handover.setUpdatedAt(now);

        if (Dict.HO_CONFIRMED.equals(status)) {
            // 已确认：保留已确认结果并转维修
            handover.setStatus(Dict.HO_KEEP_AND_REPAIR);
            handover.setFaultAfterConfirm(true);
            handover.setActiveKey(handover.getEquipCode()); // 重新进入未闭环，重新锁定开工
            handover.setDecisionNote("故障发生在负责人确认之后：保留已确认的清洗结果并转维修，"
                    + "不推翻既往确认结论；设备在复验合格前为故障状态，不能用于下一批次开工。");
            handover.setRepairer(req.getOperator());
            locked.setStatus("FAULT");
            handoverRepository.save(handover);
            equipmentRepository.save(locked);
            writeLog(handover, "REPORT_FAULT", status, Dict.HO_KEEP_AND_REPAIR, req.getOperator(), "EFFECTIVE",
                    "确认后报障（" + req.getFaultNote() + "）：保留已确认清洗结果并转维修，复验合格前设备仍不能开工");
        } else {
            // 未确认（PENDING / IN_PROGRESS / AWAIT_CONFIRM）：立即冻结，本次清洗结论作废
            handover.setStatus(Dict.HO_FAULT_FREEZE);
            handover.setFaultAfterConfirm(false);
            handover.setDecisionNote("故障发生在负责人确认之前：安全优先，报障立即冻结，"
                    + "本次尚未确认的清洗结论作废；设备转故障，不能用于下一批次开工，维修合格后须重新清洗并重新确认。");
            locked.setStatus("FAULT");
            handoverRepository.save(handover);
            equipmentRepository.save(locked);
            writeLog(handover, "REPORT_FAULT", status, Dict.HO_FAULT_FREEZE, req.getOperator(), "EFFECTIVE",
                    "清洗确认前报障（" + req.getFaultNote() + "）：立即冻结设备，本次清洗结论作废，"
                            + "维修合格后重新清洗、重新确认，期间不能开工");
        }
        return handover;
    }

    // ============================== 负责人操作 ==============================

    /** 负责人确认设备恢复可用：等待确认 -> 已确认，设备 IDLE */
    @Transactional
    public CleaningHandover confirm(String handoverNo, CleaningActionRequest req) {
        Equipment locked = lockEquipmentOf(handoverNo);
        CleaningHandover handover = mustGet(handoverNo);
        guardStatus(handover, Dict.HO_AWAIT_CONFIRM, "确认设备恢复可用", req.getOperator());

        LocalDateTime now = LocalDateTime.now();
        String from = handover.getStatus();
        handover.setStatus(Dict.HO_CONFIRMED);
        handover.setActiveKey(null); // 闭环
        handover.setConfirmedBy(req.getOperator());
        handover.setConfirmedAt(now);
        handover.setUpdatedAt(now);
        handoverRepository.save(handover);

        locked.setStatus("IDLE");
        equipmentRepository.save(locked);

        writeLog(handover, "CONFIRM", from, Dict.HO_CONFIRMED, req.getOperator(), "EFFECTIVE",
                "负责人确认设备恢复可用，已转为待机，可承接下一批次开工");
        return handover;
    }

    /** 故障冻结设备维修合格后重新清洗：FAULT_FREEZE -> PENDING（新的一轮清洗确认） */
    @Transactional
    public CleaningHandover reopenAfterRepair(String handoverNo, CleaningActionRequest req) {
        if (req.getRepairNote() == null || req.getRepairNote().isBlank()) {
            throw new BizException("请填写维修情况，再发起重新清洗");
        }
        Equipment locked = lockEquipmentOf(handoverNo);
        CleaningHandover handover = mustGet(handoverNo);
        guardStatus(handover, Dict.HO_FAULT_FREEZE, "修复后重新清洗", req.getOperator());

        LocalDateTime now = LocalDateTime.now();
        String from = handover.getStatus();
        handover.setStatus(Dict.HO_PENDING);
        handover.setRepairNote(req.getRepairNote());
        handover.setRepairer(req.getOperator());
        handover.setRepairedAt(now);
        // 新一轮清洗：清空上一轮未确认的清洗登记，保留故障与决策说明
        handover.setResidualRisk(null);
        handover.setCleanMethod(null);
        handover.setCleanStartTime(null);
        handover.setCleanEndTime(null);
        handover.setCleaner(null);
        handover.setUpdatedAt(now);
        handoverRepository.save(handover);

        locked.setStatus("CLEANING");
        equipmentRepository.save(locked);

        writeLog(handover, "REOPEN", from, Dict.HO_PENDING, req.getOperator(), "EFFECTIVE",
                "维修合格（" + req.getRepairNote() + "），原清洗结论作废，重新开始清洗；确认前不能开工");
        return handover;
    }

    /** 保留结果转维修设备复验合格：KEEP_AND_REPAIR -> CONFIRMED，设备 IDLE */
    @Transactional
    public CleaningHandover recheckAndRelease(String handoverNo, CleaningActionRequest req) {
        if (req.getRepairNote() == null || req.getRepairNote().isBlank()) {
            throw new BizException("请填写复验情况");
        }
        Equipment locked = lockEquipmentOf(handoverNo);
        CleaningHandover handover = mustGet(handoverNo);
        guardStatus(handover, Dict.HO_KEEP_AND_REPAIR, "复验放行", req.getOperator());

        LocalDateTime now = LocalDateTime.now();
        String from = handover.getStatus();
        handover.setStatus(Dict.HO_CONFIRMED);
        handover.setActiveKey(null);
        handover.setRepairNote(appendRepair(handover.getRepairNote(), req.getRepairNote()));
        handover.setRepairer(req.getOperator());
        handover.setRepairedAt(now);
        handover.setConfirmedBy(req.getOperator());
        handover.setConfirmedAt(now);
        handover.setUpdatedAt(now);
        handoverRepository.save(handover);

        locked.setStatus("IDLE");
        equipmentRepository.save(locked);

        writeLog(handover, "RECHECK", from, Dict.HO_CONFIRMED, req.getOperator(), "EFFECTIVE",
                "维修后复验合格（" + req.getRepairNote() + "）：沿用此前已确认的清洗结果，设备恢复待机，可开工");
        return handover;
    }

    // ============================== 查询 ==============================

    @Transactional(readOnly = true)
    public List<CleaningHandoverDTO> list(String status) {
        List<CleaningHandover> list = (status == null || status.isBlank())
                ? handoverRepository.findAllByOrderByCreatedAtDesc()
                : handoverRepository.findByStatusOrderByCreatedAtDesc(status);
        List<CleaningHandoverDTO> result = new ArrayList<>();
        for (CleaningHandover handover : list) {
            result.add(toDetail(handover, false));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public CleaningHandoverDTO detail(String handoverNo) {
        return toDetail(mustGet(handoverNo), true);
    }

    @Transactional(readOnly = true)
    public List<EquipmentBoardDTO> board() {
        List<Equipment> all = equipmentRepository.findAll();
        List<EquipmentBoardDTO> result = new ArrayList<>();
        for (Equipment equipment : all) {
            CleaningHandover latest = handoverRepository.findFirstByEquipCodeOrderByCreatedAtDesc(equipment.getEquipCode());
            CleaningHandover active = latest != null && Dict.isHandoverActive(latest.getStatus()) ? latest : null;
            result.add(toBoard(equipment, latest, active));
        }
        result.sort((a, b) -> a.getEquipCode().compareTo(b.getEquipCode()));
        return result;
    }

    /**
     * 开工闸门：工序开工前调用。设备存在未闭环交接单时一律拒绝；
     * 没有交接单的旧设备沿用原状态规则（仅故障拒绝），不统一锁死。
     * 调用方须已持有该设备行锁（传入加锁后的设备），保证与同时发生的确认 / 报障结论一致。
     */
    public void assertReadyForNextBatch(Equipment locked) {
        CleaningHandover active = handoverRepository
                .findFirstByActiveKeyOrderByCreatedAtDesc(locked.getEquipCode())
                .orElse(null);
        if (active != null) {
            throw new BizException("设备 " + locked.getEquipName() + " 清洗交接单 " + active.getHandoverNo()
                    + " 当前为「" + Dict.handoverStatusName(active.getStatus()) + "」，"
                    + lockReason(active) + "，不能开工");
        }
        if ("FAULT".equals(locked.getStatus())) {
            throw new BizException("设备 " + locked.getEquipName() + " 处于故障状态，不能开工");
        }
        if ("RUNNING".equals(locked.getStatus())) {
            throw new BizException("设备 " + locked.getEquipName() + " 正在加工批次 "
                    + nvl(locked.getCurrentBatchNo()) + "，请先结束当前工序（将自动形成清洗交接单）");
        }
        if ("CLEANING".equals(locked.getStatus()) || "PENDING_CONFIRM".equals(locked.getStatus())) {
            // 理论上不会到这里（有未闭环交接单已先拦），防御性兜底，避免设备状态与交接单短暂不一致时漏放
            throw new BizException("设备 " + locked.getEquipName() + " 处于"
                    + Dict.equipmentStatusName(locked.getStatus()) + "状态，清洗交接未闭环，不能开工");
        }
    }

    // ============================== 组装 ==============================

    private EquipmentBoardDTO toBoard(Equipment equipment, CleaningHandover latest, CleaningHandover active) {
        EquipmentBoardDTO dto = new EquipmentBoardDTO();
        dto.setId(equipment.getId());
        dto.setEquipCode(equipment.getEquipCode());
        dto.setEquipName(equipment.getEquipName());
        dto.setEquipType(equipment.getEquipType());
        dto.setModel(equipment.getModel());
        dto.setCapacityKg(equipment.getCapacityKg());
        dto.setCurrentTempC(equipment.getCurrentTempC());
        dto.setStatus(equipment.getStatus());
        dto.setStatusName(Dict.equipmentStatusName(equipment.getStatus()));
        dto.setWorkshop(equipment.getWorkshop());
        dto.setCurrentBatchNo(equipment.getCurrentBatchNo());
        dto.setHasHandover(latest != null);
        dto.setLegacy(latest == null);
        if (latest != null) {
            dto.setHandoverBatchNo(latest.getBatchNo());
        }
        if (active != null) {
            dto.setActiveHandoverNo(active.getHandoverNo());
            dto.setHandoverStatus(active.getStatus());
            dto.setHandoverStatusName(Dict.handoverStatusName(active.getStatus()));
            dto.setAvailableForNextBatch(false);
            dto.setUnavailableReason(lockReason(active));
        } else {
            // 无未闭环交接单：有单且已确认 -> 可开工；旧设备沿用原状态（除故障 / 运行中均可）
            boolean fault = "FAULT".equals(equipment.getStatus());
            boolean running = "RUNNING".equals(equipment.getStatus());
            dto.setAvailableForNextBatch(!fault && !running);
            dto.setUnavailableReason(fault ? "设备故障" : running ? "正在加工批次 " + nvl(equipment.getCurrentBatchNo()) : null);
        }
        return dto;
    }

    private CleaningHandoverDTO toDetail(CleaningHandover handover, boolean withLogs) {
        CleaningHandoverDTO dto = new CleaningHandoverDTO();
        dto.setHandoverNo(handover.getHandoverNo());
        dto.setEquipCode(handover.getEquipCode());
        dto.setEquipName(handover.getEquipName());
        dto.setEquipType(handover.getEquipType());
        dto.setBatchNo(handover.getBatchNo());
        dto.setStepName(handover.getStepName());
        dto.setStepNameCn(Dict.stepName(handover.getStepName()));
        dto.setStatus(handover.getStatus());
        dto.setStatusName(Dict.handoverStatusName(handover.getStatus()));
        dto.setResidualRisk(handover.getResidualRisk());
        dto.setCleanMethod(handover.getCleanMethod());
        dto.setCleanStartTime(handover.getCleanStartTime());
        dto.setCleanEndTime(handover.getCleanEndTime());
        dto.setCleaner(handover.getCleaner());
        dto.setFaultNote(handover.getFaultNote());
        dto.setFaultAfterConfirm(handover.getFaultAfterConfirm());
        dto.setDecisionNote(handover.getDecisionNote());
        dto.setRepairNote(handover.getRepairNote());
        dto.setRepairer(handover.getRepairer());
        dto.setRepairedAt(handover.getRepairedAt());
        dto.setCreatedBy(handover.getCreatedBy());
        dto.setConfirmedBy(handover.getConfirmedBy());
        dto.setConfirmedAt(handover.getConfirmedAt());
        dto.setCreatedAt(handover.getCreatedAt());
        dto.setUpdatedAt(handover.getUpdatedAt());

        boolean active = Dict.isHandoverActive(handover.getStatus());
        dto.setLocked(active);
        dto.setLockReason(active ? lockReason(handover) : "负责人已确认，设备恢复可用，可承接下一批次");
        dto.setAllowedActions(allowedActions(handover.getStatus()));
        if (withLogs) {
            dto.setLogs(logRepository.findByHandoverNoOrderByCreatedAtAsc(handover.getHandoverNo()));
        }
        return dto;
    }

    private List<String> allowedActions(String status) {
        List<String> actions = new ArrayList<>();
        switch (status) {
            case Dict.HO_PENDING -> actions.add("START");
            case Dict.HO_IN_PROGRESS -> actions.add("COMPLETE");
            case Dict.HO_AWAIT_CONFIRM -> actions.add("CONFIRM");
            case Dict.HO_FAULT_FREEZE -> actions.add("REOPEN");
            case Dict.HO_KEEP_AND_REPAIR -> actions.add("RECHECK");
            default -> {
                // CONFIRMED 无正向流转动作
            }
        }
        // 非故障冻结 / 维修中均可报障；已确认也允许报障（走保留结果转维修）
        if (!Dict.HO_FAULT_FREEZE.equals(status) && !Dict.HO_KEEP_AND_REPAIR.equals(status)) {
            actions.add("REPORT_FAULT");
        }
        return actions;
    }

    private String lockReason(CleaningHandover handover) {
        return switch (handover.getStatus()) {
            case Dict.HO_PENDING -> "清洗尚未开始";
            case Dict.HO_IN_PROGRESS -> "清洗进行中";
            case Dict.HO_AWAIT_CONFIRM -> "清洗完成，等待负责人确认";
            case Dict.HO_FAULT_FREEZE -> "清洗确认前报障，设备已故障冻结，须维修合格后重新清洗确认";
            case Dict.HO_KEEP_AND_REPAIR -> "保留已确认清洗结果并转维修，复验合格前";
            default -> "";
        } + "，暂不能用于下一批次开工";
    }

    // ============================== 内部工具 ==============================

    /**
     * 状态不符说明他人的结论已经先生效：本次操作驳回（不改动任何状态），
     * 并以独立事务补一条 REJECTED 留痕，随后抛出业务异常提示刷新查看已生效状态。
     */
    private void guardStatus(CleaningHandover handover, String expected, String action, String operator) {
        if (expected.equals(handover.getStatus())) {
            return;
        }
        String reason = "当前单据状态为「" + Dict.handoverStatusName(handover.getStatus()) + "」，不能执行「" + action
                + "」；已有他人的操作先生效，本次操作被驳回";
        auditRecorder.rejected(handover.getHandoverNo(), handover.getEquipCode(), action,
                handover.getStatus(), operator == null || operator.isBlank() ? "未知" : operator, reason);
        throw new BizException(reason + "，请刷新查看已生效状态"
                + (Dict.HO_CONFIRMED.equals(handover.getStatus()) ? "（设备已由负责人确认恢复）" : ""));
    }

    private CleaningHandover mustGet(String handoverNo) {
        return handoverRepository.findByHandoverNo(handoverNo)
                .orElseThrow(() -> new BizException("交接单 " + handoverNo + " 不存在"));
    }

    private Equipment lockEquipmentOf(String handoverNo) {
        CleaningHandover handover = handoverRepository.findByHandoverNo(handoverNo)
                .orElseThrow(() -> new BizException("交接单 " + handoverNo + " 不存在"));
        return equipmentRepository.lockByEquipCode(handover.getEquipCode())
                .orElseThrow(() -> new BizException("设备 " + handover.getEquipCode() + " 不存在"));
    }

    private void writeLog(CleaningHandover handover, String action, String from, String to,
            String operator, String result, String detail) {
        CleaningHandoverLog log = new CleaningHandoverLog();
        log.setHandoverNo(handover.getHandoverNo());
        log.setEquipCode(handover.getEquipCode());
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperator(operator);
        log.setResult(result);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);
    }

    private String appendRepair(String oldNote, String newNote) {
        if (oldNote == null || oldNote.isBlank()) {
            return newNote;
        }
        return oldNote + "；复验：" + newNote;
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
