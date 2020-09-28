package com.chentsov.common.messages.responses;

import com.chentsov.common.messages.AbstractMessage;
import com.sun.deploy.util.StringUtils;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that represents an auth (rename/delete) response from the server
 */
public final class AuthResponse extends AbstractMessage {

    public final boolean success;
    public final String pathToStorage;

    public AuthResponse(boolean success) {
        this.success = success;
        pathToStorage = "";
    }

    public AuthResponse(boolean success, String pathToStorage) {
        this.success = success;
        this.pathToStorage = pathToStorage;
    }
}
