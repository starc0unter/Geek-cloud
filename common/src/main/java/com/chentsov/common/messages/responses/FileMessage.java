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
    private final String destination;
    private final String relativePath;
    private byte[] data;
    //in case of several parts starts from 1 to (totalParts)
    private int part;
    //in case of several parts shows total amount of file parts
    private final int totalParts;

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
                    FileMessage message = new FileMessage(relativePath, destination, 1);
                    message.setDataPart(new byte[0], 1);
                    consumer.accept(message);
                    logger.info("Sent empty folder: " + filePath.toString());
                    continue;
                }

                final long actualFileSize = Files.size(filePath);
                allFilesCompleted = sendSingle(destination, consumer, progressBar, allFilesRequired, allFilesCompleted, filePath, relativePath, (int) actualFileSize);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int sendSingle(String destination,
                                  Consumer<FileMessage> consumer,
                                  ProgressBar progressBar,
                                  int allFilesRequired,
                                  int allFilesCompleted,
                                  Path filePath,
                                  String relativePath,
                                  int actualFileSize) throws IOException {
        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(filePath.toString()))) {
            boolean strictlyEqual = actualFileSize % MAX_FILE_SIZE == 0;
            int totalParts = actualFileSize / MAX_FILE_SIZE;
            if (!strictlyEqual) totalParts++;
            //selecting initial data array size
            byte[] part = totalParts > 1 ? new byte[MAX_FILE_SIZE] : new byte[actualFileSize];
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
            while ((bytesRead = stream.read(part)) > 0) {
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
        return allFilesCompleted;
    }

    /**
     * Receives a FileMessage. If the file consists of several parts, method waits for all
     * the parts and then merges them
     *
     * @param fileParts a Map instance that stores non-written file parts
     * @param operation an operation that run when the file is successfully written
     * @throws IOException when i/o errors occur
     */
    public static void receive(FileMessage message, Map<Path, FileParts> fileParts, Runnable operation) throws IOException {
        Path destination = Paths.get(message.destination + File.separator + message.getRelativePath());
        if (message.getTotalParts() <= 1) {
            processSmallFile(message, operation, destination);
            return;
        }
        processLargeFile(message, fileParts, operation, destination);
    }

    private static void processLargeFile(FileMessage message, Map<Path, FileParts> fileParts, Runnable operation, Path destination) throws IOException {
        logger.info("Receiving large file: " + destination + ", part N " + message.getPart() + " of " + message.getTotalParts()
                + ", size is " + message.getData().length);
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
        parts.addPart(message);

        FileMessage currentPart;
        //while instance of FileParts contains next part we write it down,
        //otherwise we wait for the required part and store unwritten parts in RAM
        while ((currentPart = parts.removeAndIncrement()) != null) {
            Files.write(destination, currentPart.getData(), StandardOpenOption.APPEND);
        }

        if (parts.getNextPart() > message.getTotalParts()) {
            logger.info("File successfully assembled: " + message.getDestination());
            fileParts.clear();
            operation.run();
        }
    }

    private static void processSmallFile(FileMessage message, Runnable operation, Path destination) throws IOException {
        logger.info("Receiving small file or folder: " + message.getDestination());
        if (message.getData().length == 0) Files.createDirectories(destination);
        else {
            Files.createDirectories(destination.getParent());
            Files.write(destination, message.getData(), StandardOpenOption.CREATE);
        }
        operation.run();
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
