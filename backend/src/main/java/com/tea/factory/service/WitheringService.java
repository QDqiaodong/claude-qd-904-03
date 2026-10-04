package com.tea.factory.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.WitheringTrough;
import com.tea.factory.dto.WitheringRequest;
import com.tea.factory.repository.WitheringTroughRepository;

/**
 * 摊青与萎凋：登记环境温湿度、失水率，达标后转为「可杀青」。
 *
 * 槽号复用模型：每轮摊青是 withering_trough 里的一条独立记录（批次履历）。
 * 开槽 INSERT 新记录，下槽把当前记录置 DONE 并写 released_at，槽号随即释放、可承接新批次；
 * 同一槽号至多一条在槽记录，由数据库 active_no 唯一索引兜底（并发开槽只有一人成功）。
 */
@Service
public class WitheringService {

    private final WitheringTroughRepository troughRepository;

    public WitheringService(WitheringTroughRepository troughRepository) {
        this.troughRepository = troughRepository;
    }

    /** 当前在槽（摊青中 / 可杀青），历史下槽记录不在其列 */
    public List<WitheringTrough> list() {
        return troughRepository.findByReleasedAtIsNullOrderByTroughNoAsc();
    }

    /** 历史完成（已下槽）批次履历，最近下槽在前；troughNo 非空时只看该槽号 */
    public List<WitheringTrough> history(String troughNo) {
        if (troughNo != null && !troughNo.isBlank()) {
            return troughRepository.findByTroughNoAndReleasedAtIsNotNullOrderByOpenedAtDesc(troughNo.trim());
        }
        return troughRepository.findByReleasedAtIsNotNullOrderByReleasedAtDesc();
    }

    /** 开槽登记：每轮摊青生成独立记录，已下槽的槽号可以再次开槽 */
    @Transactional
    public WitheringTrough open(WitheringRequest request) {
        String troughNo = request.getTroughNo();
        if (troughNo == null || troughNo.isBlank()) {
            throw new BizException("槽号不能为空");
        }
        troughNo = troughNo.trim();
        if (!Dict.isValidWitherMethod(request.getWitherMethod())) {
            throw new BizException("摊青方式只能是：" + String.join(" / ", Dict.WITHER_METHODS));
        }
        if (!Dict.isValidTeaType(request.getTeaType())) {
            throw new BizException("茶类不合法，可选：" + String.join("、", Dict.TEA_TYPES));
        }
        BigDecimal thickness = request.getLeafThicknessCm() == null ? BigDecimal.valueOf(3) : request.getLeafThicknessCm();
        if (thickness.compareTo(BigDecimal.valueOf(1)) < 0 || thickness.compareTo(BigDecimal.valueOf(15)) > 0) {
            throw new BizException("摊叶厚度应在 1~15 cm 之间，过厚会闷青、过薄会失水过快");
        }

        WitheringTrough trough = new WitheringTrough();
        trough.setTroughNo(troughNo);
        trough.setIntakeNo(request.getIntakeNo());
        trough.setBatchNo(request.getBatchNo());
        trough.setTeaType(request.getTeaType());
        trough.setWitherMethod(request.getWitherMethod());
        trough.setLeafThicknessCm(thickness);
        trough.setWitherHours(request.getWitherHours());
        trough.setTempC(request.getTempC());
        trough.setHumidityPct(request.getHumidityPct());
        trough.setLeafWeightKg(request.getLeafWeightKg());
        trough.setCurrentLossPct(request.getCurrentLossPct() == null ? BigDecimal.ZERO : request.getCurrentLossPct());
        trough.setTargetLossPct(Dict.targetLossPct(request.getTeaType()));
        trough.setStatus("WITHERING");
        LocalDateTime now = LocalDateTime.now();
        trough.setOpenedAt(now);
        trough.setUpdatedAt(now);
        try {
            return troughRepository.saveAndFlush(trough);
        } catch (DataIntegrityViolationException e) {
            // 两人同时用同一槽号开槽：先到的 INSERT 已占用 active_no，后者在此唯一索引上失败。
            throw new BizException("槽号 " + troughNo + " 已在用，请先下槽后再开槽");
        }
    }

    /** 登记环境温湿度与失水率（只作用于该槽号当前在槽的一轮） */
    @Transactional
    public WitheringTrough log(String troughNo, WitheringRequest request) {
        WitheringTrough trough = requireActiveTrough(troughNo);
        if (request.getTempC() != null) {
            trough.setTempC(request.getTempC());
        }
        if (request.getHumidityPct() != null) {
            trough.setHumidityPct(request.getHumidityPct());
        }
        if (request.getWitherHours() != null) {
            trough.setWitherHours(request.getWitherHours());
        }
        if (request.getLeafThicknessCm() != null) {
            trough.setLeafThicknessCm(request.getLeafThicknessCm());
        }
        if (request.getCurrentLossPct() != null) {
            BigDecimal loss = request.getCurrentLossPct();
            if (loss.compareTo(BigDecimal.ZERO) < 0 || loss.compareTo(BigDecimal.valueOf(90)) > 0) {
                throw new BizException("失水率应在 0~90% 之间");
            }
            trough.setCurrentLossPct(loss);
        }
        trough.setStatus(trough.getCurrentLossPct().compareTo(trough.getTargetLossPct()) >= 0 ? "READY" : "WITHERING");
        trough.setUpdatedAt(LocalDateTime.now());
        return troughRepository.save(trough);
    }

    /** 下槽（摊青结束，鲜叶转投制茶工单）：本轮记录置 DONE 并释放槽号，历史保留可追溯 */
    @Transactional
    public WitheringTrough release(String troughNo) {
        WitheringTrough trough = requireActiveTrough(troughNo);
        if (trough.getCurrentLossPct().compareTo(trough.getTargetLossPct()) < 0) {
            throw new BizException("槽号 " + troughNo + " 当前失水率 " + trough.getCurrentLossPct()
                    + "% 未达目标 " + trough.getTargetLossPct() + "%，不允许下槽");
        }
        LocalDateTime now = LocalDateTime.now();
        trough.setStatus("DONE");
        trough.setReleasedAt(now);
        trough.setUpdatedAt(now);
        return troughRepository.save(trough);
    }

    /** 当前可杀青（达标待放行）槽，只含在槽记录，已下槽历史不计入 */
    public List<WitheringTrough> readyList() {
        return troughRepository.findByStatusAndReleasedAtIsNullOrderByTroughNoAsc("READY");
    }

    /** 取槽号当前在槽记录（加行锁）；槽号空闲（含只有历史完成记录）时给出可复用提示 */
    private WitheringTrough requireActiveTrough(String troughNo) {
        if (troughNo == null || troughNo.isBlank()) {
            throw new BizException("槽号不能为空");
        }
        return troughRepository.lockActiveByTroughNo(troughNo.trim())
                .orElseThrow(() -> new BizException("槽号 " + troughNo + " 当前无在槽批次，可直接开槽承接新批次"));
    }
}
