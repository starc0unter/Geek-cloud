package com.chentsov.server.dbService;

import com.chentsov.server.dbService.dao.DAO;
import com.chentsov.server.dbService.dao.UsersDAO;
import com.chentsov.server.dbService.dataset.UsersDataSet;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import java.util.List;

/**
 * @author Evgenii Chentsov
 * <p>
 * A class that represents the main database service
 */
public class DBService {

    private static final String hibernate_show_sql = "true";
    private static final String hibernate_hbm2ddl_auto = "update";

    private final SessionFactory sessionFactory;                // Hibernate session factory
    private static DBService dbService;

    /**
     * A singleton instance of DBService
     */
    private DBService() {
        Configuration configuration = getH2Configuration();
        sessionFactory = createSessionFactory(configuration);
    }

    public static synchronized DBService getInstance() {
        if (dbService == null) dbService = new DBService();
        return dbService;
    }

    /**
     * Creates Hibernate configuration
     *
     * @return prepared configuration
     */
    private Configuration getH2Configuration() {
        Configuration configuration = new Configuration();
        configuration.addAnnotatedClass(UsersDataSet.class);

        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:./h2db");
        configuration.setProperty("hibernate.connection.username", "starcounter");
        configuration.setProperty("hibernate.connection.password", "starcounter");
        configuration.setProperty("hibernate.show_sql", hibernate_show_sql);
        configuration.setProperty("hibernate.hbm2ddl.auto", hibernate_hbm2ddl_auto);
        return configuration;
    }

    /**
     * Creates SessionFactory to produce Hibernate sessions
     *
     * @param configuration prepared Hibernate configuration
     * @return prepared SessionFactory
     */
    private static SessionFactory createSessionFactory(Configuration configuration) {
        StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder();
        builder.applySettings(configuration.getProperties());
        ServiceRegistry serviceRegistry = builder.build();
        return configuration.buildSessionFactory(serviceRegistry);
    }

    /**
     * gets a response from DB about account according to proposed username
     *
     * @param username a username to get user info from DB
     * @return a list of users
     */
    public List<UsersDataSet> get(String username) {
        try {
            Session session = sessionFactory.openSession();
            DAO dao = new UsersDAO(session);
            List<UsersDataSet> dataSet = dao.get(username);
            session.close();
            return dataSet;
        } catch (HibernateException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Adds a new account into DB
     *
     * @param username a username
     * @param password a password
     * @return true if account creation is successful and false otherwise
     */
    public boolean addUser(String username, String password, String salt) {
        if (hasUser(username)) return false;
        try {
            Session session = sessionFactory.openSession();
            Transaction transaction = session.beginTransaction();
            DAO dao = new UsersDAO(session);
            dao.addUser(username, password, salt);
            transaction.commit();
            session.close();
            return true;
        } catch (HibernateException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Checks if account with given username already exists
     *
     * @param username a username
     * @return true if account already exists and false otherwise
     */
    public boolean hasUser(String username) {
        List<UsersDataSet> uds = get(username);
        return uds.size() > 0;
    }

    public void close() {
        sessionFactory.close();
    }

}
