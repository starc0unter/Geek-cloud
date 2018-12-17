package com.chentsov.server.dbService.dao;

import com.chentsov.server.dbService.dataset.UsersDataSet;
import org.hibernate.HibernateException;

import java.util.List;

/**
 * @author Evgenii Chentsov
 * <p>
 * An interface that represents data access object.
 */
public interface DAO {

    @SuppressWarnings("unused")
    UsersDataSet get(long id) throws HibernateException;

    List<UsersDataSet> get(String username) throws HibernateException;

    void addUser(String username, String password, String salt) throws HibernateException;

}
