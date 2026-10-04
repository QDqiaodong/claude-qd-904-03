package com.tea.factory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.Dict;
import com.tea.factory.domain.TeaProcessOrder;
import com.tea.factory.dto.StepQtyNodeDTO;
import com.tea.factory.dto.TeaTypeYieldDTO;
import com.tea.factory.repository.TeaProcessOrderRepository;

/**
 * 产出率统计：直接从“批次原投料基准 + 在制量交接时间线”聚合，
 * 与工单详情里的数量时间线是同一份数据，页面与统计必然对得上账。
 * 不使用成茶率估算产出；未完工批次的产出计 0、在制量计入在制。
 */
@Service
public class QtyStatsService {

    private static final BigDecimal KG_TOLERANCE = BigDecimal.valueOf(0.05);

    private final TeaProcessOrderRepository orderRepository;
    private final StepQtyHandoverService qtyHandoverService;

    public QtyStatsService(TeaProcessOrderRepository orderRepository,
            StepQtyHandoverService qtyHandoverService) {
        this.orderRepository = orderRepository;
        this.qtyHandoverService = qtyHandoverService;
    }

    @Transactional(readOnly = true)
    public List<TeaTypeYieldDTO> aggregateByTeaType() {
        List<TeaProcessOrder> orders = orderRepository.findAll();
        Map<String, List<TeaProcessOrder>> byType = new LinkedHashMap<>();
        for (String teaType : Dict.TEA_TYPES) {
            byType.put(teaType, new ArrayList<>());
        }
        for (TeaProcessOrder o : orders) {
            byType.computeIfAbsent(o.getTeaType(), k -> new ArrayList<>()).add(o);
        }

        List<TeaTypeYieldDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<TeaProcessOrder>> entry : byType.entrySet()) {
            result.add(aggregate(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private TeaTypeYieldDTO aggregate(String teaType, List<TeaProcessOrder> orders) {
        TeaTypeYieldDTO dto = new TeaTypeYieldDTO();
        dto.setTeaType(teaType);
        dto.setTeaTypeName(Dict.teaTypeName(teaType));
        dto.setBatchCount((long) orders.size());

        BigDecimal input = BigDecimal.ZERO;
        BigDecimal finalOutput = BigDecimal.ZERO;
        BigDecimal wip = BigDecimal.ZERO;
        long done = 0;
        long processing = 0;
        for (TeaProcessOrder o : orders) {
            input = input.add(nz(o.getInputQtyKg()));
            if ("DONE".equals(o.getStatus())) {
                done++;
                finalOutput = finalOutput.add(nz(o.getOutputQtyKg()));
            } else {
                processing++;
                wip = wip.add(nz(o.getAvailableWipKg()));
            }
        }

        // 累计损耗与未登记差：直接走每批的时间线/对账，保证与详情逐数一致
        BigDecimal loss = BigDecimal.ZERO;
        BigDecimal unrecorded = BigDecimal.ZERO;
        boolean anyPartial = false;
        for (TeaProcessOrder o : orders) {
            List<StepQtyNodeDTO> nodes = qtyHandoverService.timeline(o);
            loss = loss.add(nz(qtyHandoverService.balance(o, nodes).getAccumulatedLossKg()));
            BigDecimal gap = nz(qtyHandoverService.balance(o, nodes).getUnrecordedGapKg());
            if (gap.signum() > 0) {
                unrecorded = unrecorded.add(gap);
                anyPartial = true;
            }
        }

        dto.setDoneBatchCount(done);
        dto.setProcessingBatchCount(processing);
        dto.setInputQtyKg(input.setScale(2, RoundingMode.HALF_UP));
        dto.setFinalOutputQtyKg(finalOutput.setScale(2, RoundingMode.HALF_UP));
        dto.setAccumulatedLossQtyKg(loss.setScale(2, RoundingMode.HALF_UP));
        dto.setWorkInProcessQtyKg(wip.setScale(2, RoundingMode.HALF_UP));
        dto.setUnrecordedGapQtyKg(unrecorded.setScale(2, RoundingMode.HALF_UP));

        BigDecimal reconcileGap = input.subtract(finalOutput).subtract(loss).subtract(wip).subtract(unrecorded)
                .setScale(2, RoundingMode.HALF_UP);
        if (reconcileGap.abs().compareTo(KG_TOLERANCE) <= 0) {
            reconcileGap = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        dto.setReconcileGapQtyKg(reconcileGap);
        dto.setReconcileStatus(anyPartial || unrecorded.signum() > 0 ? "PARTIAL" : "BALANCED");
        return dto;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
