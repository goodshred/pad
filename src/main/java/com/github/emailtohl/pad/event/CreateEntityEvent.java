package com.github.emailtohl.pad.event;

import com.github.emailtohl.pad.jpa.EntityBase;
/**
 * 创建实体事件
 * @author HeLei
 */
public class CreateEntityEvent extends EventBase {
	private static final long serialVersionUID = 4477445467998909245L;
	public final EntityBase entity;

	public CreateEntityEvent(EntityBase entity) {
		this.entity = entity;
	}
}
