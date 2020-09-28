package com.chentsov.common;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A class that wraps a String file path.
 */
public class FileItem implements Serializable {

    private String name;
    private long size;
    private Date date;
    private String stringPath;
    private boolean isRootDir = false;
    private boolean isParentDir = false;
    private final boolean isDir;

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public Date getDate() {
        return date;
    }

    public String getStringPath() {
        return stringPath;
    }

    public File getFile() {
        return new File(stringPath);
    }

    public Path getPath() {
        return Paths.get(stringPath);
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRootDir() {
        return isRootDir;
    }

    public boolean isParentDir() {
        return isParentDir;
    }

    public boolean isDir() {
        return isDir;
    }

    private FileItem(String stringPath) {
        this.stringPath = stringPath;
        Path path = Paths.get(stringPath);
        isDir = Files.isDirectory(path);
        name = path.getFileName().toString();
        try {
            size = Files.size(path);
            date = new Date(Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private FileItem(String stringPath, boolean isRootDir) {
        this(stringPath);
        this.isRootDir = isRootDir;
        this.isParentDir = true;
        if (isRootDir) name = "...";
    }

    /**
     * Renames a file.
     *
     * @param newName a new file name
     */
    public void rename(String newName) {
        Path path = Paths.get(stringPath);
        Path newPath = Paths.get(path.getParent().toString(), newName);
        try {
            Files.move(path, newPath, StandardCopyOption.REPLACE_EXISTING);
            stringPath = newPath.toString();
            name = newName;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes files for selected storage
     */
    public void remove() {
        try {
            Files.walkFileTree(getPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        //some references may prevent directories from disappearing is OS so we collect garbage here
        System.gc();
    }

    /**
     * Refreshes file list and puts it into given List
     *
     * @param files      a list that stores paths to files
     * @param stringPath a stringPath to search
     */
    public static void refreshFileList(List<FileItem> files, String stringPath, boolean isRoot) {
        files.clear();
        try {
            if (!isRoot) files.add(new FileItem(new File(stringPath).getParent(), true));
            files.addAll(Files
                    .list(Paths.get(stringPath))
                    .filter(Files::exists)
                    .map(Path::toString)
                    .map(FileItem::new)
                    .collect(Collectors.toList()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
