package com.tea.factory.generator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hibernate.annotations.IdGeneratorType;

/**
 * 鲜叶进厂单号生成器注解：XY + 日期 + 当日自增序号，如 XY20260928-001。
 * 使用方法：{@code @Id @GeneratedValue @IntakeNo private String intakeNo;}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@IdGeneratorType(IntakeNoGenerator.class)
public @interface IntakeNo {
}
