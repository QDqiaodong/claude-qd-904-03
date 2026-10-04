package com.tea.factory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tea.factory.common.ApiResponse;
import com.tea.factory.domain.WitheringTrough;
import com.tea.factory.dto.WitheringRequest;
import com.tea.factory.service.WitheringService;

/**
 * 摊青与萎凋。
 */
@RestController
@RequestMapping("/api/withering")
public class WitheringController {

    private final WitheringService witheringService;

    public WitheringController(WitheringService witheringService) {
        this.witheringService = witheringService;
    }

    @GetMapping
    public ApiResponse<List<WitheringTrough>> list() {
        return ApiResponse.success(witheringService.list());
    }

    /** 历史完成（已下槽）批次履历；?troughNo=TQ-01 可只查某槽号的各轮履历 */
    @GetMapping("/history")
    public ApiResponse<List<WitheringTrough>> history(@RequestParam(required = false) String troughNo) {
        return ApiResponse.success(witheringService.history(troughNo));
    }

    @GetMapping("/ready")
    public ApiResponse<List<WitheringTrough>> ready() {
        return ApiResponse.success(witheringService.readyList());
    }

    /** 开槽登记 */
    @PostMapping
    public ApiResponse<WitheringTrough> open(@RequestBody WitheringRequest request) {
        return ApiResponse.success("已开槽 " + request.getTroughNo(), witheringService.open(request));
    }

    /** 登记环境温湿度与失水率 */
    @PutMapping("/{troughNo}/log")
    public ApiResponse<WitheringTrough> log(@PathVariable String troughNo, @RequestBody WitheringRequest request) {
        WitheringTrough trough = witheringService.log(troughNo, request);
        return ApiResponse.success("槽号 " + troughNo + " 失水率 " + trough.getCurrentLossPct() + "%", trough);
    }

    /** 下槽 */
    @PostMapping("/{troughNo}/release")
    public ApiResponse<WitheringTrough> release(@PathVariable String troughNo) {
        return ApiResponse.success("槽号 " + troughNo + " 已下槽", witheringService.release(troughNo));
    }
}
