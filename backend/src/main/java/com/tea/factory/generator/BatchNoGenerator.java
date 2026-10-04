package com.tea.factory.generator;

import java.util.EnumSet;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

import com.tea.factory.common.Dict;

/**
 * Hibernate 6 自定义 ID 生成器：生成制茶工单批次号 PC-GREEN-20260928-003。
 * 中段茶类代码取自实体自身的 teaType 字段（生成器能拿到 owner 实例）。
 */
public class BatchNoGenerator implements BeforeExecutionGenerator {

    public static final String TABLE = "tea_process_order";
    public static final String COLUMN = "batch_no";
    public static final String PREFIX = "PC-";

    public BatchNoGenerator() {
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
            EventType eventType) {
        String teaType = null;
        if (owner != null) {
            try {
                teaType = (String) owner.getClass().getMethod("getTeaType").invoke(owner);
            } catch (ReflectiveOperationException ignored) {
                teaType = null;
            }
        }
        return NoSequenceStore.next(TABLE, COLUMN, PREFIX + Dict.teaTypeCode(teaType));
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }

    @Override
    public boolean allowAssignedIdentifiers() {
        return false;
    }
}
