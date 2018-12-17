package com.chentsov.common.messages.requests;

import com.chentsov.common.messages.AbstractMessage;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that represents authorization request
 */
public class AuthRequest extends AbstractMessage {

    private String login;
    private String password;
    private boolean isNewUser;

    public AuthRequest(String login, String password, boolean isNewUser) {
        this.login = login;
        this.password = password;
        this.isNewUser = isNewUser;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public boolean isNewUser() {
        return isNewUser;
    }

}
