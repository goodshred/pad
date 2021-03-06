package com.github.emailtohl.pad.demo.jpa;

import static org.junit.Assert.assertNotNull;

import javax.persistence.EntityManager;
import javax.sql.DataSource;
import javax.transaction.UserTransaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.emailtohl.pad.entities.oauth2.OauthCode;

public class JpaTest {
	private TransactionManagerSetup TM;
	private String persistenceUnitName;
	private String[] hbmResources;
	private JPASetup JPA;

	@Before
	public void setUp() throws Exception {
		configurePersistenceUnit("com.github.emailtohl.pad.demo.jpa");
		// 设置数据源和事务
		TM = new TransactionManagerSetup(DatabaseProduct.H2);
		// 设置JPA及其实现
		JPA = new JPASetup(TM.databaseProduct, persistenceUnitName, hbmResources);
        // Always drop the schema, cleaning up at least some of the artifacts
        // that might be left over from the last run, if it didn't cleanup
        // properly
        JPA.dropSchema();
        JPA.createSchema();
	}

	@After
	public void tearDown() throws Exception {
		if (JPA != null) {
			if (!"true".equals(System.getProperty("keepSchema"))) {
				JPA.dropSchema();
			}
			JPA.entityManagerFactory.close();
		}
		if (TM != null) {
			TM.stop();
		}
	}

	public void configurePersistenceUnit(String persistenceUnitName, String... hbmResources) throws Exception {
		this.persistenceUnitName = persistenceUnitName;
		this.hbmResources = hbmResources;
	}

	@Test
	public void test() {
		DataSource dataSource = TM.getDataSource();
		assertNotNull(dataSource);
		
		UserTransaction ut = TM.getUserTransaction();
		assertNotNull(ut);
		
		EntityManager em = JPA.entityManagerFactory.createEntityManager();
		OauthCode a = new OauthCode();
		a.setCode("a token");
		a.setAuthentication("a token".getBytes());
		
		em.getTransaction().begin();
		em.persist(a);
		em.getTransaction().commit();
		
		a = em.find(OauthCode.class, a.getCode());
		assertNotNull(a);
	}

}
