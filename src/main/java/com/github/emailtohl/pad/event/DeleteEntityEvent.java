package com.github.emailtohl.pad.event;

import com.github.emailtohl.pad.jpa.EntityBase;
/**
 * 删除实体事件
 * @author HeLei
 */
public class DeleteEntityEvent extends EventBase {
	private static final long serialVersionUID = -634816873583838517L;
	public final EntityBase entity;

	public DeleteEntityEvent(EntityBase entity) {
		this.entity = entity;
	}
}