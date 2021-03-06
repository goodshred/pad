package com.github.emailtohl.pad.jpa;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.EmbeddedId;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaBuilder.In;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.query.QueryUtils;

/**
 * 根据查询对象example，组装成CriteriaQuery，并进行查询
 * 
 * @author HeLei
 */
public abstract class QueryRepository<E, ID extends Serializable> extends EntityRepository<E, ID>
		implements QueryInterface<E, ID> {
	private static final ConcurrentHashMap<Class<?>, EntityProperty[]> PROP_CACHE = new ConcurrentHashMap<Class<?>, EntityProperty[]>();
	private static final ConcurrentHashMap<Class<?>, Condition[]> CONDITION_CACHE = new ConcurrentHashMap<Class<?>, Condition[]>();
	private static final Set<Class<?>> PRIMITIVES = new HashSet<Class<?>>(Arrays.asList(int.class, long.class,
			double.class, float.class, short.class, boolean.class, byte.class, char.class));

	public QueryRepository() {
	}

	public QueryRepository(Class<E> entityClass, Class<ID> idClass) {
		super(entityClass, idClass);
	}

	/**
	 * 根据参数对象分页查询
	 * 
	 * @param example 参数对象
	 * @param pageable 分页排序信息
	 * @return 结果列表
	 */
	public Page<E> queryForPage(E example, Pageable pageable) {
		CriteriaBuilder b = entityManager.getCriteriaBuilder();
		
		CriteriaQuery<Long> c = b.createQuery(Long.class);
		Root<E> r = c.from(entityClass);
		c = c.select(b.count(r)).distinct(true);
		if (example != null) {
			Set<Predicate> predicates = getPredicates(example, r, b);
			if (predicates.size() > 0) {
				Predicate[] restrictions = new Predicate[predicates.size()];
				c = c.where(predicates.toArray(restrictions));
			}
		}
		Long total = entityManager.createQuery(c).getSingleResult();
		if (total == null || total == 0) {
			return new PageImpl<E>(new ArrayList<E>(), pageable, 0);
		}
		
		CriteriaQuery<E> q = b.createQuery(entityClass);
		r = q.from(entityClass);
		q = q.select(r).distinct(true);
		if (example != null) {
			Set<Predicate> predicates = getPredicates(example, r, b);
			if (predicates.size() > 0) {
				Predicate[] restrictions = new Predicate[predicates.size()];
				q = q.where(predicates.toArray(restrictions));
			}
		}
		q = q.orderBy(QueryUtils.toOrders(pageable.getSort(), r, b));
		List<E> result = entityManager.createQuery(q).setFirstResult((int) pageable.getOffset())
				.setMaxResults(pageable.getPageSize()).getResultList();

		return new PageImpl<E>(result, pageable, total);
	}

	/**
	 * 根据参数对象查询列表
	 * 
	 * @param example 参数对象
	 * @return 结果列表
	 */
	public List<E> queryForList(E example) {
		CriteriaBuilder b = entityManager.getCriteriaBuilder();
		CriteriaQuery<E> q = b.createQuery(entityClass);
		Root<E> r = q.from(entityClass);
		q = q.select(r).distinct(true);
		if (example != null) {
			Set<Predicate> set = getPredicates(example, r, b);
			if (set.size() > 0) {
				Predicate[] restrictions = new Predicate[set.size()];
				q = q.where(set.toArray(restrictions));
			}
		}
		return entityManager.createQuery(q).getResultList();
	}

	/**
	 * 分析参数对象，最后返回一个AND关系的谓词集合
	 * 
	 * @param example 参数对象
	 * @param root 实体的根
	 * @param cb 标准查询构造器
	 * @return AND关系的谓词集合
	 */
	protected Set<Predicate> getPredicates(Object example, Root<?> root, CriteriaBuilder cb) {
		Set<Predicate> predicates = new HashSet<Predicate>();
		StringBuilder trace = new StringBuilder();
		// hack内部类不能修改外部域的编译问题
		boolean[] first = { true };

		// 使用内部类避免中间状态变量受多线程影响
		class Closure {
			private static final String IS_NULL = "IS NULL";
			private static final String IS_NOT_NULL = "IS NOT NULL";
			private static final String EMPTY = "IS EMPTY";
			private static final String NOT_EMPTY = "IS NOT EMPTY";
			private static final String MEMBER_OF = "MEMBER OF";
			private final Set<Object> used = new HashSet<Object>();

			@SuppressWarnings({ "unchecked", "rawtypes" })
			void exec(Object o, Path<?> prefix, String parentPath) {
				// 若遇到相互关联的情况，则终止递归
				if (used.contains(o)) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("{} is recycled use", o.getClass().getSimpleName());
					}
					return;
				}
				used.add(o);
				// 先分析实体属性
				for (EntityProperty prop : getEntityProperties(o.getClass())) {
					// 排除不作为查询条件的属性
					if (prop.getAnnotation(ExcludeCondition.class) != null) {
						continue;
					}
					if (prop.getAnnotation(Instruction.class) != null) {
						continue; // 在特殊比较中处理
					}
					Object value = prop.getValue(o);
					if (value == null) {
						continue;
					}
					// 若没有指明初始化值也作为查询条件的话，那么默认该忽略
					// 接着判断是否基本类型，若是基本类型且值是初始化值，那么就不作为查询条件
					if (prop.getAnnotation(ZeroCondition.class) == null && isZeroValue(prop.getType(), value)) {
						continue;
					}
					// 先区分是否集合属性
					if (value instanceof Collection) {// 如果是集合类型
						Collection<Object> values = (Collection<Object>) value;
						if (values.isEmpty()) {
							continue;
						}
						ElementCollection elementCollection = prop.getAnnotation(ElementCollection.class);
						OneToMany oneToMany = prop.getAnnotation(OneToMany.class);
						ManyToMany manyToMany = prop.getAnnotation(ManyToMany.class);
						// 对集合处理的JPQL样例：SELECT c FROM Category c WHERE :item MEMBER OF c.items
						// items是Category的集合属性，查询Category时，谓词条件是：参数item是Category#items的一员，可用isMember:
						// cb.isMember(v, path)
						// 此处查询参数v只考虑值类型，如果v是实体类型的话，在查询前，还需先将其加载为持久化状态，不仅复杂而且影响性能
						if (elementCollection != null && isValueTypeCollection(values)) {
							Path<Collection<Object>> path = prefix.get(prop.name);
							for (Object v : values) {
								predicates.add(cb.isMember(v, path));
								log(parentPath, prop.name, MEMBER_OF, v);
							}
						} else if (prefix == root// 下面考虑v是实体类型的情况，这里采用左外连接来查询，前提条件是Join只在root层有效，用==进行严格判断
								&& (elementCollection != null || oneToMany != null || manyToMany != null)) {
							// 连接查询默认使用INNER JOIN，这会过滤掉两表未关联的项
							// 所以在这种对多的关系中，一定要用左连接：SELECT c FROM Category c LEFT JOIN c.items WHERE ……
							Join<?, Collection> join = root.join(prop.name, JoinType.LEFT);
							for (Object component : values) {
								exec(component, join, prop.name);
							}
						}
					} else {// 若不是集合
						if (EntityInspector.isValueType(prop.getType())
								|| prop.getAnnotation(EmbeddedId.class) != null) {// 如果是值类型或id
							Path<?> path = prefix.get(prop.name);
							// 不是id的字符串类型，就用“LIKE”
							if (value instanceof String && prop.getAnnotation(Id.class) == null) {
								// 模糊查询的“%”由参数提供，这里不自动添加
								String _value = ((String) value).trim().toLowerCase();
								predicates.add(cb.like(cb.lower((Path<String>) path), _value));
								log(parentPath, prop.name, "LIKE", _value);
							} else {
								predicates.add(cb.equal(path, value));
								log(parentPath, prop.name, "=", value);
							}
						} else {// 否则是实体类型
							ManyToOne manyToOne = prop.getAnnotation(ManyToOne.class);
							OneToOne oneToOne = prop.getAnnotation(OneToOne.class);
							Embedded embedded = prop.getAnnotation(Embedded.class);
							if (manyToOne != null || oneToOne != null || embedded != null) {
								Path<?> path = prefix.get(prop.name);
								exec(value, path, parentPath + '.' + prop.name);
							}
						}
					}
				}
				// 再分析自定义条件（被@Instruction注解的属性）
				// 既然是指定比较，那一定是属性的值为值对象时才有效，否则若注解在属性为关联实体上则没有意义
				for (Condition condition : getConditions(o.getClass())) {
					// 排除不作为查询条件的属性
					if (condition.getAnnotation(ExcludeCondition.class) != null) {
						continue;
					}
					Object value = condition.getValue(o);
					if (value == null) {
						continue;
					}
					// 若没有指明初始化值也作为查询条件的话，那么默认该忽略
					// 接着判断是否基本类型，若是基本类型且值是初始化值，那么就不作为查询条件
					if (condition.getAnnotation(ZeroCondition.class) == null
							&& isZeroValue(condition.getType(), value)) {
						continue;
					}
					Path<?> path = prefix.get(condition.propertyName);
					switch (condition.operator) {
					case EQ:
						if (EntityInspector.isValueType(condition.getType())) {
							predicates.add(cb.equal(path, value));
							log(parentPath, condition.propertyName, "=", value);
						}
						break;
					case NEQ:
						if (EntityInspector.isValueType(condition.getType())) {
							predicates.add(cb.notEqual(path, value));
							log(parentPath, condition.propertyName, "<>", value);
						}
						break;
					case LIKE:
						if (EntityInspector.isValueType(condition.getType()) && value instanceof String) {
							predicates
									.add(cb.like(cb.lower((Path<String>) path), ((String) value).trim().toLowerCase()));
							log(parentPath, condition.propertyName, "LIKE", value);
						}
						break;
					case NOT_LIKE:
						if (EntityInspector.isValueType(condition.getType()) && value instanceof String) {
							predicates.add(
									cb.notLike(cb.lower((Path<String>) path), ((String) value).trim().toLowerCase()));
							log(parentPath, condition.propertyName, "NOT LIKE", value);
						}
						break;
					case GT:
						if (EntityInspector.isValueType(condition.getType()) && value instanceof Comparable) {
							Comparable _value = (Comparable) value;
							Path<Comparable> _path = (Path<Comparable>) path;
							predicates.add(cb.greaterThan(_path, _value));
							log(parentPath, condition.propertyName, ">", _value);
						}
						break;
					case GTE:
						if (EntityInspector.isValueType(condition.getType()) && value instanceof Comparable) {
							Comparable _value = (Comparable) value;
							Path<Comparable> _path = (Path<Comparable>) path;
							predicates.add(cb.greaterThanOrEqualTo(_path, _value));
							log(parentPath, condition.propertyName, ">=", _value);
						}
						break;
					case LT:
						if (EntityInspector.isValueType(condition.getType()) && value instanceof Comparable) {
							Comparable _value = (Comparable) value;
							Path<Comparable> _path = (Path<Comparable>) path;
							predicates.add(cb.lessThan(_path, _value));
							log(parentPath, condition.propertyName, "<", _value);
						}
						break;
					case LTE:
						if (EntityInspector.isValueType(condition.getType()) && value instanceof Comparable) {
							Comparable _value = (Comparable) value;
							Path<Comparable> _path = (Path<Comparable>) path;
							predicates.add(cb.lessThanOrEqualTo(_path, _value));
							log(parentPath, condition.propertyName, "<=", _value);
						}
						break;
					case IN:
						// 这里只考虑值类型，若是实体类型还需要先加载其持久态实例
						if (isValueTypeCollection(value)) {
							In<Object> in = cb.in(path);
							Collection<?> values = toCollection(value);
							for (Object v : values) {
								in = in.value(v);
							}
							predicates.add(in);
							log(parentPath, condition.propertyName, "IN", values);
						}
						break;
					case EMPTY:
						predicates.add(cb.isEmpty((Path<Collection<?>>) path));
						log(parentPath, condition.propertyName, EMPTY, "");
						break;
					case NOT_EMPTY:
						predicates.add(cb.isNotEmpty((Path<Collection<?>>) path));
						log(parentPath, condition.propertyName, NOT_EMPTY, "");
						break;
					case NULL:
						predicates.add(path.isNull());
						log(parentPath, condition.propertyName, IS_NULL, "");
						break;
					case NOT_NULL:
						predicates.add(path.isNotNull());
						log(parentPath, condition.propertyName, IS_NOT_NULL, "");
						break;
					default:
						break;
					}
				}
			}

			void log(String parentPath, String propertyName, String operator, Object o) {
				if (!LOG.isDebugEnabled()) {
					return;
				}
				if (first[0]) {
					first[0] = false;
				} else {
					trace.append(" AND ");
				}
				if (MEMBER_OF.equals(operator)) {
					if (o instanceof String) {
						trace.append('\'').append(o).append('\'');
					} else {
						trace.append(o);
					}
					trace.append(' ').append(operator).append(' ').append(parentPath).append('.').append(propertyName);
				} else {
					trace.append(parentPath).append('.').append(propertyName).append(' ').append(operator).append(' ');
					if (o instanceof String && !(IS_NULL.equals(operator) || IS_NOT_NULL.equals(operator)
							|| EMPTY.equals(operator) || NOT_EMPTY.equals(operator))) {
						trace.append('\'').append(o).append('\'');
					} else {
						trace.append(o);
					}
				}
			}
		}
		new Closure().exec(example, root, entityClass.getSimpleName());
		if (LOG.isDebugEnabled()) {
			LOG.debug(trace.toString());
		}
		return predicates;
	}

	/**
	 * 从缓存中获取实体的所有属性
	 * 
	 * @param clazz 从该class中分析出实体属性
	 * @return 实体属性集合
	 */
	EntityProperty[] getEntityProperties(Class<?> clazz) {
		EntityProperty[] properties = PROP_CACHE.get(clazz);
		if (properties != null) {
			return properties;
		}
		synchronized (PROP_CACHE) {
			properties = PROP_CACHE.get(clazz);
			if (properties != null) {
				return properties;
			}
			Set<EntityProperty> sproperties = EntityInspector.getEntityProperty(clazz);
			properties = sproperties.toArray(new EntityProperty[sproperties.size()]);
			PROP_CACHE.put(clazz, properties);
		}
		return properties;
	}

	/**
	 * 从缓存中获取自定义条件
	 * 
	 * @param clazz 从该class中分析出自定义条件比较的信息
	 * @return 条件比较信息的集合
	 */
	Condition[] getConditions(Class<?> clazz) {
		Condition[] conditions = CONDITION_CACHE.get(clazz);
		if (conditions != null) {
			return conditions;
		}
		synchronized (CONDITION_CACHE) {
			conditions = CONDITION_CACHE.get(clazz);
			if (conditions != null) {
				return conditions;
			}
			Set<Condition> sconditions = EntityInspector.getConditions(clazz);
			conditions = sconditions.toArray(new Condition[sconditions.size()]);
			CONDITION_CACHE.put(clazz, conditions);
		}
		return conditions;
	}

	/**
	 * 判断集合中含的元素是否值对象
	 * 
	 * @param collection 集合或者数组
	 * @return 若集合或数组中的元素是值对象，则返回true，否则为false
	 */
	boolean isValueTypeCollection(Object collection) {
		if (collection instanceof Collection && ((Collection<?>) collection).size() > 0) {
			for (Object o : (Collection<?>) collection) {
				if (o == null || !EntityInspector.isValueType(o.getClass())) {
					return false;
				}
			}
			return true;
		} else if (collection.getClass().isArray() && Array.getLength(collection) > 0) {
			for (int i = 0; i < Array.getLength(collection); i++) {
				Object o = Array.get(collection, i);
				if (o == null || !EntityInspector.isValueType(o.getClass())) {
					return false;
				}
			}
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 判断是否基本类型的零值
	 * 
	 * @param type class类型
	 * @param value 值
	 * @return 若class类型为基本类型，且值是初始值，则返回true，否则为false
	 */
	boolean isZeroValue(Class<?> type, Object value) {
		// 若本身不是基本类型，那么返回false表示循环中不执行“continue”
		if (!PRIMITIVES.contains(type)) {
			return false;
		}
		if (type == int.class) {
			if ((int) value == 0)
				return true;
			else
				return false;
		} else if (type == long.class) {
			if ((long) value == 0L)
				return true;
			else
				return false;
		} else if (type == double.class) {
			if (Math.abs((double) value - 0.0) < 0.00001)
				return true;
			else
				return false;
		} else if (type == float.class) {
			if (Math.abs((float) value - 0.0) < 0.001)
				return true;
			else
				return false;
		} else if (type == short.class) {
			if ((short) value == 0)
				return true;
			else
				return false;
		} else if (type == boolean.class) {
			if (!((boolean) value))
				return true;
			else
				return false;
		} else if (type == byte.class) {
			if ((byte) value == (byte) 0)
				return true;
			else
				return false;
		} else if (type == char.class) {
			if ((char) value == (char) 0)
				return true;
			else
				return false;
		}
		return false;
	}

	/**
	 * 若本身是集合则直接返回，否则将数组转成集合形式
	 * 
	 * @param value 集合或数组
	 * @return 集合
	 */
	Collection<?> toCollection(Object value) {
		if (value instanceof Collection) {
			return (Collection<?>) value;
		} else if (value.getClass().isArray()) {
			int length = Array.getLength(value);
			List<Object> ls = new ArrayList<Object>(length);
			for (int i = 0; i < length; i++) {
				ls.add(Array.get(value, i));
			}
			return ls;
		}
		throw new IllegalArgumentException(value + " Parameters are neither collections nor arrays");
	}

}