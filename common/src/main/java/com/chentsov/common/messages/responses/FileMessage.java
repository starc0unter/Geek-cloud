package com.chentsov.common.messages.responses;

import com.chentsov.common.FileParts;
import com.chentsov.common.messages.AbstractMessage;
import javafx.scene.control.ProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author Evgenii Chentsov
 * <p>
 * A message that contains file data and relevant information anout the file
 */
public class FileMessage extends AbstractMessage {

    private static final Logger logger = LogManager.getLogger(FileMessage.class.getSimpleName());
    private static final int MAX_FILE_SIZE = 1024 * 1024 * 5; // 5 mb

    //final destination path is destination + relativePath
    private String destination;
    private String relativePath;
    private byte[] data;
    //in case of several parts starts from 1 to (totalParts)
    private int part;
    //in case of several parts shows total amount of file parts
    private int totalParts;

    /**
     * Creates a FileMessage instance
     *
     * @param relativePath path to file relative to the source directory
     * @param destination  path to the target folder (where files will be stored).
     * @param totalParts   amount of parts related to concrete file
     */
    private FileMessage(String relativePath, String destination, int totalParts) {
        this.destination = destination;
        this.relativePath = relativePath;
        this.totalParts = totalParts;
    }

    private void setDataPart(byte[] partData, int dataPartNumber) {
        data = partData;
        part = dataPartNumber;
    }

    public String getDestination() {
        return destination;
    }

    public byte[] getData() {
        return data;
    }

    public int getPart() {
        return part;
    }

    public static int getMaxFileSize() {
        return MAX_FILE_SIZE;
    }

    private int getTotalParts() {
        return totalParts;
    }

    private String getRelativePath() {
        return relativePath;
    }


    /**
     * Sends a file to storage. If the file is large, it is divided into several separate messages
     *
     * @param filePaths a list of files to be sent
     * @param consumer  a consumer to process prepared FileMessage
     */
    public static synchronized void send(List<Path> filePaths, String source, String destination,
                                         Consumer<FileMessage> consumer, ProgressBar progressBar) {
        try {
            int allFilesRequired = getAllFilesPartsCount(filePaths);
            int allFilesCompleted = 0;
            //if several files are selected, create a FileMessage for each one
            for (Path filePath : filePaths) {
                if (Files.isDirectory(filePath) && checkPathIsNotEmptyDir(filePath)) continue;
                String relativePath = filePath.toString().substring(source.length());

                logger.info("Sending " + filePath.toString());

                if (Files.isDirectory(filePath)) {
                    FileMessage fm = new FileMessage(relativePath, destination, 1);
                    fm.setDataPart(new byte[0], 1);
                    consumer.accept(fm);
                    logger.info("Sent empty folder: " + filePath.toString());
                    continue;
                }

                final long actualFileSize = Files.size(filePath);
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filePath.toString()))) {
                    boolean strictlyEqual = (int) actualFileSize % MAX_FILE_SIZE == 0;
                    int totalParts = (int) actualFileSize / MAX_FILE_SIZE;
                    if (!strictlyEqual) totalParts++;
                    //selecting initial data array size
                    byte[] part = totalParts > 1 ? new byte[MAX_FILE_SIZE] : new byte[(int) actualFileSize];
                    int bytesRead;
                    int currentPart = 1;

                    /*
                    !!!PLEASE READ THIS CAREFULLY!!!
                    A tricky part!
                    For the small file (1 part) we utilize a single part[] array. If there are more parts, we apply a method
                    that does not send garbage data over network. Let us suppose that a file contains of N parts. Then, we send
                    (N-1) parts with the array length of [maxFileSize], and the last part is truncated so no garbage is
                    sent. The price is not that big - just a single creation of a new byte array for the final message.
                     */
                    FileMessage fm = new FileMessage(relativePath, destination, totalParts);
                    while ((bytesRead = bis.read(part)) > 0) {
                        //preparing final data part
                        if (bytesRead < part.length) part = Arrays.copyOfRange(part, 0, bytesRead);
                        fm.setDataPart(part, currentPart);
                        logger.info("Sending data part N " + fm.getPart() + " of " + fm.getTotalParts() + ", size is " +
                                fm.getData().length);
                        currentPart++;
                        consumer.accept(fm);

                        if (progressBar != null) {
                            progressBar.setProgress(((double) ++allFilesCompleted) / allFilesRequired);
                        }
                        logger.info("message sent");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Receives a FileMessage. If the file consists of several parts, method waits for all
     * the parts and then merges them
     *
     * @param fileParts a Map instance that stores non-written file parts
     * @param operation an operation that run when the file is successfully written
     * @throws IOException when i/o errors occur
     */
    public static void receive(FileMessage fm, Map<Path, FileParts> fileParts, Runnable operation) throws IOException {
        Path destination = Paths.get(fm.destination + File.separator + fm.getRelativePath());
        if (fm.getTotalParts() > 1) {
            logger.info("Receiving large file: " + destination + ", part N " + fm.getPart() + " of " + fm.getTotalParts()
                    + ", size is " + fm.getData().length);
            if (!fileParts.containsKey(destination)) {
                fileParts.put(destination, new FileParts());
                if (!Files.exists(destination.getParent())) Files.createDirectories(destination.getParent());
                Files.deleteIfExists(destination);
                Files.createFile(destination);
            }

            /*
            !!!PLEASE READ THIS CAREFULLY #2!!!
            A tricky part!
            FileParts instance contains a HashMap<Integer, FileMessage>, where the key is the part number and
            the value is the corresponding FileMessage that represents a file part. Let us imagine that receiver gets
            file parts in the following order: 2nd, 1st, 3rd. Then:
            1. We wait for the first part.
            2. FilePart gets 2nd part and puts it into internal HashMap. It is not the part we're waiting for,
            so continue waiting further;
            3. FilePart get 1st part. It is the needed part so we write it to the folder. Now we need ++1 = 2nd part.
            4. We immediately start searching for the second part in HashMap. If found - we write it down and search
            for the next part / return null otherwise.

             As a result, we neither create any RandomAccessFile instances nor occupy RAM heavily.
             */

            FileParts parts = fileParts.get(destination);
            parts.addPart(fm);

            FileMessage currentPart;
            //while instance of FileParts contains next part we write it down,
            //otherwise we wait for the required part and store unwritten parts in RAM
            while ((currentPart = parts.removeAndIncrement()) != null) {
                Files.write(destination, currentPart.getData(), StandardOpenOption.APPEND);
            }

            if (parts.getNextPart() > fm.getTotalParts()) {
                logger.info("File successfully assembled: " + fm.getDestination());
                fileParts.clear();
                operation.run();
            }
        } else {
            logger.info("Receiving small file or folder: " + fm.getDestination());
            if (fm.getData().length == 0) Files.createDirectories(destination);
            else {
                Files.createDirectories(destination.getParent());
                Files.write(destination, fm.getData(), StandardOpenOption.CREATE);
            }
            operation.run();
        }
    }

    /**
     * Calculates all parts amount to be sent to
     *
     * @param filePaths a list of paths to the file
     * @return amount of parts to be sent
     * @throws IOException in case of I/O errors
     */
    private static int getAllFilesPartsCount(List<Path> filePaths) throws IOException {
        int allFilesParts = 0;
        for (Path path : filePaths) {
            if (checkPathIsNotEmptyDir(path)) allFilesParts += Files.size(path) / MAX_FILE_SIZE + 1;
        }

        return allFilesParts;
    }

    /**
     * Checks that current path is not empty directory
     *
     * @param path is a path to the file
     * @return true if current file is not an empty dir and false otherwise
     * @throws IOException in case of I/O errors
     */
    private static boolean checkPathIsNotEmptyDir(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            List<Path> filePaths = Files.list(path).collect(Collectors.toList());
            return filePaths.size() != 0;
        }
        return true;
    }

}
