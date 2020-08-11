package com.chentsov.common.messages.requests;

import com.chentsov.common.messages.AbstractMessage;

import java.util.Collections;
import java.util.List;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that represents a cloud file request
 */
public final class FileRequest extends AbstractMessage {

    public final String destinationPath;
    private final List<String> stringPaths;

    public List<String> getStringPaths() {
        return stringPaths;
    }

    public String getDestinationPath() {
        return destinationPath;
    }

    public FileRequest(List<String> stringPaths, String destinationPath) {
        this.stringPaths = Collections.unmodifiableList(stringPaths);
        this.destinationPath = destinationPath;
    }

}
