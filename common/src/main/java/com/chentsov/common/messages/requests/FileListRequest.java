package com.chentsov.common.messages.requests;

import com.chentsov.common.messages.AbstractMessage;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that represents cloud file list request.
 */
public final class FileListRequest extends AbstractMessage {
    public final String currentCloudPath;

    public FileListRequest(String currentCloudPath) {
        this.currentCloudPath = currentCloudPath;
    }

}
