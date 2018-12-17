package com.chentsov.server.dbService.dataset;

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author Evgenii Chentsov
 * <p>
 * A dataset that represent results of the Hibernate query
 */

@Entity
@Table(name = "users")
public class UsersDataSet implements Serializable {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "salt", nullable = false)
    private String salt;

    @SuppressWarnings("UnusedDeclaration")
    public UsersDataSet() {
    }

    public UsersDataSet(String username, String password, String salt) {
        this.id = -1;
        this.username = username;
        this.password = password;
        this.salt = salt;
    }

    @SuppressWarnings("unused")
    public long getId() {
        return id;
    }

    @SuppressWarnings("unused")
    public void setId(long id) {
        this.id = id;
    }

    @SuppressWarnings("unused")
    public String getUsername() {
        return username;
    }

    @SuppressWarnings("unused")
    public void setUsername(String username) {
        this.username = username;
    }

    @SuppressWarnings("unused")
    public String getPassword() {
        return password;
    }

    @SuppressWarnings("unused")
    public void setPassword(String password) {
        this.password = password;
    }

    @SuppressWarnings("unused")
    public String getSalt() {
        return salt;
    }

    @SuppressWarnings("unused")
    public void setSalt(String salt) {
        this.salt = salt;
    }

}
