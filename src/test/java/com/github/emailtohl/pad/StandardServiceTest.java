package com.github.emailtohl.pad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.Valid;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.github.emailtohl.pad.StandardService;
import com.github.emailtohl.pad.config.TestEnvironment;
import com.github.emailtohl.pad.exception.NotAcceptableException;
import com.github.emailtohl.pad.jpa.Paging;
import com.github.emailtohl.pad.model.Item;

public class StandardServiceTest extends TestEnvironment {
	@Autowired
	private ItemTestService itemTestService;

	@Before
	public void setUp() throws Exception {
		
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test(expected = NotAcceptableException.class)
	public void testValidate() {
		Item i = new Item();
		itemTestService.validate(i);
	}

	@Test
	public void testHasText() {
		assertFalse(itemTestService.hasText(null));
		assertFalse(itemTestService.hasText(""));
		assertTrue(itemTestService.hasText("abc"));
	}
	
	@Test
	public void testTrimStringProperty() {
		Foo f = new Foo();
		Bar b = new Bar();
		b.foo = f;
		f.bar = b;
		f.setA(1);
		f.setB("  123   ");
		f.setC(null);
		f.getE().add(" 456   ");
		f.setF(new Date());
		f.setH(new Foo());
		itemTestService.trimStringProperty(f);
		
		assertEquals("123", f.getB());
		assertEquals("456", f.getE().iterator().next());
	}
	
	@Test
	public void testWildcardStringProperty() {
		Foo f = new Foo();
		Bar b = new Bar();
		b.foo = f;
		f.bar = b;
		f.setA(1);
		f.setB("  123   ");
		f.setC(null);
		f.getE().add(" 456   ");
		f.setF(new Date());
		f.setH(new Foo());
		itemTestService.wildcardStringProperty(f);
		
		assertEquals("123%", f.getB());
		assertEquals("456%", f.getE().iterator().next());
	}
	
	@Test
	public void testClone() {
		Foo f = new Foo();
		Bar b = new Bar();
		b.foo = f;
		f.bar = b;
		f.setA(1);
		f.setB("  123   ");
		f.setC(null);
		f.getE().add(" 456   ");
		f.setF(new Date());
		f.setH(new Foo());
		
		Bar copy = itemTestService.clone(b);
		assertEquals(copy.foo.a, b.foo.a);
	}
	
}

class Foo implements Serializable {
	private static final long serialVersionUID = -209239358724280869L;
	int a;
	String b;
	String c;
	Integer d;
	Set<String> e = new HashSet<>();
	Date f;
	Set<Foo> g = new HashSet<>();
	Foo h;
	Bar bar;
	public int getA() {
		return a;
	}
	public void setA(int a) {
		this.a = a;
	}
	public String getB() {
		return b;
	}
	public void setB(String b) {
		this.b = b;
	}
	public String getC() {
		return c;
	}
	public void setC(String c) {
		this.c = c;
	}
	public Integer getD() {
		return d;
	}
	public void setD(Integer d) {
		this.d = d;
	}
	public Set<String> getE() {
		return e;
	}
	public void setE(Set<String> e) {
		this.e = e;
	}
	public Date getF() {
		return f;
	}
	public void setF(Date f) {
		this.f = f;
	}
	public Set<Foo> getG() {
		return g;
	}
	public void setG(Set<Foo> g) {
		this.g = g;
	}
	public Foo getH() {
		return h;
	}
	public void setH(Foo h) {
		this.h = h;
	}
}

class Bar implements Serializable {
	private static final long serialVersionUID = 8935792912828539080L;
	Foo foo;
}

@Service
class ItemTestService extends StandardService<Item, Long> {

	@Override
	public Item create(@Valid Item entity) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Item read(Long id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Paging<Item> query(Item example, Pageable pageable) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Item> query(Item example) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Item update(Long id, @Valid Item newEntity) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void delete(Long id) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected Item toTransient(Item entity) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected Item transientDetail(Item entity) {
		throw new UnsupportedOperationException();
	}
}
