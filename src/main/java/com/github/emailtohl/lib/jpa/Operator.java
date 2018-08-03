package com.github.emailtohl.lib.jpa;

/**
 * 操作类型，以下操作符在被注解的属性的值不为null时生效EQ, NEQ, LT, LTE, GT, GTE, LIKE, NOT_LIKE, IN
 * 而NULL, NOT_NULL虽然不需要使用属性的值，但是只有存储任意值后才生效
 * @author HeLei
 */
public enum Operator {
	EQ, NEQ, LT, LTE, GT, GTE, LIKE, NOT_LIKE, IN, NULL, NOT_NULL
}