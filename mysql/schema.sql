SET NAMES utf8mb4;

-- =====================================================================
-- 茶叶加工厂 · 鲜叶与制茶工序管理
-- 建表 + 种子数据（ddl-auto: none，表结构与数据全部由本脚本负责）
-- =====================================================================

-- ---------------- 1. 鲜叶进厂验收单 ----------------
CREATE TABLE IF NOT EXISTS leaf_intake (
    intake_no         VARCHAR(32)  NOT NULL COMMENT '进厂单号（自动生成 XY+日期+序号）',
    supplier_name     VARCHAR(64)  NOT NULL COMMENT '供应商 / 茶农',
    plot_code         VARCHAR(32)           COMMENT '采摘地块',
    pick_date         DATE                  COMMENT '采摘日期',
    leaf_grade        VARCHAR(16)           COMMENT '鲜叶等级',
    gross_weight_kg   DECIMAL(10,2)         COMMENT '毛重 kg',
    moisture_pct      DECIMAL(5,2)          COMMENT '含水量 %',
    impurity_pct      DECIMAL(5,2)          COMMENT '杂质率 %',
    inspector         VARCHAR(32)           COMMENT '验收员',
    deduct_weight_kg  DECIMAL(10,2)         COMMENT '扣重 kg',
    deduct_rate_pct   DECIMAL(5,2)          COMMENT '扣重比例 %',
    deduct_reason     VARCHAR(128)          COMMENT '扣重原因',
    net_weight_kg     DECIMAL(10,2)         COMMENT '扣重后重量 kg',
    unit_price        DECIMAL(8,2)          COMMENT '等级单价 元/kg',
    settle_price      DECIMAL(12,2)         COMMENT '结算价 元',
    status            VARCHAR(16)           COMMENT 'ACCEPTED / DEDUCTED / REJECTED',
    created_at        DATETIME              COMMENT '进厂时间',
    PRIMARY KEY (intake_no),
    KEY idx_intake_created (created_at),
    KEY idx_intake_supplier (supplier_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鲜叶进厂验收单';

-- ---------------- 2. 摊青槽 ----------------
-- 每轮摊青一条独立记录：开槽 INSERT、下槽 UPDATE 状态；物理槽号可反复承接新批次。
-- active_no 为存储生成列：在槽（WITHERING / READY）时等于 trough_no，下槽后置 NULL，
-- 配合唯一索引 uk_trough_active，从数据库层保证一个槽号至多只有一条在槽记录
-- （两人同时用同一槽号开槽，只有一人成功）；历史完成行不再占用槽号。
CREATE TABLE IF NOT EXISTS withering_trough (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    trough_no         VARCHAR(16)  NOT NULL COMMENT '槽号',
    intake_no         VARCHAR(32)           COMMENT '关联进厂单号',
    batch_no          VARCHAR(32)           COMMENT '关联工单批次号',
    tea_type          VARCHAR(16)           COMMENT '茶类',
    wither_method     VARCHAR(16)           COMMENT '日光晒青 / 室内萎凋',
    leaf_thickness_cm DECIMAL(5,2)          COMMENT '摊叶厚度 cm',
    wither_hours      DECIMAL(5,1)          COMMENT '摊青时长 h',
    temp_c            DECIMAL(5,1)          COMMENT '环境温度 ℃',
    humidity_pct      DECIMAL(5,1)          COMMENT '环境相对湿度 %',
    current_loss_pct  DECIMAL(5,2)          COMMENT '当前失水率 %',
    target_loss_pct   DECIMAL(5,2)          COMMENT '目标失水率 %',
    leaf_weight_kg    DECIMAL(10,2)         COMMENT '在槽鲜叶重量 kg',
    status            VARCHAR(16)  NOT NULL DEFAULT 'WITHERING' COMMENT 'WITHERING 摊青中 / READY 可杀青 / DONE 已下槽',
    active_no         VARCHAR(16) GENERATED ALWAYS AS (CASE WHEN status IN ('WITHERING','READY') THEN trough_no ELSE NULL END) STORED COMMENT '在槽占用标记',
    opened_at         DATETIME              COMMENT '开槽时间',
    released_at       DATETIME              COMMENT '下槽时间',
    updated_at        DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trough_active (active_no),
    KEY idx_trough_no (trough_no, opened_at),
    KEY idx_trough_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='摊青槽批次履历（每轮摊青一条）';

-- ---------------- 3. 制茶工单 ----------------
CREATE TABLE IF NOT EXISTS tea_process_order (
    batch_no           VARCHAR(32)  NOT NULL COMMENT '批次号（自动生成 PC-茶类+日期+序号）',
    tea_type           VARCHAR(16)  NOT NULL COMMENT '茶类',
    process_seq        VARCHAR(128)          COMMENT '工序序列',
    current_step       VARCHAR(16)           COMMENT '当前工序',
    current_step_index INT                   COMMENT '当前工序下标',
    input_qty_kg       DECIMAL(10,2)         COMMENT '投料量 kg（批次原投料基准，全程不变，不随工序产出改写）',
    output_qty_kg      DECIMAL(10,2)         COMMENT '最终产量 kg（仅末道交接确认后写入）',
    available_wip_kg   DECIMAL(10,2)         COMMENT '当前可用在制量 kg＝上道已确认交接产出；待复核 / 退回时保持旧值不前进',
    qty_mode           VARCHAR(16)  NOT NULL DEFAULT 'LEGACY' COMMENT '数量口径 LEGACY 旧批次派生 / TRACKED 每道实称交接',
    source_trough_no   VARCHAR(16)           COMMENT '来源摊青槽',
    operator           VARCHAR(32)           COMMENT '制茶师傅',
    status             VARCHAR(16)           COMMENT 'PROCESSING / DONE',
    created_at         DATETIME              COMMENT '开单时间',
    PRIMARY KEY (batch_no),
    KEY idx_order_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='制茶工单';

-- ---------------- 4. 工序执行记录 ----------------
CREATE TABLE IF NOT EXISTS process_step_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    batch_no        VARCHAR(32)  NOT NULL COMMENT '批次号',
    tea_type        VARCHAR(16)           COMMENT '茶类',
    step_name       VARCHAR(16)  NOT NULL COMMENT '工序键',
    step_index      INT                   COMMENT '工序下标',
    equipment_code  VARCHAR(32)           COMMENT '设备编号',
    start_time      DATETIME              COMMENT '开始时间',
    end_time        DATETIME              COMMENT '结束时间',
    param_temp_c    DECIMAL(6,1)          COMMENT '温度 ℃',
    param_temp2_c   DECIMAL(6,1)          COMMENT '第二段温度 ℃',
    param_minutes   INT                   COMMENT '时长 min',
    param_minutes2  INT                   COMMENT '第二段时长 min',
    param_rpm       INT                   COMMENT '转速 r/min',
    param_pressure  DECIMAL(5,1)          COMMENT '压力 kg',
    param_leaf_qty  DECIMAL(10,2)         COMMENT '投叶量 kg',
    humidity_pct    DECIMAL(5,1)          COMMENT '相对湿度 %',
    param_profile   VARCHAR(128)          COMMENT '参数速记',
    operator        VARCHAR(32)           COMMENT '操作人',
    abnormal_note   VARCHAR(255)          COMMENT '异常记录',
    status          VARCHAR(16)           COMMENT 'RUNNING / DONE',
    PRIMARY KEY (id),
    KEY idx_step_batch (batch_no, step_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序执行记录';

-- ---------------- 4-1. 工序在制量交接单 ----------------
-- 设计取舍（两种口径二选一，本系统取「链式实际产出 + 保留原投料基准 + 派生损耗账」）：
--   · 下道工序的可用在制量 = 上道【已确认】交接单的实际产出（CONFIRMED 才生效），
--     待复核 / 退回期间可用在制量保持上道旧值，不允许开工下一道；
--   · 批次总投入始终取 tea_process_order.input_qty_kg（原投料基准，不被任何工序产出改写），
--     每步损耗 = 本步投入 − 本步产出（派生量，不单独维护会漂移的余额），
--     故恒有：总投入 = 最终产量 + 各步累计损耗 + 当前在制量，账可逐步核对。
-- 唯一键 uk_qty_batch_step：同一批次同一道工序至多一张交接单。
-- 班组结束工序、复核人结论、另一班组开工几乎同时发生时，三者都先抢 tea_process_order
-- 行级写锁串行化，配合本唯一键兜底，只能产生一份有效交接结果。
CREATE TABLE IF NOT EXISTS step_qty_handover (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    handover_no       VARCHAR(32)  NOT NULL COMMENT '交接单号 QJ+日期+序号',
    batch_no          VARCHAR(32)  NOT NULL COMMENT '批次号',
    tea_type          VARCHAR(16)           COMMENT '茶类（快照）',
    step_name         VARCHAR(16)  NOT NULL COMMENT '工序键',
    step_index        INT          NOT NULL COMMENT '工序下标',
    input_qty_kg      DECIMAL(10,2)         COMMENT '本步投入 kg（＝上道已确认产出，首道＝批次投料）',
    output_qty_kg     DECIMAL(10,2)         COMMENT '实际产出重量 kg（班组过磅登记；旧记录为空表示当时未称）',
    loss_qty_kg       DECIMAL(10,2)         COMMENT '损耗量 kg＝投入−产出（派生记账；产出未登记时为空）',
    loss_pct          DECIMAL(6,2)          COMMENT '损耗率 %＝损耗/投入（产出未登记时为空）',
    loss_reason       VARCHAR(64)           COMMENT '损耗原因',
    handover_to       VARCHAR(32)           COMMENT '交接人（交给谁 / 接班人）',
    submitted_by      VARCHAR(32)           COMMENT '提交班组 / 结束工序操作人',
    submitted_at      DATETIME              COMMENT '提交时间',
    limit_min_pct     DECIMAL(6,2)          COMMENT '本茶类本工序正常损耗范围下限 %（提交时快照）',
    limit_max_pct     DECIMAL(6,2)          COMMENT '本茶类本工序正常损耗范围上限 %（提交时快照）',
    over_limit        TINYINT(1)            COMMENT '是否超出正常损耗范围',
    status            VARCHAR(20)  NOT NULL COMMENT 'CONFIRMED 已确认 / PENDING_REVIEW 待复核 / RETURNED 已退回更正 / LEGACY 旧记录派生',
    reviewed_by       VARCHAR(32)           COMMENT '复核人',
    reviewed_at       DATETIME              COMMENT '复核时间',
    review_note       VARCHAR(500)          COMMENT '复核说明（接受异常的理由 / 退回更正的原因）',
    returned_note     VARCHAR(500)          COMMENT '最近一次退回原因（便于班组照单更正）',
    corrected_count   INT          NOT NULL DEFAULT 0 COMMENT '退回后更正重交次数',
    legacy_note       VARCHAR(255)          COMMENT '旧记录派生口径说明',
    created_at        DATETIME     NOT NULL COMMENT '创建时间（派生旧单为回填时间）',
    updated_at        DATETIME              COMMENT '最近状态变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qty_handover_no (handover_no),
    UNIQUE KEY uk_qty_batch_step (batch_no, step_index),
    KEY idx_qty_status (status),
    KEY idx_qty_batch (batch_no, step_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序在制量交接单';

-- ---------------- 4-2. 在制量交接操作留痕 ----------------
CREATE TABLE IF NOT EXISTS step_qty_handover_log (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    handover_no  VARCHAR(32) NOT NULL COMMENT '交接单号',
    batch_no     VARCHAR(32) NOT NULL COMMENT '批次号',
    step_name    VARCHAR(16)          COMMENT '工序键',
    action       VARCHAR(20) NOT NULL COMMENT 'SUBMIT 提交/AUTO_CONFIRM 范围内自动确认/REVIEW_ACCEPT 复核接受/REVIEW_RETURN 复核退回/CORRECT 更正重交',
    from_status  VARCHAR(20)          COMMENT '操作前状态',
    to_status    VARCHAR(20)          COMMENT '操作后状态',
    operator     VARCHAR(32)          COMMENT '操作人',
    result       VARCHAR(16)          COMMENT 'EFFECTIVE 已生效 / REJECTED 被驳回',
    detail       VARCHAR(500)         COMMENT '说明（重量、损耗率、损耗原因、交接人、复核理由、驳回原因）',
    created_at   DATETIME    NOT NULL COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_qlog_handover (handover_no, created_at),
    KEY idx_qlog_batch (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在制量交接操作留痕';

-- ---------------- 5. 设备 ----------------
CREATE TABLE IF NOT EXISTS equipment (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    equip_code        VARCHAR(32) NOT NULL COMMENT '设备编号',
    equip_name        VARCHAR(64)          COMMENT '设备名称',
    equip_type        VARCHAR(16)          COMMENT '杀青机 / 揉捻机 / 发酵室 / 烘干机 / 提香机',
    model             VARCHAR(64)          COMMENT '型号',
    capacity_kg       DECIMAL(8,1)         COMMENT '容量 kg',
    current_temp_c    DECIMAL(6,1)         COMMENT '当前温度 ℃',
    status            VARCHAR(16)          COMMENT 'RUNNING / IDLE / CLEANING / PENDING_CONFIRM / FAULT',
    workshop          VARCHAR(32)          COMMENT '所在车间',
    current_batch_no  VARCHAR(32)          COMMENT '当前批次',
    last_maintain_at  DATETIME             COMMENT '最近保养时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_equip_code (equip_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='制茶设备';

-- ---------------- 5-1. 设备清洗交接单 ----------------
-- 工序收尾自动形成，带出刚完成的批次与设备；清洗人员登记残留风险、方式、起止时间，
-- 可标记完成或报故障；负责人确认后设备才恢复可用。
CREATE TABLE IF NOT EXISTS cleaning_handover (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    handover_no         VARCHAR(32) NOT NULL COMMENT '交接单号 QX+日期+序号',
    equip_code          VARCHAR(32) NOT NULL COMMENT '设备编号',
    equip_name          VARCHAR(64)          COMMENT '设备名称（形成时快照）',
    equip_type          VARCHAR(16)          COMMENT '设备类型',
    batch_no            VARCHAR(32)          COMMENT '刚完成的批次号',
    step_name           VARCHAR(16)          COMMENT '刚结束的工序键',
    status              VARCHAR(20) NOT NULL COMMENT 'PENDING / IN_PROGRESS / AWAIT_CONFIRM / FAULT_FREEZE / KEEP_AND_REPAIR / CONFIRMED',
    -- active_key 为存储生成列：未闭环（非 CONFIRMED）时等于 equip_code，确认后置 NULL。
    -- 配合唯一索引 uk_handover_active，从数据库层保证一台设备至多一张未闭环交接单。
    active_key          VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN status <> 'CONFIRMED' THEN equip_code ELSE NULL END) STORED COMMENT '未闭环标记',
    residual_risk       VARCHAR(16)          COMMENT '残留风险 低/中/高',
    clean_method        VARCHAR(64)          COMMENT '清洗方式',
    clean_start_time    DATETIME             COMMENT '清洗开始时间',
    clean_end_time      DATETIME             COMMENT '清洗结束时间',
    cleaner             VARCHAR(32)          COMMENT '清洗人员',
    fault_note          VARCHAR(255)         COMMENT '故障描述',
    fault_after_confirm TINYINT(1)           COMMENT '故障是否发生在负责人确认之后',
    decision_note       VARCHAR(500)         COMMENT '冲突取舍与开工影响说明',
    repair_note         VARCHAR(255)         COMMENT '维修 / 复验情况',
    repairer            VARCHAR(32)          COMMENT '维修 / 复验人员',
    repaired_at         DATETIME             COMMENT '复验时间',
    created_by          VARCHAR(32)          COMMENT '形成交接单的工序收尾操作人',
    confirmed_by        VARCHAR(32)          COMMENT '负责人',
    confirmed_at        DATETIME             COMMENT '确认时间',
    created_at          DATETIME    NOT NULL COMMENT '形成时间',
    updated_at          DATETIME             COMMENT '最近状态变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_handover_no (handover_no),
    UNIQUE KEY uk_handover_active (active_key),
    KEY idx_handover_equip (equip_code, created_at),
    KEY idx_handover_batch (batch_no),
    KEY idx_handover_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备清洗交接单';

-- ---------------- 5-2. 清洗交接操作留痕 ----------------
CREATE TABLE IF NOT EXISTS cleaning_handover_log (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    handover_no  VARCHAR(32) NOT NULL COMMENT '交接单号',
    equip_code   VARCHAR(32) NOT NULL COMMENT '设备编号',
    action       VARCHAR(20) NOT NULL COMMENT 'CREATE/START/COMPLETE/REPORT_FAULT/CONFIRM/REOPEN/RECHECK',
    from_status  VARCHAR(20)          COMMENT '操作前状态',
    to_status    VARCHAR(20)          COMMENT '操作后状态',
    operator     VARCHAR(32)          COMMENT '操作人',
    result       VARCHAR(16)          COMMENT 'EFFECTIVE 已生效 / REJECTED 被驳回',
    detail       VARCHAR(500)         COMMENT '说明（含驳回原因、取舍与开工影响）',
    created_at   DATETIME    NOT NULL COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_hlog_handover (handover_no, created_at),
    KEY idx_hlog_equip (equip_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='清洗交接操作留痕';

-- ---------------- 6. 烘房 ----------------
CREATE TABLE IF NOT EXISTS drying_room (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    room_no          VARCHAR(16) NOT NULL COMMENT '房号',
    layers           INT                  COMMENT '层数',
    current_batch_no VARCHAR(32)          COMMENT '在烘批次',
    temp_curve       VARCHAR(128)         COMMENT '温控曲线',
    target_temp_c    DECIMAL(6,1)         COMMENT '目标温度 ℃',
    current_temp_c   DECIMAL(6,1)         COMMENT '当前温度 ℃',
    drying_minutes   INT                  COMMENT '在烘时长 min',
    status           VARCHAR(16)          COMMENT 'RUNNING / IDLE / CLEANING / FAULT',
    updated_at       DATETIME             COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_no (room_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='烘房';

-- ---------------- 7. 审评记录 ----------------
CREATE TABLE IF NOT EXISTS tea_evaluation (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    batch_no        VARCHAR(32) NOT NULL COMMENT '批次号',
    tea_type        VARCHAR(16)          COMMENT '茶类',
    shape_score     DECIMAL(5,2)         COMMENT '外形',
    soup_score      DECIMAL(5,2)         COMMENT '汤色',
    aroma_score     DECIMAL(5,2)         COMMENT '香气',
    taste_score     DECIMAL(5,2)         COMMENT '滋味',
    leaf_base_score DECIMAL(5,2)         COMMENT '叶底',
    total_score     DECIMAL(5,2)         COMMENT '加权总分',
    comment         VARCHAR(255)         COMMENT '评语',
    defect          VARCHAR(64)          COMMENT '缺陷',
    evaluator       VARCHAR(32)          COMMENT '审评员',
    evaluated_at    DATETIME             COMMENT '审评时间',
    PRIMARY KEY (id),
    KEY idx_eval_batch (batch_no),
    KEY idx_eval_total (total_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审评记录';

-- ---------------- 8. 成品入库 ----------------
CREATE TABLE IF NOT EXISTS finished_product (
    product_no    VARCHAR(32)  NOT NULL COMMENT '成品编号（自动生成 CP+日期+序号）',
    batch_no      VARCHAR(32)  NOT NULL COMMENT '批次号',
    tea_type      VARCHAR(16)           COMMENT '茶类',
    grade         VARCHAR(16)           COMMENT '特级 / 一级 / 二级 / 三级',
    packages      INT                   COMMENT '件数',
    net_weight_kg DECIMAL(10,2)         COMMENT '净重 kg',
    storage_bin   VARCHAR(32)           COMMENT '入库仓位',
    moisture_pct  DECIMAL(5,2)          COMMENT '出厂水分 %',
    status        VARCHAR(16)           COMMENT 'STORED / REJECTED',
    stored_at     DATETIME              COMMENT '入库时间',
    PRIMARY KEY (product_no),
    KEY idx_product_batch (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成品入库';

-- =====================================================================
-- 种子数据
-- =====================================================================

-- ---------------- 鲜叶进厂验收单（10 车，含扣重） ----------------
INSERT INTO leaf_intake (intake_no, supplier_name, plot_code, pick_date, leaf_grade, gross_weight_kg,
                         moisture_pct, impurity_pct, inspector, deduct_weight_kg, deduct_rate_pct,
                         deduct_reason, net_weight_kg, unit_price, settle_price, status, created_at) VALUES
(CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 '勐海曼弄合作社', 'A-03 古树区', DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY), '单芽',
 128.50, 74.20, 1.80, '陈质检', 0.00, 0.00, '验收合格，未扣重', 128.50, 118.00, 15163.00, 'ACCEPTED',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 HOUR),
(CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-002'),
 '易武麻黑 李阿芳', 'B-11 半坡地', DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY), '一芽一叶',
 216.40, 79.60, 2.10, '陈质检', 5.19, 2.40, '含水量 79.60% 超上限 78%', 211.21, 86.00, 18164.06, 'DEDUCTED',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 8 HOUR),
(CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-003'),
 '凤庆大寺初制所', 'C-07 台地茶', DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY), '一芽二叶',
 342.80, 76.10, 6.40, '杨质检', 9.60, 2.80, '杂质率 6.40% 超上限 5%', 333.20, 52.00, 17326.40, 'DEDUCTED',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 7 HOUR),
(CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-004'),
 '安溪西坪 王水土', 'D-02 内安溪', DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY), '对夹叶',
 288.00, 77.30, 3.20, '杨质检', 0.00, 0.00, '验收合格，未扣重', 288.00, 24.00, 6912.00, 'ACCEPTED',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 HOUR),
(CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-005'),
 '福鼎点头 陈氏茶园', 'E-05 大白茶园', DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY), '单芽',
 96.20, 75.40, 0.90, '林质检', 0.00, 0.00, '验收合格，未扣重', 96.20, 118.00, 11351.60, 'ACCEPTED',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 HOUR),
(CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-006'),
 '勐海勐混 岩香', 'A-09 生态茶园', DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY), '一芽一叶',
 174.60, 81.20, 5.80, '陈质检', 11.17, 6.40, '含水量 81.20% 超上限 78%；杂质率 5.80% 超上限 5%',
 163.43, 86.00, 14054.98, 'DEDUCTED', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 HOUR),
(CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-007'),
 '祁门历口合作社', 'F-04 槠叶种', DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY), '一芽一叶',
 205.30, 77.80, 2.60, '王质检', 0.00, 0.00, '验收合格，未扣重', 205.30, 86.00, 17655.80, 'ACCEPTED',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 HOUR),
(CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-008'),
 '武夷星村 江氏', 'G-01 正岩区', DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY), '对夹叶',
 312.50, 78.90, 4.10, '王质检', 4.22, 1.35, '含水量 78.90% 超上限 78%', 308.28, 24.00, 7398.72, 'DEDUCTED',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 HOUR),
(CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-009'),
 '普洱困鹿山 赵家', 'H-06 古树', DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY), '单芽',
 88.40, 73.60, 1.20, '陈质检', 0.00, 0.00, '验收合格，未扣重', 88.40, 118.00, 10431.20, 'ACCEPTED',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 HOUR),
(CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-010'),
 '临沧邦东 昔归组', 'I-02 岩茶地', DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY), '一芽二叶',
 268.90, 80.10, 7.20, '杨质检', 20.30, 7.55, '含水量 80.10% 超上限 78%；杂质率 7.20% 超上限 5%',
 248.60, 52.00, 12927.20, 'DEDUCTED', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 HOUR);

-- ---------------- 摊青槽（8 槽在槽记录） ----------------
INSERT INTO withering_trough (trough_no, intake_no, batch_no, tea_type, wither_method, leaf_thickness_cm,
                              wither_hours, temp_c, humidity_pct, current_loss_pct, target_loss_pct,
                              leaf_weight_kg, status, opened_at, updated_at) VALUES
('TQ-01', CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-002'),
 CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 'GREEN', '室内萎凋', 3.0, 8.5, 24.0, 68.0, 33.50, 32.00, 180.00, 'READY',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 8 HOUR,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 30 MINUTE),
('TQ-02', CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-006'), NULL,
 'GREEN', '室内萎凋', 4.5, 6.0, 23.5, 70.0, 27.80, 32.00, 210.00, 'WITHERING',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 8 HOUR,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 25 MINUTE),
('TQ-03', CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-007'),
 CONCAT('PC-BLACK-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 'RED', '日光晒青', 2.5, 10.0, 28.0, 62.0, 38.60, 38.00, 195.00, 'READY',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 16 HOUR,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 40 MINUTE),
('TQ-04', NULL, NULL,
 'RED', '室内萎凋', 5.0, 7.5, 25.0, 66.0, 31.20, 38.00, 240.00, 'WITHERING',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 7 HOUR,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 MINUTE),
('TQ-05', CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-008'),
 CONCAT('PC-OOLONG-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 'OOLONG', '日光晒青', 2.0, 4.5, 30.5, 58.0, 26.40, 26.00, 150.00, 'READY',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 7 HOUR,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 MINUTE),
('TQ-06', NULL, NULL,
 'OOLONG', '室内萎凋', 3.5, 3.0, 26.0, 64.0, 19.80, 26.00, 160.00, 'WITHERING',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 HOUR,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 15 MINUTE),
('TQ-07', CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-005'),
 CONCAT('PC-WHITE-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 'WHITE', '室内萎凋', 1.5, 24.0, 22.0, 72.0, 45.20, 45.00, 120.00, 'READY',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 36 HOUR,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 90 MINUTE),
('TQ-08', CONCAT('XY', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-010'),
 CONCAT('PC-PUER-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 'PUER', '室内萎凋', 6.0, 2.5, 27.5, 60.0, 9.40, 12.00, 260.00, 'WITHERING',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 HOUR,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 MINUTE);

-- ---------------- 制茶工单（6 张；旧批次数量口径 qty_mode=LEGACY，在制量按历史记录派生回填） ----------------
INSERT INTO tea_process_order (batch_no, tea_type, process_seq, current_step, current_step_index,
                               input_qty_kg, output_qty_kg, available_wip_kg, qty_mode,
                               source_trough_no, operator, status, created_at) VALUES
(CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 'GREEN', 'SPREAD>FIX>ROLL>DRY>PICK', 'DONE', 5, 180.00, 39.60, 39.60, 'LEGACY', 'TQ-01', '周师傅', 'DONE',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 7 HOUR),
(CONCAT('PC-BLACK-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 'RED', 'SPREAD>ROLL>FERMENT>DRY>PICK', 'DONE', 5, 195.00, 46.80, 46.80, 'LEGACY', 'TQ-03', '李师傅', 'DONE',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 HOUR),
(CONCAT('PC-WHITE-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 'WHITE', 'SPREAD>DRY>PICK', 'DONE', 3, 120.00, 31.20, 31.20, 'LEGACY', 'TQ-07', '陈师傅', 'DONE',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 12 HOUR),
(CONCAT('PC-PUER-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 'PUER', 'SPREAD>FIX>ROLL>FERMENT>DRY>PICK', 'FERMENT', 3, 260.00, 0.00, 240.00, 'LEGACY', 'TQ-08', '岩师傅', 'PROCESSING',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 HOUR),
(CONCAT('PC-OOLONG-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 'OOLONG', 'SPREAD>MAKING>FIX>ROLL>DRY>PICK', 'FIX', 2, 150.00, 0.00, 150.00, 'LEGACY', 'TQ-05', '江师傅', 'PROCESSING',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 HOUR),
(CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-002'),
 'GREEN', 'SPREAD>FIX>ROLL>DRY>PICK', 'FIX', 1, 210.00, 0.00, 210.00, 'LEGACY', 'TQ-02', '周师傅', 'PROCESSING',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 HOUR);

-- ---------------- 工序执行记录（绿茶批次 001 全流程） ----------------
INSERT INTO process_step_record (batch_no, tea_type, step_name, step_index, equipment_code, start_time, end_time,
                                 param_temp_c, param_temp2_c, param_minutes, param_minutes2, param_rpm,
                                 param_pressure, param_leaf_qty, humidity_pct, param_profile, operator,
                                 abnormal_note, status) VALUES
(CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'GREEN', 'SPREAD', 0, 'TQ-01',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 16 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 8 HOUR,
 NULL, NULL, 480, NULL, NULL, NULL, 180.00, 68.0, '摊叶厚度 3.0cm，室内萎凋', '周师傅', NULL, 'DONE'),
(CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'GREEN', 'FIX', 1, 'SQ-01',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 8 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 7 HOUR + INTERVAL 30 MINUTE,
 235.0, NULL, 4, NULL, NULL, NULL, 180.00, NULL, '滚筒温度 235℃，投叶 180kg', '周师傅', NULL, 'DONE'),
(CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'GREEN', 'ROLL', 2, 'RN-01',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 7 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 HOUR + INTERVAL 20 MINUTE,
 NULL, NULL, 35, NULL, 45, 8.5, 78.00, NULL, '轻压 8.5kg，转速 45r/min', '周师傅', NULL, 'DONE'),
(CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'GREEN', 'DRY', 3, 'HG-01',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 HOUR,
 110.0, 90.0, 25, 40, NULL, NULL, 78.00, NULL, '毛火110℃/足火90℃', '周师傅', NULL, 'DONE'),
(CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'GREEN', 'PICK', 4, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 HOUR,
 NULL, NULL, 60, NULL, NULL, NULL, 39.60, NULL, '拣剔拼配 39.6kg', '周师傅', '检出少量焦边爆点', 'DONE');

-- ---------------- 工序执行记录（红茶批次 001 全流程） ----------------
INSERT INTO process_step_record (batch_no, tea_type, step_name, step_index, equipment_code, start_time, end_time,
                                 param_temp_c, param_temp2_c, param_minutes, param_minutes2, param_rpm,
                                 param_pressure, param_leaf_qty, humidity_pct, param_profile, operator,
                                 abnormal_note, status) VALUES
(CONCAT('PC-BLACK-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'RED', 'SPREAD', 0, 'TQ-03',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 16 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 HOUR,
 NULL, NULL, 600, NULL, NULL, NULL, 195.00, 62.0, '日光晒青 + 室内萎凋', '李师傅', NULL, 'DONE'),
(CONCAT('PC-BLACK-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'RED', 'ROLL', 1, 'RN-02',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 HOUR + INTERVAL 15 MINUTE,
 NULL, NULL, 45, NULL, 50, 12.0, 120.00, NULL, '揉捻 45min，细胞破损率 85%', '李师傅', NULL, 'DONE'),
(CONCAT('PC-BLACK-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'RED', 'FERMENT', 2, 'FJ-01',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 HOUR,
 28.0, NULL, 210, NULL, NULL, NULL, 120.00, 92.0, '发酵室 28℃/湿度92%', '李师傅', NULL, 'DONE'),
(CONCAT('PC-BLACK-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'RED', 'DRY', 3, 'HG-01',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 HOUR,
 115.0, 95.0, 30, 45, NULL, NULL, 46.80, NULL, '毛火115℃/足火95℃', '李师傅', NULL, 'DONE'),
(CONCAT('PC-BLACK-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'RED', 'PICK', 4, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 40 MINUTE,
 NULL, NULL, 55, NULL, NULL, NULL, 46.80, NULL, '风选拣剔', '李师傅', NULL, 'DONE');

-- ---------------- 工序执行记录（白茶批次 001 全流程） ----------------
INSERT INTO process_step_record (batch_no, tea_type, step_name, step_index, equipment_code, start_time, end_time,
                                 param_temp_c, param_temp2_c, param_minutes, param_minutes2, param_rpm,
                                 param_pressure, param_leaf_qty, humidity_pct, param_profile, operator,
                                 abnormal_note, status) VALUES
(CONCAT('PC-WHITE-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'WHITE', 'SPREAD', 0, 'TQ-07',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 36 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 12 HOUR,
 NULL, NULL, 1440, NULL, NULL, NULL, 120.00, 72.0, '自然萎凋 24h，摊叶 1.5cm', '陈师傅', NULL, 'DONE'),
(CONCAT('PC-WHITE-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'WHITE', 'DRY', 1, 'HG-02',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 12 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 HOUR,
 80.0, 60.0, 90, 120, NULL, NULL, 66.00, NULL, '毛火80℃/足火60℃', '陈师傅', NULL, 'DONE'),
(CONCAT('PC-WHITE-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'WHITE', 'PICK', 2, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 8 HOUR,
 NULL, NULL, 40, NULL, NULL, NULL, 31.20, NULL, '拣剔拼配', '陈师傅', '检出少量红变叶', 'DONE');

-- ---------------- 工序执行记录（普洱批次 001 进行中：摊青→杀青→揉捻） ----------------
INSERT INTO process_step_record (batch_no, tea_type, step_name, step_index, equipment_code, start_time, end_time,
                                 param_temp_c, param_temp2_c, param_minutes, param_minutes2, param_rpm,
                                 param_pressure, param_leaf_qty, humidity_pct, param_profile, operator,
                                 abnormal_note, status) VALUES
(CONCAT('PC-PUER-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'PUER', 'SPREAD', 0, 'TQ-08',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 HOUR,
 NULL, NULL, 150, NULL, NULL, NULL, 260.00, 60.0, '摊青 2.5h，摊叶 6cm', '岩师傅', NULL, 'DONE'),
(CONCAT('PC-PUER-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'PUER', 'FIX', 1, 'SQ-02',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 HOUR + INTERVAL 40 MINUTE,
 190.0, NULL, 6, NULL, NULL, NULL, 260.00, NULL, '铁锅手工杀青 190℃', '岩师傅', NULL, 'DONE'),
(CONCAT('PC-PUER-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'PUER', 'ROLL', 2, 'RN-01',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 HOUR + INTERVAL 20 MINUTE,
 NULL, NULL, 30, NULL, 40, 10.5, 240.00, NULL, '揉捻成条', '岩师傅', NULL, 'DONE');

-- ---------------- 工序执行记录（乌龙批次 001 进行中：晒青→做青） ----------------
INSERT INTO process_step_record (batch_no, tea_type, step_name, step_index, equipment_code, start_time, end_time,
                                 param_temp_c, param_temp2_c, param_minutes, param_minutes2, param_rpm,
                                 param_pressure, param_leaf_qty, humidity_pct, param_profile, operator,
                                 abnormal_note, status) VALUES
(CONCAT('PC-OOLONG-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'OOLONG', 'SPREAD', 0, 'TQ-05',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 7 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 HOUR + INTERVAL 30 MINUTE,
 NULL, NULL, 270, NULL, NULL, NULL, 150.00, 58.0, '日光晒青 4.5h', '江师傅', NULL, 'DONE'),
(CONCAT('PC-OOLONG-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'OOLONG', 'MAKING', 1, 'RN-02',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 HOUR + INTERVAL 10 MINUTE,
 24.0, NULL, 90, NULL, 18, NULL, 150.00, 64.0, '摇青三遍，转速 18r/min', '江师傅', NULL, 'DONE');

-- ---------------- 工序执行记录（绿茶批次 002 进行中：摊青） ----------------
INSERT INTO process_step_record (batch_no, tea_type, step_name, step_index, equipment_code, start_time, end_time,
                                 param_temp_c, param_temp2_c, param_minutes, param_minutes2, param_rpm,
                                 param_pressure, param_leaf_qty, humidity_pct, param_profile, operator,
                                 abnormal_note, status) VALUES
(CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-002'), 'GREEN', 'SPREAD', 0, 'TQ-02',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 8 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 HOUR,
 NULL, NULL, 360, NULL, NULL, NULL, 210.00, 70.0, '摊叶 4.5cm，室内萎凋', '周师傅', NULL, 'DONE');

-- ---------------- 设备（11 台） ----------------
INSERT INTO equipment (equip_code, equip_name, equip_type, model, capacity_kg, current_temp_c, status,
                       workshop, current_batch_no, last_maintain_at) VALUES
('SQ-01', '1 号滚筒杀青机', '杀青机', '6CST-80', 120.0, 235.0, 'RUNNING', '杀青车间', NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY),
('SQ-02', '2 号滚筒杀青机', '杀青机', '6CST-60', 80.0, 186.0, 'RUNNING', '杀青车间',
 CONCAT('PC-PUER-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
('SQ-03', '3 号微波杀青机', '杀青机', '6CW-40', 50.0, 0.0, 'FAULT', '杀青车间', NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 DAY),
('RN-01', '1 号揉捻机', '揉捻机', '6CR-55', 60.0, 0.0, 'PENDING_CONFIRM', '揉捻车间', NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 DAY),
('RN-02', '2 号揉捻机', '揉捻机', '6CR-45', 45.0, 0.0, 'RUNNING', '揉捻车间',
 CONCAT('PC-OOLONG-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 DAY),
('FJ-01', '1 号发酵室', '发酵室', 'FJ-20', 500.0, 28.0, 'RUNNING', '发酵车间', NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY),
('FJ-02', '2 号发酵室', '发酵室', 'FJ-15', 350.0, 26.0, 'IDLE', '发酵车间', NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY),
('HG-01', '1 号链式烘干机', '烘干机', '6CH-20', 200.0, 110.0, 'RUNNING', '干燥车间', NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY),
('HG-02', '2 号箱式烘干机', '烘干机', '6CH-10', 120.0, 95.0, 'IDLE', '干燥车间', NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY),
('TX-01', '1 号提香机', '提香机', '6CTX-8', 80.0, 85.0, 'IDLE', '干燥车间', NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 7 DAY),
('TX-02', '2 号提香机', '提香机', '6CTX-6', 60.0, 0.0, 'CLEANING', '干燥车间', NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY);

-- ---------------- 烘房（5 间） ----------------
INSERT INTO drying_room (room_no, layers, current_batch_no, temp_curve, target_temp_c, current_temp_c,
                         drying_minutes, status, updated_at) VALUES
('HF-A', 5, CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 '毛火110℃→足火90℃', 100.0, 104.0, 65, 'RUNNING', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 12 MINUTE),
('HF-B', 6, CONCAT('PC-BLACK-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 '毛火115℃→足火95℃', 105.0, 108.0, 75, 'RUNNING', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 8 MINUTE),
('HF-C', 4, NULL, '待用', NULL, 26.0, 0, 'IDLE', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 HOUR),
('HF-D', 5, CONCAT('PC-WHITE-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 '毛火80℃→足火60℃', 70.0, 68.0, 210, 'RUNNING', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 MINUTE),
('HF-E', 6, NULL, '待用', NULL, 24.0, 0, 'CLEANING', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 HOUR);

-- ---------------- 审评记录（6 份，五项齐全） ----------------
INSERT INTO tea_evaluation (batch_no, tea_type, shape_score, soup_score, aroma_score, taste_score,
                            leaf_base_score, total_score, comment, defect, evaluator, evaluated_at) VALUES
(CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'GREEN',
 92.00, 94.00, 90.00, 91.00, 88.00, 91.00,
 '条索紧结匀整，汤色嫩绿明亮，栗香高长，滋味鲜醇回甘，叶底嫩绿完整', NULL, '沈审评',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 HOUR),
(CONCAT('PC-BLACK-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'RED',
 88.00, 90.00, 92.00, 89.00, 86.00, 89.30,
 '条索乌润紧细，汤色红艳明亮，甜香带蜜韵，滋味醇厚鲜甜，叶底红匀', NULL, '吴审评',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 90 MINUTE),
(CONCAT('PC-WHITE-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'WHITE',
 85.00, 86.00, 84.00, 83.00, 82.00, 83.95,
 '芽头肥壮披毫，汤色杏黄清亮，毫香显，滋味清甜爽口，叶底匀整', NULL, '沈审评',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 7 HOUR),
(CONCAT('PC-PUER-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'PUER',
 80.00, 82.00, 78.00, 76.00, 74.00, 77.90,
 '条索肥壮尚紧，汤色橙黄尚亮，陈香初显，滋味醇和略涩，叶底尚匀', '断碎', '郑审评',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 100 MINUTE),
(CONCAT('PC-OOLONG-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'OOLONG',
 94.00, 92.00, 95.00, 93.00, 90.00, 93.35,
 '颗粒紧结重实，汤色金黄透亮，兰花香馥郁持久，滋味醇厚甘爽音韵显，叶底绿叶红边', NULL, '吴审评',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 80 MINUTE),
(CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-002'), 'GREEN',
 76.00, 74.00, 72.00, 70.00, 68.00, 72.20,
 '条索尚紧略碎，汤色黄绿尚亮，清香尚纯，滋味尚醇略涩，叶底黄绿带红边', '红变叶', '郑审评',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 MINUTE);

-- ---------------- 成品入库（6 件，含 1 件水分超标挂账） ----------------
INSERT INTO finished_product (product_no, batch_no, tea_type, grade, packages, net_weight_kg, storage_bin,
                              moisture_pct, status, stored_at) VALUES
(CONCAT('CP', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'GREEN',
 '特级', 12, 240.00, 'A库-01 货架A1', 5.20, 'STORED', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 HOUR),
(CONCAT('CP', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-002'),
 CONCAT('PC-BLACK-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'RED',
 '一级', 18, 360.00, 'A库-02 货架A3', 5.60, 'STORED', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 80 MINUTE),
(CONCAT('CP', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-003'),
 CONCAT('PC-WHITE-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'WHITE',
 '一级', 9, 180.00, 'B库-03 货架B1', 6.10, 'STORED', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 HOUR),
(CONCAT('CP', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-004'),
 CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'GREEN',
 '二级', 6, 120.00, 'B库-05 货架B4', 6.80, 'STORED', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 HOUR),
(CONCAT('CP', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-005'),
 CONCAT('PC-PUER-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'PUER',
 '二级', 20, 400.00, '待复火区-01', 8.20, 'REJECTED', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 MINUTE),
(CONCAT('CP', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-006'),
 CONCAT('PC-BLACK-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'RED',
 '三级', 8, 160.00, 'C库-02 货架C2', 7.00, 'STORED', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 30 MINUTE);

-- ---------------- 设备清洗交接单（2 张：1 张等待确认在锁、1 张已确认闭环；旧设备无交接单沿用原状态） ----------------
INSERT INTO cleaning_handover (handover_no, equip_code, equip_name, equip_type, batch_no, step_name, status,
                              residual_risk, clean_method, clean_start_time, clean_end_time, cleaner,
                              created_by, confirmed_by, confirmed_at, created_at, updated_at) VALUES
(CONCAT('QX-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'),
 'RN-01', '1 号揉捻机', '揉捻机',
 CONCAT('PC-GREEN-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'ROLL', 'AWAIT_CONFIRM',
 '中', '人工拆卸清扫',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 MINUTE, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 15 MINUTE,
 '何清洗', '周师傅', NULL, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 MINUTE, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 15 MINUTE),
(CONCAT('QX-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-002'),
 'HG-02', '2 号箱式烘干机', '烘干机',
 CONCAT('PC-WHITE-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'DRY', 'CONFIRMED',
 '低', '高温蒸汽',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 HOUR, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 HOUR - INTERVAL 20 MINUTE,
 '何清洗', '陈师傅', '李负责', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 HOUR,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 HOUR - INTERVAL 10 MINUTE,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 HOUR);

-- ---------------- 清洗交接操作留痕（与上面两张单对应） ----------------
INSERT INTO cleaning_handover_log (handover_no, equip_code, action, from_status, to_status, operator, result, detail, created_at) VALUES
(CONCAT('QX-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'RN-01', 'CREATE', NULL, 'PENDING', '周师傅', 'EFFECTIVE',
 '工序收尾形成交接单：批次绿茶 001 拣剔后设备 RN-01 待清洗，确认前不能用于下一批次',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 MINUTE),
(CONCAT('QX-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'RN-01', 'START', 'PENDING', 'IN_PROGRESS', '何清洗', 'EFFECTIVE',
 '开始清洗：残留风险中，方式「人工拆卸清扫」，拆揉桶刮除茶垢后清水复冲',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 MINUTE),
(CONCAT('QX-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-001'), 'RN-01', 'COMPLETE', 'IN_PROGRESS', 'AWAIT_CONFIRM', '何清洗', 'EFFECTIVE',
 '清洗完成，等待负责人确认；确认前设备不能用于下一批次',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 15 MINUTE),
(CONCAT('QX-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-002'), 'HG-02', 'CREATE', NULL, 'PENDING', '陈师傅', 'EFFECTIVE',
 '工序收尾形成交接单：白茶 001 干燥后设备 HG-02 待清洗',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 HOUR - INTERVAL 10 MINUTE),
(CONCAT('QX-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-002'), 'HG-02', 'START', 'PENDING', 'IN_PROGRESS', '何清洗', 'EFFECTIVE',
 '开始清洗：残留风险低，方式「高温蒸汽」',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 HOUR),
(CONCAT('QX-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-002'), 'HG-02', 'COMPLETE', 'IN_PROGRESS', 'AWAIT_CONFIRM', '何清洗', 'EFFECTIVE',
 '清洗完成，等待负责人确认',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 HOUR - INTERVAL 20 MINUTE),
(CONCAT('QX-', DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y%m%d'), '-002'), 'HG-02', 'CONFIRM', 'AWAIT_CONFIRM', 'CONFIRMED', '李负责', 'EFFECTIVE',
 '负责人确认设备恢复可用，已转为待机，可承接下一批次开工',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 HOUR);

-- =====================================================================
-- 旧工序记录兼容口径回填（不是补称、不伪造损耗）
-- 每一条 status=DONE 的旧工序记录派生一张 LEGACY 在制量交接单：
--   · 本步投入 = 本道记录登记的投叶量（首道缺省取批次投料），沿用旧工单“投叶量即本步收到的在制量”口径；
--   · 实际产出：仅末道用工单产量；中间工序旧系统未过磅，一律留空，损耗量/损耗率同样留空，
--     不把“没称”当成零损耗；
--   · 中间工序的损耗按“本步投入 − 下道投入（下道投叶量）”估列并明确标注为估列，
--     使旧批次也逐级对平：总投入 = 最终产量 + 各步损耗（估列+末道实差），可解释、可核对；
--   · 旧批次不参与新损耗范围复核、不锁死；从下一道登记实称产出的工序起自动转为逐道实称口径（混合批次）。
-- =====================================================================
INSERT INTO step_qty_handover (handover_no, batch_no, tea_type, step_name, step_index,
                               input_qty_kg, output_qty_kg, loss_qty_kg, loss_pct,
                               loss_reason, handover_to, submitted_by, submitted_at,
                               limit_min_pct, limit_max_pct, over_limit, status,
                               corrected_count, legacy_note, created_at, updated_at)
SELECT CONCAT('QJ-LEGACY-', LPAD(ROW_NUMBER() OVER (ORDER BY r.batch_no, r.step_index), 4, '0')),
       r.batch_no, r.tea_type, r.step_name, r.step_index,
       r.input_qty_kg,
       CASE WHEN r.step_index = LAST_STEP.last_idx AND o.status = 'DONE' THEN o.output_qty_kg ELSE NULL END,
       CASE WHEN r.step_index = LAST_STEP.last_idx AND o.status = 'DONE'
            THEN ROUND(r.input_qty_kg - o.output_qty_kg, 2)
            WHEN r.step_index <> LAST_STEP.last_idx AND r.next_input_qty IS NOT NULL
                 AND ROUND(r.input_qty_kg - r.next_input_qty, 2) > 0
            THEN ROUND(r.input_qty_kg - r.next_input_qty, 2)
            ELSE NULL END,
       CASE WHEN r.step_index = LAST_STEP.last_idx AND o.status = 'DONE'
            THEN ROUND((r.input_qty_kg - o.output_qty_kg) / r.input_qty_kg * 100, 2)
            WHEN r.step_index <> LAST_STEP.last_idx AND r.next_input_qty IS NOT NULL
                 AND ROUND(r.input_qty_kg - r.next_input_qty, 2) > 0
            THEN ROUND((r.input_qty_kg - r.next_input_qty) / r.input_qty_kg * 100, 2)
            ELSE NULL END,
       '旧记录未登记（按历史数据派生）',
       NULL, r.operator, r.end_time,
       NULL, NULL, NULL, 'LEGACY', 0,
       CASE WHEN r.step_index = LAST_STEP.last_idx AND o.status = 'DONE'
            THEN '旧批次末道：产出取工单产量，损耗由总投入与最终产量差额派生'
            WHEN r.step_index = LAST_STEP.last_idx
            THEN '在制批次已完成的最后一道：下道尚未执行，实际产出当时未过磅，损耗不估列，差额由当前在制量承接'
            WHEN r.next_input_qty IS NOT NULL AND ROUND(r.input_qty_kg - r.next_input_qty, 2) > 0
            THEN '旧批次中间工序：当时未过磅，损耗按相邻工序投叶量差额估列，仅作参考'
            ELSE '旧批次中间工序：当时未过磅，实际产出与损耗未登记，不计零损耗'
       END,
       r.end_time, r.end_time
FROM (
    SELECT r.*,
           COALESCE(r.param_leaf_qty,
                    (SELECT o2.input_qty_kg FROM tea_process_order o2 WHERE o2.batch_no = r.batch_no))
               AS input_qty_kg,
           LEAD(r.param_leaf_qty) OVER (PARTITION BY r.batch_no ORDER BY r.step_index)
               AS next_input_qty
    FROM process_step_record r
    WHERE r.status = 'DONE'
) r
JOIN tea_process_order o ON o.batch_no = r.batch_no
JOIN (
    SELECT batch_no, MAX(step_index) AS last_idx
    FROM process_step_record
    GROUP BY batch_no
) LAST_STEP ON LAST_STEP.batch_no = r.batch_no;
