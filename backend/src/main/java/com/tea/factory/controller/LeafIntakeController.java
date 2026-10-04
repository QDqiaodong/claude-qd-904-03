package com.tea.factory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tea.factory.common.ApiResponse;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.LeafIntake;
import com.tea.factory.dto.IntakeRequest;
import com.tea.factory.dto.SupplierQualityDTO;
import com.tea.factory.service.LeafIntakeService;

import jakarta.validation.Valid;

/**
 * 鲜叶进厂与验收。
 */
@RestController
@RequestMapping("/api/intake")
public class LeafIntakeController {

    private final LeafIntakeService intakeService;

    public LeafIntakeController(LeafIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    /** 验收录入，单号由后端自动生成 */
    @PostMapping
    public ApiResponse<LeafIntake> accept(@Valid @RequestBody IntakeRequest request) {
        LeafIntake saved = intakeService.accept(request);
        return ApiResponse.success("验收完成，进厂单号 " + saved.getIntakeNo(), saved);
    }

    @GetMapping("/today")
    public ApiResponse<List<LeafIntake>> today() {
        return ApiResponse.success(intakeService.todayList());
    }

    @GetMapping
    public ApiResponse<List<LeafIntake>> list() {
        return ApiResponse.success(intakeService.recentList());
    }

    /** 按供应商汇总验收质量 */
    @GetMapping("/supplier-quality")
    public ApiResponse<List<SupplierQualityDTO>> supplierQuality() {
        return ApiResponse.success(intakeService.supplierQuality());
    }

    @GetMapping("/vocab")
    public ApiResponse<Object> vocab() {
        return ApiResponse.success(Dict.vocab());
    }
}
