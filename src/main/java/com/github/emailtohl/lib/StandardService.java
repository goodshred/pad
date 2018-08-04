package com.github.emailtohl.lib;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import com.github.emailtohl.lib.exception.NotAcceptableException;
import com.github.emailtohl.lib.jpa.Paging;

/**
 * 抽象的服务，主要就是增删改查功能。
 * 
 * 标准化参数名、参数类型以及返回后，不仅利于维护，更利于在切面层进行扩展。
 * 
 * @author HeLei
 *
 * @param <E> 实体的类型
 * @param <ID> 实体ID的类型
 * @param <USERID> 非幂等操作时，操作者ID类型
 */
public abstract class StandardService<E, ID, USERID extends Serializable> {
	protected static final Logger LOG = LogManager.getLogger();
	/**
	 * 手动校验
	 */
	protected static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
	protected Validator validator = FACTORY.getValidator();

	/**
	 * 创建一个实体
	 * 
	 * @param entity
	 *            实体对象
	 * @param userId
	 *            修改人，在一些业务中需要
	 * @return 保存好ID的实体对象
	 */
	public abstract E create(E entity, @Nullable USERID userId);

	/**
	 * 根据ID获取实体
	 * 若未找到可抛出java.util.NoSuchElementException或com.github.emailtohl.lib.exception.NotFoundException
	 * 若有冲突，可抛出com.github.emailtohl.lib.exception.ConflictException
	 * 
	 * @param id
	 *            实体的ID
	 * @return 实体对象
	 */
	public abstract E read(ID id);

	/**
	 * 分页查询
	 * 
	 * @param example
	 *            查询参数
	 * @param pageable
	 *            分页排序
	 * @return Paging封装的分页信息，一般JPA底层返回的是Page对象，但该对象不利于JSON等序列化。
	 *         所以在将持久化状态的实体转瞬态时，同时改变分页对象
	 */
	public abstract Paging<E> query(E example, Pageable pageable);

	/**
	 * 查询列表
	 * 
	 * @param example
	 *            查询参数
	 * @return 结果列表
	 */
	public abstract List<E> query(E example);

	/**
	 * 修改实体内容，并指明哪些属性忽略
	 * 若未找到可抛出java.util.NoSuchElementException或com.github.emailtohl.lib.exception.NotFoundException
	 * 
	 * @param id
	 *            实体ID
	 * @param newEntity
	 *            修改的实体对象
	 * @param userId
	 *            修改人，在一些业务中需要
	 * @return 返回null表示没找到该实体
	 */
	public abstract E update(ID id, E newEntity, @Nullable USERID userId);

	/**
	 * 根据ID删除实体
	 * 
	 * @param id
	 *            实体的id
	 * @param userId
	 *            修改人，在一些业务中需要
	 */
	public abstract void delete(ID id, @Nullable USERID userId);

	/**
	 * 屏蔽实体中的敏感信息，如密码；将持久化状态的实体转存到瞬时态的实体对象上以便于调用者序列化 本方法提取简略信息，不做关联查询，主要用于列表中
	 * 
	 * @param entity
	 *            持久化状态的实体对象
	 * @return 瞬时态的实体对象
	 */
	protected abstract E toTransient(E entity);

	/**
	 * 屏蔽实体中的敏感信息，如密码；将持久化状态的实体转存到瞬时态的实体对象上以便于调用者序列化 本方法提取详细信息，做关联查询
	 * 
	 * @param entity
	 *            持久化状态的实体对象
	 * @return 瞬时态的实体对象
	 */
	protected abstract E transientDetail(@Valid E entity);

	/**
	 * 手动校验对象是否符合约束条件
	 * 
	 * @param entity
	 *            被校验的实体对象
	 */
	public void validate(E entity) {
		Set<ConstraintViolation<E>> violations = validator.validate(entity);
		if (violations.size() > 0) {
			violations.forEach(v -> LOG.debug(v));
			throw new NotAcceptableException(new ConstraintViolationException(violations));
		}
	}

	/**
	 * 判断字符串是否存在
	 * 
	 * @param text
	 *            被判断的字符串文本
	 * @return 字符串是否为null或空字符串
	 */
	public boolean hasText(String text) {
		return text != null && !text.isEmpty();
	}

}
