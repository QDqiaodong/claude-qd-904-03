package com.tea.factory.generator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hibernate.annotations.IdGeneratorType;

/**
 * 制茶工单批次号生成器注解：PC-茶类代码 + 日期 + 当日自增序号，如 PC-GREEN-20260928-003。
 * 使用方法：{@code @Id @GeneratedValue @BatchNo private String batchNo;}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@IdGeneratorType(BatchNoGenerator.class)
public @interface BatchNo {
}
