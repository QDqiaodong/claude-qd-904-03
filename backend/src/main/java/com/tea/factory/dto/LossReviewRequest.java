package com.tea.factory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 损耗复核请求：复核人对超范围交接单给出结论。
 * accept=true 接受异常并写明理由；accept=false 退回班组更正重量与损耗说明。
 */
@Data
public class LossReviewRequest {

    /** 复核人 */
    @NotBlank(message = "复核人不能为空")
    private String reviewer;

    /** true 接受异常（须填理由）/ false 退回更正（须填退回原因） */
    private Boolean accept;

    /** 复核说明：接受异常的理由，或退回更正的原因 */
    @NotBlank(message = "复核说明不能为空：接受请写明理由，退回请指明更正要求")
    private String reviewNote;
}
