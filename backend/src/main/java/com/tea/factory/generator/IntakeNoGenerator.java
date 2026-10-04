package com.tea.factory.generator;

import java.util.EnumSet;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

/**
 * Hibernate 6 自定义 ID 生成器：生成鲜叶进厂单号 XY20260928-001。
 */
public class IntakeNoGenerator implements BeforeExecutionGenerator {

    public static final String TABLE = "leaf_intake";
    public static final String COLUMN = "intake_no";
    public static final String PREFIX = "XY";

    public IntakeNoGenerator() {
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
            EventType eventType) {
        return NoSequenceStore.next(TABLE, COLUMN, PREFIX);
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
