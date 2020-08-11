package com.chentsov.common.messages.requests;

import com.chentsov.common.FileItem;
import com.chentsov.common.messages.AbstractMessage;

/**
 * @author Evgenii Chentsov
 */
public class RenameFileRequest extends AbstractMessage {
    public final FileItem item;
    public final String newName;

    public RenameFileRequest(FileItem item, String newName) {
        this.item = item;
        this.newName = newName;
    }

}
