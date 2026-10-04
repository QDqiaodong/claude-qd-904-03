package com.tea.factory.generator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 单号序号仓库：按「前缀 + 当日日期」维护自增序号。
 * 首次用到某个前缀时从库里取该前缀当天已有的最大单号做初始化，
 * 这样容器重启后也不会和已存在的单号撞号。
 */
@Component
public class NoSequenceStore {

    private static final Logger log = LoggerFactory.getLogger(NoSequenceStore.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static volatile NoSequenceStore INSTANCE;

    private final JdbcTemplate jdbc;
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public NoSequenceStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        INSTANCE = this;
    }

    static NoSequenceStore instance() {
        return INSTANCE;
    }

    /** 生成下一条单号：prefix + yyyyMMdd + "-" + 三位序号 */
    static String next(String table, String column, String prefix) {
        String date = LocalDate.now().format(DATE_FMT);
        NoSequenceStore store = instance();
        if (store == null) {
            return prefix + date + "-001";
        }
        return store.nextInternal(table, column, prefix, date);
    }

    /** 清洗交接单号：QX + 日期 + 三位序号 */
    public static String handoverNo() {
        return next("cleaning_handover", "handover_no", "QX-");
    }

    /** 工序在制量交接单号：QJ + 日期 + 三位序号 */
    public static String qtyHandoverNo() {
        return next("step_qty_handover", "handover_no", "QJ-");
    }

    private String nextInternal(String table, String column, String prefix, String date) {
        AtomicInteger counter = counters.computeIfAbsent(prefix + "|" + date,
                key -> new AtomicInteger(queryMax(table, column, prefix, date)));
        return prefix + date + "-" + String.format("%03d", counter.incrementAndGet());
    }

    /** 查询该前缀当天已用到的最大序号 */
    private int queryMax(String table, String column, String prefix, String date) {
        try {
            String sql = "select max(" + column + ") from " + table + " where " + column + " like ?";
            String max = jdbc.queryForObject(sql, String.class, prefix + date + "-%");
            if (max == null || max.length() < 3) {
                return 0;
            }
            return Integer.parseInt(max.substring(max.length() - 3));
        } catch (Exception e) {
            log.warn("单号序号初始化失败（{}），从 0 开始：{}", table, e.getMessage());
            return 0;
        }
    }
}
