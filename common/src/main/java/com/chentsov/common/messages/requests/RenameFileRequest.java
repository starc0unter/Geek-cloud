package com.chentsov.common.messages.requests;

import com.chentsov.common.FileItem;
import com.chentsov.common.messages.AbstractMessage;

/**
 * @author Evgenii Chentsov
 */
public class RenameFileRequest extends AbstractMessage {

    private FileItem item;
    private String newName;

    public RenameFileRequest(FileItem item, String newName) {
        this.item = item;
        this.newName = newName;
    }

    public FileItem getItem() {
        return item;
    }

    public String getNewName() {
        return newName;
    }

}
