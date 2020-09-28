package com.chentsov.common.messages.requests;

import com.chentsov.common.FileItem;
import com.chentsov.common.messages.AbstractMessage;

import java.util.Collections;
import java.util.List;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that represents a modify (rename/delete) file request
 */
public final class DeleteFileRequest extends AbstractMessage {

    public final List<FileItem> items;

    public DeleteFileRequest(List<FileItem> items) {
        this.items = Collections.unmodifiableList(items);
    }

}
