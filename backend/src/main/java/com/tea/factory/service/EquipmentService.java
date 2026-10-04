package com.tea.factory.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.CleaningHandover;
import com.tea.factory.domain.DryingRoom;
import com.tea.factory.domain.Equipment;
import com.tea.factory.dto.EquipmentBoardDTO;
import com.tea.factory.repository.CleaningHandoverRepository;
import com.tea.factory.repository.DryingRoomRepository;
import com.tea.factory.repository.EquipmentRepository;

/**
 * 设备与烘房：设备状态 / 温度维护，烘房在烘批次与温控曲线。
 * 设备状态的清洗相关流转（清洗中 / 待确认 / 故障冻结 / 恢复待机）统一由清洗交接台驱动，
 * 这里的直接改状态接口只保留旧设备（无交接单）的原始维护能力，不能绕过交接流程。
 */
@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final DryingRoomRepository dryingRoomRepository;
    private final CleaningHandoverRepository handoverRepository;
    private final CleaningHandoverService cleaningHandoverService;

    public EquipmentService(EquipmentRepository equipmentRepository,
            DryingRoomRepository dryingRoomRepository,
            CleaningHandoverRepository handoverRepository,
            CleaningHandoverService cleaningHandoverService) {
        this.equipmentRepository = equipmentRepository;
        this.dryingRoomRepository = dryingRoomRepository;
        this.handoverRepository = handoverRepository;
        this.cleaningHandoverService = cleaningHandoverService;
    }

    /** 设备列表：附带清洗交接口径的开工可用性，旧设备沿用原状态 */
    @Transactional(readOnly = true)
    public List<EquipmentBoardDTO> board() {
        return cleaningHandoverService.board();
    }

    public List<Equipment> listEquipment() {
        return equipmentRepository.findAll();
    }

    public List<Equipment> listByType(String type) {
        return equipmentRepository.findByEquipTypeOrderByEquipCodeAsc(type);
    }

    /**
     * 直接维护设备状态（旧接口，保留给没有交接单的旧设备与温度维护）。
     * 存在交接单（尤其未闭环）的设备必须走清洗交接台，不能从这里直接改状态，否则会和交接单互相矛盾。
     */
    @Transactional
    public Equipment updateStatus(String equipCode, String status, BigDecimal tempC) {
        Equipment equipment = equipmentRepository.lockByEquipCode(equipCode)
                .orElseThrow(() -> new BizException("设备 " + equipCode + " 不存在"));
        if (!Dict.isValidEquipmentStatus(status)) {
            throw new BizException("设备状态不合法，可选：运行 / 待机 / 清洗中 / 待确认 / 故障");
        }

        CleaningHandover active = handoverRepository
                .findFirstByActiveKeyOrderByCreatedAtDesc(equipCode)
                .orElse(null);
        if (active != null) {
            throw new BizException("设备 " + equipCode + " 有未闭环清洗交接单 " + active.getHandoverNo()
                    + "（" + Dict.handoverStatusName(active.getStatus())
                    + "），请到「设备清洗交接台」操作，不能直接改状态");
        }

        CleaningHandover latest = handoverRepository.findFirstByEquipCodeOrderByCreatedAtDesc(equipCode);
        if (latest != null && "FAULT".equals(equipment.getStatus()) && !"FAULT".equals(status)) {
            // 已有交接单的设备故障必须通过交接台维修 / 复验放行，避免绕过留痕
            throw new BizException("设备 " + equipCode + " 的故障须在清洗交接单中登记维修并复验放行，不能直接解除");
        }
        if ("RUNNING".equals(equipment.getStatus()) && !"RUNNING".equals(status) && !"FAULT".equals(status)) {
            throw new BizException("设备正在运行，请先在工序台结束当前工序（将自动形成清洗交接单）");
        }
        if (("CLEANING".equals(status) || "PENDING_CONFIRM".equals(status))) {
            throw new BizException("「清洗中 / 待确认」由工序收尾与清洗交接流程自动置位，不能手工设置");
        }

        equipment.setStatus(status);
        if (tempC != null) {
            equipment.setCurrentTempC(tempC);
        }
        if ("RUNNING".equals(status)) {
            equipment.setLastMaintainAt(LocalDateTime.now());
        }
        return equipmentRepository.save(equipment);
    }

    public List<DryingRoom> listRooms() {
        return dryingRoomRepository.findAll();
    }

    /** 烘房进烘：登记在烘批次与温控曲线 */
    @Transactional
    public DryingRoom load(String roomNo, String batchNo, String tempCurve, BigDecimal targetTempC,
            Integer dryingMinutes) {
        DryingRoom room = dryingRoomRepository.findByRoomNo(roomNo);
        if (room == null) {
            throw new BizException("烘房 " + roomNo + " 不存在");
        }
        if ("FAULT".equals(room.getStatus())) {
            throw new BizException("烘房 " + roomNo + " 处于故障状态，不能进烘");
        }
        if (room.getCurrentBatchNo() != null && !room.getCurrentBatchNo().isBlank()
                && !room.getCurrentBatchNo().equals(batchNo)) {
            throw new BizException("烘房 " + roomNo + " 正在烘批次 " + room.getCurrentBatchNo() + "，请先出烘");
        }
        room.setCurrentBatchNo(batchNo);
        room.setTempCurve(tempCurve);
        room.setTargetTempC(targetTempC);
        room.setCurrentTempC(targetTempC);
        room.setDryingMinutes(dryingMinutes);
        room.setStatus("RUNNING");
        room.setUpdatedAt(LocalDateTime.now());
        return dryingRoomRepository.save(room);
    }

    /** 出烘 */
    @Transactional
    public DryingRoom unload(String roomNo) {
        DryingRoom room = dryingRoomRepository.findByRoomNo(roomNo);
        if (room == null) {
            throw new BizException("烘房 " + roomNo + " 不存在");
        }
        room.setCurrentBatchNo(null);
        room.setStatus("IDLE");
        room.setUpdatedAt(LocalDateTime.now());
        return dryingRoomRepository.save(room);
    }
}
