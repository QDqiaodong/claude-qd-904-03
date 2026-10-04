package com.tea.factory.common;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 制茶词典与工艺参数表：茶类、工序序列、鲜叶等级、审评维度、成品等级、常见缺陷、
 * 设备类型与状态、各工序的工艺阈值等。
 * 规则集中在此，便于调整或扩展。
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 茶类 ----------------
    public static final String GREEN = "GREEN";
    public static final String RED = "RED";
    public static final String OOLONG = "OOLONG";
    public static final String WHITE = "WHITE";
    public static final String PUER = "PUER";

    public static final List<String> TEA_TYPES = List.of(GREEN, RED, OOLONG, WHITE, PUER);

    private static final Map<String, String> TEA_TYPE_NAME = Map.of(
            GREEN, "绿茶",
            RED, "红茶",
            OOLONG, "乌龙茶",
            WHITE, "白茶",
            PUER, "普洱茶");

    private static final Map<String, String> TEA_TYPE_CODE = Map.of(
            GREEN, "GREEN",
            RED, "BLACK",
            OOLONG, "OOLONG",
            WHITE, "WHITE",
            PUER, "PUER");

    // ---------------- 工序 ----------------
    public static final String STEP_SPREAD = "SPREAD";     // 摊青 / 萎凋 / 晒青
    public static final String STEP_MAKING = "MAKING";     // 做青（摇青）
    public static final String STEP_FIX = "FIX";           // 杀青
    public static final String STEP_ROLL = "ROLL";         // 揉捻
    public static final String STEP_FERMENT = "FERMENT";   // 发酵 / 渥堆
    public static final String STEP_DRY = "DRY";           // 干燥
    public static final String STEP_PICK = "PICK";         // 拣剔拼配

    private static final Map<String, String> STEP_NAME = Map.of(
            STEP_SPREAD, "摊青",
            STEP_MAKING, "做青",
            STEP_FIX, "杀青",
            STEP_ROLL, "揉捻",
            STEP_FERMENT, "发酵",
            STEP_DRY, "干燥",
            STEP_PICK, "拣剔");

    /** 红茶的发酵、普洱的渥堆，同一工序键在不同茶类下的叫法 */
    private static final Map<String, Map<String, String>> STEP_ALIAS = Map.of(
            RED, Map.of(STEP_FERMENT, "发酵", STEP_SPREAD, "萎凋"),
            PUER, Map.of(STEP_FERMENT, "渥堆", STEP_SPREAD, "摊青"),
            OOLONG, Map.of(STEP_SPREAD, "晒青"),
            WHITE, Map.of(STEP_SPREAD, "摊青（自然萎凋）"));

    /** 各茶类的工序序列（必须按顺序执行，不能跳步） */
    private static final Map<String, List<String>> TEA_FLOW = Map.of(
            GREEN, List.of(STEP_SPREAD, STEP_FIX, STEP_ROLL, STEP_DRY, STEP_PICK),
            RED, List.of(STEP_SPREAD, STEP_ROLL, STEP_FERMENT, STEP_DRY, STEP_PICK),
            OOLONG, List.of(STEP_SPREAD, STEP_MAKING, STEP_FIX, STEP_ROLL, STEP_DRY, STEP_PICK),
            WHITE, List.of(STEP_SPREAD, STEP_DRY, STEP_PICK),
            PUER, List.of(STEP_SPREAD, STEP_FIX, STEP_ROLL, STEP_FERMENT, STEP_DRY, STEP_PICK));

    /** 各茶类杀青温度下限（℃），低于该值不允许结束杀青进入下一工序 */
    private static final Map<String, BigDecimal> MIN_FIX_TEMP = Map.of(
            GREEN, BigDecimal.valueOf(220),
            OOLONG, BigDecimal.valueOf(200),
            PUER, BigDecimal.valueOf(180));

    /** 各茶类发酵 / 渥堆最短时长（分钟），不足不允许进入干燥 */
    private static final Map<String, Integer> MIN_FERMENT_MINUTES = Map.of(
            RED, 180,
            PUER, 720,
            OOLONG, 120);

    /** 各茶类摊青目标失水率（%） */
    private static final Map<String, BigDecimal> TARGET_LOSS_PCT = Map.of(
            GREEN, BigDecimal.valueOf(32),
            RED, BigDecimal.valueOf(38),
            OOLONG, BigDecimal.valueOf(26),
            WHITE, BigDecimal.valueOf(45),
            PUER, BigDecimal.valueOf(12));

    /** 各茶类成茶率（投料 -> 干茶；旧批次估算口径，新批次以逐道实称为准） */
    private static final Map<String, BigDecimal> YIELD_RATE = Map.of(
            GREEN, BigDecimal.valueOf(0.22),
            RED, BigDecimal.valueOf(0.24),
            OOLONG, BigDecimal.valueOf(0.20),
            WHITE, BigDecimal.valueOf(0.26),
            PUER, BigDecimal.valueOf(0.25));

    // ---------------- 工序在制量交接：各茶类 × 各工序正常损耗范围（%） ----------------
    // 口径：损耗率 =（本步投入 − 本步产出）/ 本步投入。
    // 失水是主要构成，故摊青 / 干燥区间最宽；做青、杀青等机械工序区间较窄。
    // 范围只用于“是否要复核”的提示线，不替代工艺参数红线；超限不是判废，而是要求复核人说明。
    public record LossRange(BigDecimal minPct, BigDecimal maxPct) {
    }

    private static LossRange r(double min, double max) {
        return new LossRange(BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }

    /** 工序默认损耗范围（未按茶类单列时使用） */
    private static final Map<String, LossRange> DEFAULT_STEP_LOSS = Map.of(
            STEP_SPREAD, r(5, 48),
            STEP_MAKING, r(1, 10),
            STEP_FIX, r(2, 15),
            STEP_ROLL, r(1, 8),
            STEP_FERMENT, r(1, 12),
            STEP_DRY, r(30, 62),
            STEP_PICK, r(0, 6));

    /** 各茶类工序的差异化范围；未列出的工序回落到默认范围 */
    private static final Map<String, Map<String, LossRange>> TEA_STEP_LOSS = Map.of(
            GREEN, Map.of(
                    STEP_SPREAD, r(25, 40),
                    STEP_FIX, r(3, 12),
                    STEP_ROLL, r(1, 6),
                    STEP_DRY, r(40, 60),
                    STEP_PICK, r(0, 4)),
            RED, Map.of(
                    STEP_SPREAD, r(30, 45),
                    STEP_ROLL, r(2, 8),
                    STEP_FERMENT, r(2, 10),
                    STEP_DRY, r(42, 62),
                    STEP_PICK, r(0, 5)),
            OOLONG, Map.of(
                    STEP_SPREAD, r(8, 20),
                    STEP_MAKING, r(3, 10),
                    STEP_FIX, r(3, 12),
                    STEP_ROLL, r(1, 7),
                    STEP_DRY, r(38, 58),
                    STEP_PICK, r(1, 6)),
            WHITE, Map.of(
                    STEP_SPREAD, r(35, 48),
                    STEP_DRY, r(30, 50),
                    STEP_PICK, r(0, 4)),
            PUER, Map.of(
                    STEP_SPREAD, r(5, 15),
                    STEP_FIX, r(2, 10),
                    STEP_ROLL, r(2, 8),
                    STEP_FERMENT, r(1, 8),
                    STEP_DRY, r(35, 55),
                    STEP_PICK, r(1, 5)));

    // ---------------- 在制量交接状态 ----------------
    public static final String QH_CONFIRMED = "CONFIRMED";            // 已确认（范围内自动确认 / 复核接受）
    public static final String QH_PENDING_REVIEW = "PENDING_REVIEW";  // 超损耗范围，待复核
    public static final String QH_RETURNED = "RETURNED";              // 复核退回，班组更正后重交
    public static final String QH_LEGACY = "LEGACY";                  // 旧工序记录派生，不参与复核

    private static final Map<String, String> QTY_HANDOVER_STATUS = Map.of(
            QH_CONFIRMED, "已确认",
            QH_PENDING_REVIEW, "待复核",
            QH_RETURNED, "已退回更正",
            QH_LEGACY, "旧记录派生");

    /** 数量口径 */
    private static final Map<String, String> QTY_MODE = Map.of(
            "TRACKED", "逐道实称",
            "LEGACY", "旧批派生");

    /** 常见损耗原因（班组结束工序时必选其一或自填） */
    public static final List<String> LOSS_REASONS = List.of(
            "正常失水", "茶末 / 碎茶损耗", "老叶 / 黄片拣出", "焦边爆点",
            "渥堆 / 发酵损耗", "撒漏（操作损耗）", "水分超标复烘", "杂质 / 非茶夹杂物", "设备残留");

    /** 损耗率核对允许误差（百分比点，四舍五入与过磅误差内不提示） */
    public static final BigDecimal LOSS_TOLERANCE_PCT = BigDecimal.valueOf(0.05);

    // ---------------- 鲜叶等级 ----------------
    public static final String BUD = "单芽";
    public static final String ONE_BUD_ONE_LEAF = "一芽一叶";
    public static final String ONE_BUD_TWO_LEAF = "一芽二叶";
    public static final String OPPOSITE_LEAF = "对夹叶";

    public static final List<String> LEAF_GRADES = List.of(BUD, ONE_BUD_ONE_LEAF, ONE_BUD_TWO_LEAF, OPPOSITE_LEAF);

    /** 各鲜叶等级收购单价（元/kg） */
    private static final Map<String, BigDecimal> LEAF_GRADE_PRICE = Map.of(
            BUD, BigDecimal.valueOf(118),
            ONE_BUD_ONE_LEAF, BigDecimal.valueOf(86),
            ONE_BUD_TWO_LEAF, BigDecimal.valueOf(52),
            OPPOSITE_LEAF, BigDecimal.valueOf(24));

    // ---------------- 摊青方式 ----------------
    public static final String SUN_WITHHER = "日光晒青";
    public static final String INDOOR_WITHHER = "室内萎凋";
    public static final List<String> WITHER_METHODS = List.of(SUN_WITHHER, INDOOR_WITHHER);

    // ---------------- 审评维度（五项，权重合计 100） ----------------
    public static final List<String> EVAL_DIMS = List.of("外形", "汤色", "香气", "滋味", "叶底");

    private static final Map<String, BigDecimal> EVAL_WEIGHT = Map.of(
            "外形", BigDecimal.valueOf(0.25),
            "汤色", BigDecimal.valueOf(0.10),
            "香气", BigDecimal.valueOf(0.25),
            "滋味", BigDecimal.valueOf(0.30),
            "叶底", BigDecimal.valueOf(0.10));

    // ---------------- 成品等级 ----------------
    public static final String SPECIAL = "特级";
    public static final String FIRST = "一级";
    public static final String SECOND = "二级";
    public static final String THIRD = "三级";

    public static final List<String> FINISH_GRADES = List.of(SPECIAL, FIRST, SECOND, THIRD);

    // ---------------- 常见缺陷 ----------------
    public static final List<String> DEFECTS = List.of(
            "红变叶", "焦边爆点", "断碎", "水闷味", "霉变", "老叶夹杂", "虫咬叶", "异味吸附");

    // ---------------- 设备 ----------------
    public static final String EQ_FIX = "杀青机";
    public static final String EQ_ROLL = "揉捻机";
    public static final String EQ_FERMENT = "发酵室";
    public static final String EQ_DRY = "烘干机";
    public static final String EQ_AROMA = "提香机";

    public static final List<String> EQUIPMENT_TYPES = List.of(EQ_FIX, EQ_ROLL, EQ_FERMENT, EQ_DRY, EQ_AROMA);

    /** 设备状态（PENDING_CONFIRM：清洗完成待负责人确认，期间同样不能开工） */
    private static final Map<String, String> EQUIPMENT_STATUS = Map.of(
            "RUNNING", "运行",
            "IDLE", "待机",
            "CLEANING", "清洗中",
            "PENDING_CONFIRM", "待确认",
            "FAULT", "故障");

    public static final List<String> EQUIPMENT_STATUS_KEYS =
            List.of("RUNNING", "IDLE", "CLEANING", "PENDING_CONFIRM", "FAULT");

    // ---------------- 清洗交接 ----------------
    /** 交接单状态 */
    public static final String HO_PENDING = "PENDING";              // 清洗未开始
    public static final String HO_IN_PROGRESS = "IN_PROGRESS";      // 清洗进行中
    public static final String HO_AWAIT_CONFIRM = "AWAIT_CONFIRM";  // 清洗完成，等待负责人确认
    public static final String HO_FAULT_FREEZE = "FAULT_FREEZE";    // 清洗期间报障：立即冻结，修复后重新清洗
    public static final String HO_KEEP_AND_REPAIR = "KEEP_AND_REPAIR"; // 确认后报障：保留已确认结果并转维修
    public static final String HO_CONFIRMED = "CONFIRMED";          // 负责人确认，设备恢复可用

    private static final Map<String, String> HANDOVER_STATUS = Map.of(
            HO_PENDING, "清洗未开始",
            HO_IN_PROGRESS, "清洗进行中",
            HO_AWAIT_CONFIRM, "等待确认",
            HO_FAULT_FREEZE, "故障冻结",
            HO_KEEP_AND_REPAIR, "保留结果转维修",
            HO_CONFIRMED, "已确认恢复");

    /** 未闭环状态：设备一律不能用于下一批次开工 */
    public static final List<String> HANDOVER_ACTIVE_STATUS =
            List.of(HO_PENDING, HO_IN_PROGRESS, HO_AWAIT_CONFIRM, HO_FAULT_FREEZE, HO_KEEP_AND_REPAIR);

    /** 残留风险 */
    public static final String RISK_LOW = "低";
    public static final String RISK_MIDDLE = "中";
    public static final String RISK_HIGH = "高";
    public static final List<String> RESIDUAL_RISKS = List.of(RISK_LOW, RISK_MIDDLE, RISK_HIGH);

    /** 清洗方式 */
    public static final String CLEAN_WATER = "清水冲洗";
    public static final String CLEAN_DETERGENT = "食品级清洁剂";
    public static final String CLEAN_STEAM = "高温蒸汽";
    public static final String CLEAN_DISASSEMBLE = "人工拆卸清扫";
    public static final String CLEAN_SPECIAL = "专项清洗";
    public static final List<String> CLEAN_METHODS =
            List.of(CLEAN_WATER, CLEAN_DETERGENT, CLEAN_STEAM, CLEAN_DISASSEMBLE, CLEAN_SPECIAL);

    // ---------------- 状态 ----------------
    private static final Map<String, String> ORDER_STATUS = Map.of(
            "PROCESSING", "在制",
            "DONE", "已完成");

    private static final Map<String, String> TROUGH_STATUS = Map.of(
            "WITHERING", "摊青中",
            "READY", "可杀青",
            "DONE", "已下槽");

    private static final Map<String, String> STEP_STATUS = Map.of(
            "RUNNING", "执行中",
            "DONE", "已完成");

    private static final Map<String, String> INTAKE_STATUS = Map.of(
            "ACCEPTED", "合格入厂",
            "DEDUCTED", "扣重验收",
            "REJECTED", "退回");

    // ---------------- 业务阈值 ----------------
    /** 鲜叶含水量上限（%），超过要扣重 */
    public static final BigDecimal MAX_MOISTURE = BigDecimal.valueOf(78);
    /** 鲜叶杂质率上限（%），超过要扣重 */
    public static final BigDecimal MAX_IMPURITY = BigDecimal.valueOf(5);
    /** 含水量每超 1% 的扣重比例（%） */
    public static final BigDecimal MOISTURE_DEDUCT_PER_PCT = BigDecimal.valueOf(1.5);
    /** 杂质率每超 1% 的扣重比例（%） */
    public static final BigDecimal IMPURITY_DEDUCT_PER_PCT = BigDecimal.valueOf(2.0);
    /** 单次扣重比例上限（%），超过则建议退回 */
    public static final BigDecimal MAX_DEDUCT_RATE = BigDecimal.valueOf(30);
    /** 成品出厂水分上限（%），超过不允许入库 */
    public static final BigDecimal MAX_PRODUCT_MOISTURE = BigDecimal.valueOf(7);

    // ---------------- 取值方法 ----------------

    public static boolean isValidTeaType(String teaType) {
        return teaType != null && TEA_TYPE_NAME.containsKey(teaType);
    }

    public static String teaTypeName(String teaType) {
        return teaType == null ? "" : TEA_TYPE_NAME.getOrDefault(teaType, teaType);
    }

    /** 工单批次号中间段的茶类代码 */
    public static String teaTypeCode(String teaType) {
        return teaType == null ? "GREEN" : TEA_TYPE_CODE.getOrDefault(teaType, "GREEN");
    }

    public static boolean isValidStep(String step) {
        return step != null && STEP_NAME.containsKey(step);
    }

    /** 工序显示名（红茶叫发酵、普洱叫渥堆） */
    public static String stepName(String teaType, String step) {
        if (step == null) {
            return "";
        }
        Map<String, String> alias = teaType == null ? Map.of() : STEP_ALIAS.getOrDefault(teaType, Map.of());
        return alias.getOrDefault(step, STEP_NAME.getOrDefault(step, step));
    }

    public static String stepName(String step) {
        return stepName(null, step);
    }

    /** 茶类的工序序列 */
    public static List<String> flow(String teaType) {
        return TEA_FLOW.getOrDefault(teaType, TEA_FLOW.get(GREEN));
    }

    public static int stepIndex(String teaType, String step) {
        return flow(teaType).indexOf(step);
    }

    public static BigDecimal minFixTemp(String teaType) {
        return MIN_FIX_TEMP.getOrDefault(teaType, BigDecimal.valueOf(200));
    }

    public static int minFermentMinutes(String teaType) {
        return MIN_FERMENT_MINUTES.getOrDefault(teaType, 120);
    }

    public static BigDecimal targetLossPct(String teaType) {
        return TARGET_LOSS_PCT.getOrDefault(teaType, BigDecimal.valueOf(30));
    }

    public static BigDecimal yieldRate(String teaType) {
        return YIELD_RATE.getOrDefault(teaType, BigDecimal.valueOf(0.22));
    }

    /** 某茶类某工序的正常损耗范围（%）：茶类差异表未列时回落工序默认范围 */
    public static LossRange lossRange(String teaType, String step) {
        Map<String, LossRange> byStep = TEA_STEP_LOSS.get(teaType);
        LossRange range = byStep == null ? null : byStep.get(step);
        return range != null ? range : DEFAULT_STEP_LOSS.getOrDefault(step, r(0, 30));
    }

    public static String qtyHandoverStatusName(String status) {
        return status == null ? "" : QTY_HANDOVER_STATUS.getOrDefault(status, status);
    }

    public static String qtyModeName(String mode) {
        return mode == null ? "" : QTY_MODE.getOrDefault(mode, mode);
    }

    public static boolean isValidLossReason(String reason) {
        return reason != null && !reason.isBlank();
    }

    public static boolean isValidLeafGrade(String grade) {
        return grade != null && LEAF_GRADE_PRICE.containsKey(grade);
    }

    public static BigDecimal leafGradePrice(String grade) {
        return LEAF_GRADE_PRICE.getOrDefault(grade, BigDecimal.valueOf(24));
    }

    public static boolean isValidWitherMethod(String method) {
        return method != null && WITHER_METHODS.contains(method);
    }

    /** 审评维度权重（合计 1.0） */
    public static BigDecimal evalWeight(String dim) {
        return EVAL_WEIGHT.getOrDefault(dim, BigDecimal.ZERO);
    }

    /** 按总分判定成品等级；低于 60 分不入等 */
    public static String gradeOf(BigDecimal total) {
        if (total == null) {
            return "不入等";
        }
        double v = total.doubleValue();
        if (v >= 90) {
            return SPECIAL;
        }
        if (v >= 80) {
            return FIRST;
        }
        if (v >= 70) {
            return SECOND;
        }
        if (v >= 60) {
            return THIRD;
        }
        return "不入等";
    }

    public static boolean isValidFinishGrade(String grade) {
        return grade != null && FINISH_GRADES.contains(grade);
    }

    public static boolean isValidEquipmentType(String type) {
        return type != null && EQUIPMENT_TYPES.contains(type);
    }

    public static boolean isValidEquipmentStatus(String status) {
        return status != null && EQUIPMENT_STATUS.containsKey(status);
    }

    public static String equipmentStatusName(String status) {
        return status == null ? "" : EQUIPMENT_STATUS.getOrDefault(status, status);
    }

    public static String handoverStatusName(String status) {
        return status == null ? "" : HANDOVER_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidHandoverStatus(String status) {
        return status != null && HANDOVER_STATUS.containsKey(status);
    }

    public static boolean isHandoverActive(String status) {
        return status != null && HANDOVER_ACTIVE_STATUS.contains(status);
    }

    public static boolean isValidResidualRisk(String risk) {
        return risk != null && RESIDUAL_RISKS.contains(risk);
    }

    public static boolean isValidCleanMethod(String method) {
        return method != null && CLEAN_METHODS.contains(method);
    }

    public static String orderStatusName(String status) {
        return status == null ? "" : ORDER_STATUS.getOrDefault(status, status);
    }

    public static String troughStatusName(String status) {
        return status == null ? "" : TROUGH_STATUS.getOrDefault(status, status);
    }

    public static String stepStatusName(String status) {
        return status == null ? "" : STEP_STATUS.getOrDefault(status, status);
    }

    public static String intakeStatusName(String status) {
        return status == null ? "" : INTAKE_STATUS.getOrDefault(status, status);
    }

    /** 给页面用的完整词典（茶类、工序、等级、缺陷等） */
    public static Map<String, Object> vocab() {
        Map<String, Object> vocab = new LinkedHashMap<>();
        vocab.put("teaTypes", TEA_TYPES);
        vocab.put("teaTypeName", TEA_TYPE_NAME);
        vocab.put("leafGrades", LEAF_GRADES);
        vocab.put("leafGradePrice", LEAF_GRADE_PRICE);
        vocab.put("witherMethods", WITHER_METHODS);
        vocab.put("evalDims", EVAL_DIMS);
        vocab.put("finishGrades", FINISH_GRADES);
        vocab.put("defects", DEFECTS);
        vocab.put("equipmentTypes", EQUIPMENT_TYPES);
        vocab.put("equipmentStatus", EQUIPMENT_STATUS);
        vocab.put("handoverStatus", HANDOVER_STATUS);
        vocab.put("residualRisks", RESIDUAL_RISKS);
        vocab.put("cleanMethods", CLEAN_METHODS);
        vocab.put("stepName", STEP_NAME);
        vocab.put("teaFlow", TEA_FLOW);
        vocab.put("minFixTemp", MIN_FIX_TEMP);
        vocab.put("minFermentMinutes", MIN_FERMENT_MINUTES);
        vocab.put("targetLossPct", TARGET_LOSS_PCT);
        vocab.put("lossReasons", LOSS_REASONS);
        vocab.put("qtyHandoverStatus", QTY_HANDOVER_STATUS);
        vocab.put("qtyMode", QTY_MODE);
        // 各茶类 × 各工序正常损耗范围（%），页面结束工序时实时提示并与后端判定同源
        Map<String, Map<String, Map<String, BigDecimal>>> lossRanges = new LinkedHashMap<>();
        for (String teaType : TEA_TYPES) {
            Map<String, Map<String, BigDecimal>> byStep = new LinkedHashMap<>();
            for (String step : flow(teaType)) {
                LossRange range = lossRange(teaType, step);
                Map<String, BigDecimal> bound = new LinkedHashMap<>();
                bound.put("minPct", range.minPct());
                bound.put("maxPct", range.maxPct());
                byStep.put(step, bound);
            }
            lossRanges.put(teaType, byStep);
        }
        vocab.put("lossRanges", lossRanges);
        vocab.put("thresholds", Map.of(
                "maxMoisture", MAX_MOISTURE,
                "maxImpurity", MAX_IMPURITY,
                "maxDeductRate", MAX_DEDUCT_RATE,
                "maxProductMoisture", MAX_PRODUCT_MOISTURE));
        return vocab;
    }
}
