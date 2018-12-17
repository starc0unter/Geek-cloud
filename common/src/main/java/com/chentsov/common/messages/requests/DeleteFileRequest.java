package com.chentsov.common.messages.requests;

import com.chentsov.common.FileItem;
import com.chentsov.common.messages.AbstractMessage;

import java.util.List;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that represents a modify (rename/delete) file request
 */
public class DeleteFileRequest extends AbstractMessage {

    private List<FileItem> items;

    public DeleteFileRequest(List<FileItem> items) {
        this.items = items;
    }

    public List<FileItem> getItems() {
        return items;
    }

}
