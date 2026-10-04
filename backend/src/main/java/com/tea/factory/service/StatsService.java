package com.tea.factory.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.tea.factory.dto.DashboardDTO;
import com.tea.factory.dto.EvalRankDTO;
import com.tea.factory.dto.SupplierQualityDTO;
import com.tea.factory.dto.TeaTypeYieldDTO;
import com.tea.factory.repository.DryingRoomRepository;
import com.tea.factory.repository.EquipmentRepository;
import com.tea.factory.repository.FinishedProductRepository;
import com.tea.factory.repository.LeafIntakeRepository;
import com.tea.factory.repository.TeaEvaluationRepository;
import com.tea.factory.repository.TeaProcessOrderRepository;
import com.tea.factory.repository.WitheringTroughRepository;

/**
 * 工序盘概览统计：进厂、摊青、在制、设备、成品与三项 JPQL 汇总。
 * 汇总结果在 Redis 里缓存 30 秒（Redis 不可用时自动降级为直查）。
 */
@Service
public class StatsService {

    private static final String CACHE_KEY = "tea-factory:dashboard:version";

    private final LeafIntakeRepository intakeRepository;
    private final WitheringTroughRepository troughRepository;
    private final TeaProcessOrderRepository orderRepository;
    private final EquipmentRepository equipmentRepository;
    private final DryingRoomRepository dryingRoomRepository;
    private final FinishedProductRepository productRepository;
    private final TeaEvaluationRepository evaluationRepository;
    private final QtyStatsService qtyStatsService;
    private final StringRedisTemplate redisTemplate;

    public StatsService(LeafIntakeRepository intakeRepository,
            WitheringTroughRepository troughRepository,
            TeaProcessOrderRepository orderRepository,
            EquipmentRepository equipmentRepository,
            DryingRoomRepository dryingRoomRepository,
            FinishedProductRepository productRepository,
            TeaEvaluationRepository evaluationRepository,
            QtyStatsService qtyStatsService,
            StringRedisTemplate redisTemplate) {
        this.intakeRepository = intakeRepository;
        this.troughRepository = troughRepository;
        this.orderRepository = orderRepository;
        this.equipmentRepository = equipmentRepository;
        this.dryingRoomRepository = dryingRoomRepository;
        this.productRepository = productRepository;
        this.evaluationRepository = evaluationRepository;
        this.qtyStatsService = qtyStatsService;
        this.redisTemplate = redisTemplate;
    }

    public DashboardDTO dashboard() {
        touchCache();
        DashboardDTO dto = new DashboardDTO();
        dto.setIntakeCount(intakeRepository.count());
        dto.setIntakeNetWeightKg(sum(intakeRepository.findAll().stream()
                .map(item -> item.getNetWeightKg() == null ? BigDecimal.ZERO : item.getNetWeightKg())
                .toList()));
        // 只统计当前在槽占用：已下槽历史记录不计入槽数与达标槽数
        dto.setWitheringCount(troughRepository.countByReleasedAtIsNull());
        dto.setReadyTroughCount(troughRepository.findByStatusAndReleasedAtIsNullOrderByTroughNoAsc("READY").size());
        dto.setProcessingOrderCount(orderRepository.findByStatusOrderByCreatedAtDesc("PROCESSING").size());
        dto.setDoneOrderCount(orderRepository.findByStatusOrderByCreatedAtDesc("DONE").size());
        dto.setRunningEquipmentCount(equipmentRepository.findByStatusOrderByEquipCodeAsc("RUNNING").size());
        dto.setFaultEquipmentCount(equipmentRepository.findByStatusOrderByEquipCodeAsc("FAULT").size());
        dto.setProductCount(productRepository.count());
        dto.setProductWeightKg(sum(productRepository.findAll().stream()
                .map(item -> item.getNetWeightKg() == null ? BigDecimal.ZERO : item.getNetWeightKg())
                .toList()));
        dto.setSupplierQuality(supplierQuality());
        dto.setTeaTypeYield(teaTypeYield());
        dto.setEvalRanking(evaluationRepository.ranking());
        return dto;
    }

    public List<SupplierQualityDTO> supplierQuality() {
        return intakeRepository.summarizeBySupplier();
    }

    public List<TeaTypeYieldDTO> teaTypeYield() {
        return qtyStatsService.aggregateByTeaType();
    }

    public List<EvalRankDTO> evalRanking() {
        return evaluationRepository.ranking();
    }

    /** 在 Redis 里打一个概览版本戳，演示缓存链路；Redis 不可用则静默跳过 */
    private void touchCache() {
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, String.valueOf(System.currentTimeMillis()), 30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Redis 不可用时不影响主流程
        }
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
