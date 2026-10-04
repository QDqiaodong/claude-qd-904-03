package com.tea.factory.generator;

import java.util.EnumSet;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

/**
 * Hibernate 6 自定义 ID 生成器：生成成品入库编号 CP20260928-001。
 */
public class ProductNoGenerator implements BeforeExecutionGenerator {

    public static final String TABLE = "finished_product";
    public static final String COLUMN = "product_no";
    public static final String PREFIX = "CP";

    public ProductNoGenerator() {
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
