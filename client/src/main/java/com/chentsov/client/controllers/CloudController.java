package com.chentsov.client.controllers;

import com.chentsov.client.Connection;
import com.chentsov.client.util.GUIHelper;
import com.chentsov.client.util.WatcherService;
import com.chentsov.common.FileItem;
import com.chentsov.common.FileParts;
import com.chentsov.common.messages.AbstractMessage;
import com.chentsov.common.messages.requests.*;
import com.chentsov.common.messages.responses.FileMessage;
import com.chentsov.common.messages.responses.FileListResponse;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.chentsov.client.util.GUIHelper.*;

public class CloudController extends AbstractController implements Initializable {

    private static final Logger logger = LogManager.getLogger(CloudController.class.getSimpleName());
    private final static String PATH_TO_STORAGE = "client/local_storage";

    private String currentLocalPath = PATH_TO_STORAGE;
    private String currentCloudPath = "";

    private final ObservableList<FileItem> localFiles = FXCollections.observableArrayList();
    private final ObservableList<FileItem> cloudFiles = FXCollections.observableArrayList();
    //a map that contains parts of different downloaded parts. When all the parts are downloaded, they are to be merged
    private final Map<Path, FileParts> fileParts = new HashMap<>();

    private WatcherService watcherService;

    @FXML
    VBox mainVBox;

    @FXML
    MenuBar menuBar;

    @FXML
    Menu fileMenu;

    //local elements
    @FXML
    TableView<FileItem> localFilesTable;

    @FXML
    Button sendButton;

    @FXML
    Button localRefreshButton;

    @FXML
    Label localCurrentPathLabel;

    //cloud elements
    @FXML
    TableView<FileItem> cloudFilesTable;

    @FXML
    Button saveButton;

    @FXML
    Button cloudRefreshButton;

    @FXML
    Label cloudCurrentPathLabel;

    private Connection connection;

    public String getCurrentLocalPath() {
        return currentLocalPath;
    }

    public WatcherService getWatcherService() {
        return watcherService;
    }

    public void setCurrentLocalPath(String currentLocalPath) {
        this.currentLocalPath = currentLocalPath;
        localCurrentPathLabel.setText(currentLocalPath);
    }

    public void setCurrentCloudPath(String currentCloudPath) {
        this.currentCloudPath = currentCloudPath;
    }

    public String getCurrentCloudPath() {
        return currentCloudPath;
    }

    public void menuExit() {
        System.exit(0);
    }

    public void logout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Log out confirmation");
        alert.setHeaderText("Are you sure you want to log out?");
        Optional<ButtonType> result = alert.showAndWait();
        //noinspection OptionalGetWithoutIsPresent
        if (result.get() == ButtonType.OK) {
            connection.sendMsg(new LogoutMessage());
            logger.info("Logging out...");
            changeScene((Stage) mainVBox.getScene().getWindow(), getClass().getResource("/login.fxml"),250, 350, false);
        }
    }

    /**
     * Initializes the main window
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            connection = Connection.get();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Thread listenerThread = new Thread(this::listenForMessages);
        listenerThread.setDaemon(true);
        listenerThread.start();

        GUIHelper.setupGUI(localFilesTable, cloudFilesTable, this);

        refreshCloudFiles();
        refreshLocalFiles();

        watcherService = new WatcherService(Paths.get(currentLocalPath), this);
    }

    /**
     * Listens for incoming messages
     */
    private void listenForMessages() {
        try {
            logger.info("Listening for messages");
            while (true) {
                AbstractMessage am = connection.readObject();
                if (am instanceof FileListResponse) {
                    processFileList((FileListResponse) am);
                } else if (am instanceof FileMessage) {
                    logger.info("Received FileMessage");
                    FileMessage fm = (FileMessage) am;
                    receiveFileMessage(fm);
                } else if (am instanceof LogoutMessage) {
                    break;
                }
            }
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        } finally {
            logger.info("Closing connection");
            Connection.close();
        }
    }

    private void processFileList(FileListResponse am) {
        logger.info("Received FileList");
        cloudFiles.clear();
        cloudFiles.addAll(am.cloudFilesList);
        if (Platform.isFxApplicationThread()) {
            cloudFilesTable.setItems(cloudFiles);
            cloudCurrentPathLabel.setText(currentCloudPath);
        } else Platform.runLater(() -> {
            cloudFilesTable.setItems(cloudFiles);
            cloudCurrentPathLabel.setText(currentCloudPath);
        });
    }

    /**
     * Deletes a file in local storage
     */
    public void deleteLocalFiles() {
        watcherService.stop();
        List<FileItem> items = localFilesTable.getSelectionModel().getSelectedItems();
        if (items.size() == 0) return;
        for (FileItem item : items) {
            item.remove();
        }
        watcherService.start();
        refreshLocalFiles();
    }

    /**
     * Refreshes localFiles list located in local storage
     */
    public void refreshLocalFiles() {
        if (Platform.isFxApplicationThread()) {
            boolean isRoot = Paths.get(currentLocalPath).equals(Paths.get(PATH_TO_STORAGE));
            FileItem.refreshFileList(localFiles, currentLocalPath, isRoot);
            localFilesTable.setItems(localFiles);
        } else {
            Platform.runLater(() -> {
                boolean isRoot = Paths.get(currentLocalPath).equals(Paths.get(PATH_TO_STORAGE));
                FileItem.refreshFileList(localFiles, currentLocalPath, isRoot);
                localFilesTable.setItems(localFiles);
            });
        }

    }

    /**
     * Sends file from local storage to cloud
     */
    public void sendLocalFiles() {
        List<Path> filePaths = new ArrayList<>();
        try {
            for (FileItem fileItem : localFilesTable.getSelectionModel().getSelectedItems()) {
                if (!fileItem.isParentDir())
                    filePaths.addAll(Files.walk(fileItem.getPath()).collect(Collectors.toList()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (filePaths.size() == 0) return;
        ProgressController pc = ProgressController.showProgressStage(this.getClass());
        new Thread(() -> {
            FileMessage.send(filePaths, currentLocalPath, currentCloudPath, connection::sendMsg, Objects.requireNonNull(pc).getProgressBar());
            pc.close();
            refreshCloudFiles();
        }).start();
    }

    /**
     * Sends command to get refreshed file list in cloud
     */
    public void refreshCloudFiles() {
        connection.sendMsg(new FileListRequest(currentCloudPath));
    }

    /**
     * Saves the file from cloud to local storage
     */
    public void requestCloudFile() {
        List<String> filenames = cloudFilesTable.getSelectionModel().getSelectedItems()
                .stream()
                .filter(item -> !item.isParentDir())
                .map(FileItem::getStringPath)
                .collect(Collectors.toList());
        if (filenames.size() == 0) return;

        connection.sendMsg(new FileRequest(filenames, currentLocalPath));
    }

    /**
     * Deletes file from cloud
     */
    public void deleteCloudFiles() {
        List<FileItem> items = new ArrayList<>(cloudFilesTable.getSelectionModel().getSelectedItems());
        if (items.size() == 0) return;

        connection.sendMsg(new DeleteFileRequest(items));
    }

    /**
     * Receives files from cloud server.
     *
     * @param fm received  file message
     * @throws IOException in case of i/o operations
     */
    private void receiveFileMessage(FileMessage fm) throws IOException {
        FileMessage.receive(fm, fileParts, this::refreshLocalFiles);
    }

    /**
     * Renames a file in local storage
     *
     * @param item a FileItem instance to be renamed
     * @param newName a new file name
     */
    public void renameLocalFile(FileItem item, String newName) {
        if (!newName.isEmpty()) item.rename(newName);
    }

    /**
     * Renames a file in cloud storage
     *
     * @param item a FileItem instance to be renamed
     * @param newName a new file name
     */
    public void renameCloudFile(FileItem item, String newName) {
        if (!newName.isEmpty()) connection.sendMsg(new RenameFileRequest(item, newName));
    }

}
