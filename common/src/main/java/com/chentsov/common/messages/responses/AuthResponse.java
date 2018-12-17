package com.chentsov.common.messages.responses;

import com.chentsov.common.messages.AbstractMessage;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that represents an auth (rename/delete) response from the server
 */
public class AuthResponse extends AbstractMessage {

    private boolean success;
    private String pathToStorage;

    public AuthResponse(boolean success) {
        this.success = success;
    }

    public AuthResponse(boolean success, String pathToStorage) {
        this.success = success;
        this.pathToStorage = pathToStorage;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getPathToStorage() {
        return pathToStorage;
    }
}
