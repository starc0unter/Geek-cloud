package com.chentsov.common;

import com.chentsov.common.messages.responses.FileMessage;

import java.util.*;

/**
 * @author Evgenii Chentsov
 * <p>
 * A class that contains file parts for the large file.
 */
public class FileParts {

    private int nextPart;                                   //next part to be written on disk;
    private final Map<Integer, FileMessage> parts;          //key: part number; value: message containing data part

    public int getNextPart() {
        return nextPart;
    }

    public FileParts() {
        nextPart = 1;
        parts = new HashMap<>();
    }

    /**
     * Returns the message for the required part. If such part
     * has not been received yet, returns null
     *
     * @return the FileMessage of required file part or null if such part was not received
     */
    public FileMessage removeAndIncrement() {
        FileMessage part = parts.remove(nextPart);
        if (part != null) nextPart++;

        return part;
    }

    public void addPart(FileMessage fm) {
        parts.put(fm.getPart(), fm);
    }

}
