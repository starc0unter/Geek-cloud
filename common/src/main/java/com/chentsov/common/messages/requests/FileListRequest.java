package com.chentsov.common.messages.requests;

import com.chentsov.common.messages.AbstractMessage;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that represents cloud file list request.
 */
public class FileListRequest extends AbstractMessage {
    private String currentCloudPath;

    public String getCurrentCloudPath() {
        return currentCloudPath;
    }

    public FileListRequest(String currentCloudPath) {
        this.currentCloudPath = currentCloudPath;
    }

}
