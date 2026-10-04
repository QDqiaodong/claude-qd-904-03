package com.tea.factory.generator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hibernate.annotations.IdGeneratorType;

/**
 * 成品编号生成器注解：CP + 日期 + 当日自增序号，如 CP20260928-001。
 * 使用方法：{@code @Id @GeneratedValue @ProductNo private String productNo;}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@IdGeneratorType(ProductNoGenerator.class)
public @interface ProductNo {
}
