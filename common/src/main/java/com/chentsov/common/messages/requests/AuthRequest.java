package com.chentsov.common.messages.requests;

import com.chentsov.common.messages.AbstractMessage;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that represents authorization request
 */
public final class AuthRequest extends AbstractMessage {

    public final String login;
    public final String password;
    public final boolean isNewUser;

    public AuthRequest(String login, String password, boolean isNewUser) {
        this.login = login;
        this.password = password;
        this.isNewUser = isNewUser;
    }

}
