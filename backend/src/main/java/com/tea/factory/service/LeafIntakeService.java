package com.tea.factory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.LeafIntake;
import com.tea.factory.dto.IntakeRequest;
import com.tea.factory.dto.SupplierQualityDTO;
import com.tea.factory.repository.LeafIntakeRepository;

/**
 * 鲜叶进厂与验收：含水量 / 杂质率超标自动扣重，按等级单价结算。
 */
@Service
public class LeafIntakeService {

    private final LeafIntakeRepository intakeRepository;

    public LeafIntakeService(LeafIntakeRepository intakeRepository) {
        this.intakeRepository = intakeRepository;
    }

    /**
     * 验收一车鲜叶。单号由 Hibernate 自定义生成器在 insert 时生成。
     */
    @Transactional
    public LeafIntake accept(IntakeRequest request) {
        if (!Dict.isValidLeafGrade(request.getLeafGrade())) {
            throw new BizException("鲜叶等级不合法，可选：" + String.join("、", Dict.LEAF_GRADES));
        }
        BigDecimal gross = nvl(request.getGrossWeightKg());
        if (gross.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("鲜叶毛重必须大于 0");
        }
        BigDecimal moisture = nvl(request.getMoisturePct());
        BigDecimal impurity = nvl(request.getImpurityPct());
        if (moisture.compareTo(BigDecimal.ZERO) < 0 || moisture.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BizException("含水量必须在 0~100% 之间");
        }
        if (impurity.compareTo(BigDecimal.ZERO) < 0 || impurity.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BizException("杂质率必须在 0~100% 之间");
        }

        // ---- 扣重规则 ----
        BigDecimal deductRate = BigDecimal.ZERO;
        List<String> reasons = new ArrayList<>();
        if (moisture.compareTo(Dict.MAX_MOISTURE) > 0) {
            BigDecimal over = moisture.subtract(Dict.MAX_MOISTURE);
            deductRate = deductRate.add(over.multiply(Dict.MOISTURE_DEDUCT_PER_PCT));
            reasons.add("含水量 " + strip(moisture) + "% 超上限 " + strip(Dict.MAX_MOISTURE) + "%");
        }
        if (impurity.compareTo(Dict.MAX_IMPURITY) > 0) {
            BigDecimal over = impurity.subtract(Dict.MAX_IMPURITY);
            deductRate = deductRate.add(over.multiply(Dict.IMPURITY_DEDUCT_PER_PCT));
            reasons.add("杂质率 " + strip(impurity) + "% 超上限 " + strip(Dict.MAX_IMPURITY) + "%");
        }
        if (deductRate.compareTo(Dict.MAX_DEDUCT_RATE) > 0) {
            throw new BizException("扣重比例 " + strip(deductRate) + "% 已超过上限 " + strip(Dict.MAX_DEDUCT_RATE)
                    + "%，该车鲜叶应整批退回，不允许扣重入库");
        }

        LeafIntake intake = new LeafIntake();
        intake.setSupplierName(request.getSupplierName());
        intake.setPlotCode(request.getPlotCode());
        intake.setPickDate(request.getPickDate() == null ? LocalDate.now() : request.getPickDate());
        intake.setLeafGrade(request.getLeafGrade());
        intake.setGrossWeightKg(gross);
        intake.setMoisturePct(moisture);
        intake.setImpurityPct(impurity);
        intake.setInspector(request.getInspector() == null ? "陈质检" : request.getInspector());

        BigDecimal deduct = gross.multiply(deductRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(deduct);
        BigDecimal unitPrice = Dict.leafGradePrice(request.getLeafGrade());

        intake.setDeductRatePct(deductRate.setScale(2, RoundingMode.HALF_UP));
        intake.setDeductWeightKg(deduct);
        intake.setNetWeightKg(net);
        intake.setUnitPrice(unitPrice);
        intake.setSettlePrice(net.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
        intake.setDeductReason(reasons.isEmpty() ? "验收合格，未扣重" : String.join("；", reasons));
        intake.setStatus(deduct.signum() > 0 ? "DEDUCTED" : "ACCEPTED");
        intake.setCreatedAt(LocalDateTime.now());

        return intakeRepository.save(intake);
    }

    /** 当日进厂流水 */
    public List<LeafIntake> todayList() {
        return intakeRepository.findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime.now().toLocalDate().atStartOfDay());
    }

    public List<LeafIntake> recentList() {
        return intakeRepository.findTop20ByOrderByCreatedAtDesc();
    }

    /** 按供应商汇总验收质量 */
    public List<SupplierQualityDTO> supplierQuality() {
        return intakeRepository.summarizeBySupplier();
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
