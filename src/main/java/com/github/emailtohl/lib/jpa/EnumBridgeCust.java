package com.github.emailtohl.lib.jpa;

import org.hibernate.search.bridge.builtin.EnumBridge;

/**
 * 对枚举的搜索，原EnumBridge未对类型进行判断，导致程序错误，故覆盖原EnumBridge
 * 使用方法：@Field(bridge = @FieldBridge(impl = EnumBridgeCust.class))
 * 
 * @author HeLei
 */
public class EnumBridgeCust extends EnumBridge {
	@Override
	public String objectToString(Object object) {
		if (object == null) {
			return "";
		}
		if (object instanceof Enum) {
			@SuppressWarnings("rawtypes")
			Enum e = (Enum) object;
			return e != null ? e.name() : null;
		}
		return object.toString();
	}
}
