package com.chentsov.common.messages.responses;

import com.chentsov.common.FileItem;
import com.chentsov.common.messages.AbstractMessage;

import java.util.List;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that represents response from the server containing cloud file list
 */
public class FileListResponse extends AbstractMessage {

    private List<FileItem> cloudFilesList;

    public FileListResponse(List<FileItem> cloudFilesList) {
        this.cloudFilesList = cloudFilesList;
    }

    public List<FileItem> getCloudFilesList() {
        return cloudFilesList;
    }

}
