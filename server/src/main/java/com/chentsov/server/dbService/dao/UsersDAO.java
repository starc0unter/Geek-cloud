package com.chentsov.server.dbService.dao;

import com.chentsov.server.dbService.dataset.UsersDataSet;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

import java.util.List;

/**
 * @author Evgenii Chentsov
 * <p>
 * A class that represents user auth information
 */
public class UsersDAO implements DAO {

    private Session session;

    public UsersDAO(Session session) {
        this.session = session;
    }

    @Override
    public UsersDataSet get(long id) throws HibernateException {
        return (UsersDataSet) session.get(UsersDataSet.class, id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UsersDataSet> get(String username) throws HibernateException {
        Criteria criteria = session.createCriteria(UsersDataSet.class);

        return (List<UsersDataSet>) criteria
                .add(Restrictions.eq("username", username))
                .list();
    }

    @Override
    public void addUser(String username, String password, String salt) throws HibernateException {
        session.save(new UsersDataSet(username, password, salt));
    }

}
