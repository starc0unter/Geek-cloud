package com.chentsov.client.util;

import com.chentsov.client.Connection;
import com.chentsov.client.controllers.AbstractController;
import com.chentsov.client.controllers.CloudController;
import com.chentsov.client.controllers.ProgressController;
import com.chentsov.common.FileItem;
import com.chentsov.common.messages.responses.FileMessage;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * @author Evgenii Chentsov
 */
public class GUIHelper {

    private static final Logger logger = LogManager.getLogger(GUIHelper.class.getSimpleName());
    private static Map<String, Image> iconCache = new WeakHashMap<>();

    private static CloudController controller;
    private static Connection connection;

    private static SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    /**
     * Prepares GUI for the main stage
     *
     * @param localFilesTable a TableView that represents local storage
     * @param cloudFilesTable a TableView that represents cloud storage
     * @param controller      a controller of the main stage
     */
    public static void setupGUI(TableView<FileItem> localFilesTable, TableView<FileItem> cloudFilesTable, CloudController controller) {
        GUIHelper.controller = controller;

        logger.info("Configuring tables...");
        setupTableStructure(localFilesTable, controller::renameLocalFile, true);
        setupTableStructure(cloudFilesTable, controller::renameCloudFile, false);

        setupLocalDnD(localFilesTable, cloudFilesTable);
        setupCloudDnD(localFilesTable, cloudFilesTable);
        logger.info("Tables configured");

        try {
            connection = Connection.get();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures table structure
     *
     * @param table          a TableView to be configured
     * @param onRenameAction an action to be performed when "rename" context menu command is called
     * @param isLocal        a variables that indicates that current table is local or not
     */
    private static void setupTableStructure(TableView<FileItem> table, BiConsumer<FileItem, String> onRenameAction, boolean isLocal) {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        //configuring columns
        TableColumn<FileItem, FileItem> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        nameColumn.setCellFactory(getNameCellCallback(table, onRenameAction));
        nameColumn.setComparator(getNameComparator());

        TableColumn<FileItem, FileItem> fileSizeColumn = new TableColumn<>("Size");
        fileSizeColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        fileSizeColumn.setCellFactory(getSizeCellCallback());
        fileSizeColumn.setComparator(getSizeComparator());
        fileSizeColumn.setMaxWidth(80.0);
        fileSizeColumn.setMinWidth(80.0);

        TableColumn<FileItem, FileItem> fileDateColumn = new TableColumn<>("Date");
        fileDateColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        fileDateColumn.setCellFactory(getDateCellCallback());
        fileDateColumn.setComparator(getDateComparator());
        fileDateColumn.setMaxWidth(120.0);
        fileDateColumn.setMinWidth(120.0);

        //noinspection unchecked
        table.getColumns().addAll(nameColumn, fileSizeColumn, fileDateColumn);

        //configuring context menu
        TableContextMenu contextMenu = new TableContextMenu(table);
        contextMenu.setupDeleteMenuItem(isLocal ? controller::deleteLocalFiles : controller::deleteCloudFiles);

        //configuring double-click action
        table.setRowFactory(isLocal ? getLocalDoubleClickCallback() : getCloudDoubleClickCallback());
    }

    /**
     * Configures drag-and-drop operations for the local table.
     *
     * @param cloudTable a table that contains a list of cloud files
     * @param localTable a table that contains a list of local files
     */
    private static void setupLocalDnD(TableView<FileItem> localTable, TableView<FileItem> cloudTable) {
        localTable.setOnDragDetected(event -> setupSetOnDragDetected(localTable, event, TransferMode.ANY));
        localTable.setOnDragOver(event -> setupSetOnDragOverDetected(localTable, event));

        localTable.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                if (event.getGestureSource() == cloudTable) {
                    logger.info("Gesture source is the cloud table");
                    controller.requestCloudFile();
                    controller.refreshLocalFiles();
                } else {
                    logger.info("Gesture source is outside the app");
                    copyFilesFromDragBoard(db, controller.getCurrentLocalPath());
                    controller.refreshLocalFiles();
                }
                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    /**
     * Configures drag-and-drop operations for the cloud table.
     *
     * @param cloudTable a table that contains a list of cloud files
     * @param localTable a table that contains a list of local files
     */
    private static void setupCloudDnD(TableView<FileItem> localTable, TableView<FileItem> cloudTable) {
        cloudTable.setOnDragDetected(event -> setupSetOnDragDetected(cloudTable, event, TransferMode.LINK));
        cloudTable.setOnDragOver(event -> setupSetOnDragOverDetected(cloudTable, event));

        cloudTable.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                if (event.getGestureSource() == localTable) {
                    logger.info("Gesture source is the local table");
                    controller.sendLocalFiles();
                } else {
                    logger.info("Gesture source is outside the app");
                    List<Path> filePaths = new ArrayList<>();
                    String source = db.getFiles().get(0).getParent();
                    try {
                        for (File file : db.getFiles()) {
                            filePaths.addAll(Files.walk(file.toPath()).collect(Collectors.toList()));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    ProgressController pc = ProgressController.showProgressStage(CloudController.class);
                    new Thread(() -> {
                        FileMessage.send(filePaths, source, controller.getCurrentCloudPath(),
                                connection::sendMsg, Objects.requireNonNull(pc).getProgressBar());
                        pc.close();
                    }).start();
                }
                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    /**
     * Configures TableView operations when dragOn operation is detected.
     *
     * @param table a TableView that call dragOn operation
     * @param event an event that appears under dragOn operation
     */
    private static void setupSetOnDragDetected(TableView<FileItem> table, MouseEvent event, TransferMode... modes) {
        Dragboard db = table.startDragAndDrop(modes);
        ClipboardContent content = new ClipboardContent();

        List<File> files = table.getSelectionModel().getSelectedItems()
                .stream()
                .map(item -> new File(item.getStringPath()))
                .collect(Collectors.toList());

        content.putFiles(files);
        db.setContent(content);

        event.consume();
    }

    /**
     * Configures TableView operations when dragOver operation is detected
     *
     * @param table a TableView that call dragOn operation
     * @param event an event that appears under dragOn operation
     */
    private static void setupSetOnDragOverDetected(TableView<FileItem> table, DragEvent event) {
        if (event.getGestureSource() != table && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.LINK);
        }
        event.consume();
    }

    /**
     * Copies files from dragBoard to local path
     *
     * @param db   a dragBoard that holds files
     * @param path a destination to copy to
     */
    private static void copyFilesFromDragBoard(Dragboard db, String path) {
        List<Path> filePaths = new ArrayList<>();
        String rootPath = db.getFiles().get(0).getParent();
        try {
            //getting all files in folders
            for (File file : db.getFiles()) {
                filePaths.addAll(Files.walk(file.toPath()).collect(Collectors.toList()));
            }
            //getting relative path and putting it into the current folder
            new Thread(() -> {
                for (Path p : filePaths) {
                    try {
                        String relativePath = p.toString().substring(rootPath.length());
                        if (Files.isDirectory(p)) Files.createDirectories(Paths.get(path, relativePath));
                        else {
                            Files.createDirectories(p.getParent());
                            Files.copy(p,
                                    Paths.get(path, relativePath),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a Callback for the double-click event
     *
     * @return a Callback instance that defines the behavior of the local table
     */
    private static Callback<TableView<FileItem>, TableRow<FileItem>> getLocalDoubleClickCallback() {
        return tv -> {
            TableRow<FileItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    FileItem item = row.getItem();
                    logger.info("Double click on: " + item.getStringPath());

                    File currentFile = item.getFile();
                    if (currentFile.isDirectory()) {
                        controller.setCurrentLocalPath(currentFile.getPath());
                        controller.getWatcherService().registerDir(currentFile.toString());   //watchService has a bug here
                        controller.refreshLocalFiles();
                    } else {
                        Desktop desktop = Desktop.getDesktop();
                        try {
                            desktop.open(currentFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            return row;
        };
    }

    /**
     * Creates a Callback for the double-click event
     *
     * @return a Callback instance that defines the behavior of the cloud table
     */
    private static Callback<TableView<FileItem>, TableRow<FileItem>> getCloudDoubleClickCallback() {
        return tv -> {
            TableRow<FileItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    FileItem item = row.getItem();
                    logger.info("Double click on cloud file: " + item.getName());

                    File currentFile = item.getFile();
                    if (currentFile.isDirectory()) {
                        controller.setCurrentCloudPath(currentFile.getPath());
                        controller.refreshCloudFiles();
                    } else {
                        Alert alert = new Alert(Alert.AlertType.WARNING, "Please download the file to open", ButtonType.OK);
                        alert.showAndWait();
                    }
                }
            });
            return row;
        };
    }

    /**
     * Returns a Callback for the date table cell
     *
     * @return a prepared Callback for the cell
     */
    private static Callback<TableColumn<FileItem, FileItem>, TableCell<FileItem, FileItem>>
    getDateCellCallback() {
        return new Callback<TableColumn<FileItem, FileItem>, TableCell<FileItem, FileItem>>() {
            @Override
            public TableCell<FileItem, FileItem> call(TableColumn<FileItem, FileItem> param) {
                return new TableCell<FileItem, FileItem>() {
                    private String stringDate = "";

                    {
                        setText(stringDate);
                        setContentDisplay(ContentDisplay.TEXT_ONLY);
                    }

                    @Override
                    protected void updateItem(FileItem item, boolean empty) {
                        super.updateItem(item, empty);
                        stringDate = (item != null) ? getStringDate(item) : null;
                        setText(stringDate);
                    }

                    private String getStringDate(FileItem item) {
                        String date = "";
                        if (item.isRootDir()) return date;
                        return sdf.format(item.getDate());
                    }
                };
            }
        };
    }

    /**
     * Returns a Callback for the size table cell
     *
     * @return a prepared Callback for the cell
     */
    private static Callback<TableColumn<FileItem, FileItem>, TableCell<FileItem, FileItem>>
    getSizeCellCallback() {
        return new Callback<TableColumn<FileItem, FileItem>, TableCell<FileItem, FileItem>>() {
            @Override
            public TableCell<FileItem, FileItem> call(TableColumn<FileItem, FileItem> param) {
                return new TableCell<FileItem, FileItem>() {
                    private String stringSize = "";

                    {
                        setText(stringSize);
                        setContentDisplay(ContentDisplay.TEXT_ONLY);
                    }

                    @Override
                    protected void updateItem(FileItem item, boolean empty) {
                        super.updateItem(item, empty);
                        stringSize = (item != null) ? getStringSize(item) : null;
                        setText(stringSize);
                    }

                    private String getStringSize(FileItem item) {
                        String size = "";
                        if (item.isRootDir()) return size;
                        return !item.getFile().isDirectory() ? formatSize(item.getSize()) : "<DIR>";
                    }
                };
            }
        };
    }

    /**
     * Returns a Callback for the name table cell. Allows to configure custom editable column
     * that consists of ImageView and Label
     *
     * @param table     a TableView to be configured
     * @param performer a BiConsumer that performs an action when commit event appears
     * @return a prepared Callback for the cell
     */
    private static Callback<TableColumn<FileItem, FileItem>, TableCell<FileItem, FileItem>>
    getNameCellCallback(TableView<FileItem> table, BiConsumer<FileItem, String> performer) {
        return new Callback<TableColumn<FileItem, FileItem>, TableCell<FileItem, FileItem>>() {
            @Override
            public TableCell<FileItem, FileItem> call(TableColumn<FileItem, FileItem> param) {
                return new TableCell<FileItem, FileItem>() {
                    private HBox hBox = new HBox();
                    private ImageView icon;
                    private Label nameLabel = new Label();
                    private TextField editNameField = new TextField();
                    private FileItem item;

                    {
                        setGraphic(hBox);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                        editNameField.focusedProperty().addListener((observable, oldValue, newValue) -> {
                            if (!editNameField.isFocused()) cancelEdit();
                        });
                    }

                    @Override
                    public void startEdit() {
                        super.startEdit();
                        logger.info("Edit started");
                        switchToEditField();
                        setGraphic(hBox);
                        editNameField.selectAll();
                        editNameField.requestFocus();
                    }

                    @Override
                    public void commitEdit(FileItem newItem) {
                        super.commitEdit(newItem);
                        if (nameAlreadyOccupied()) {
                            showNameOccupiedAlert();
                            cancelEdit();
                            return;
                        }
                        performer.accept(newItem, editNameField.getText());
                        updateItem(newItem, false);
                        table.setEditable(false);
                    }

                    @Override
                    public void cancelEdit() {
                        super.cancelEdit();
                        hBox.getChildren().clear();
                        hBox.getChildren().addAll(icon, nameLabel);
                        table.setEditable(false);
                    }

                    @Override
                    protected void updateItem(FileItem item, boolean empty) {
                        super.updateItem(item, empty);
                        this.item = item;
                        hBox.getChildren().clear();
                        if (item != null) {
                            icon = getIcon(item.getStringPath());
                            nameLabel.setText(item.getName());
                            hBox.getChildren().addAll(icon, nameLabel);
                        }
                        setGraphic(hBox);
                    }

                    private void switchToEditField() {
                        editNameField.setMinWidth(nameLabel.getPrefWidth());
                        editNameField.setText(nameLabel.getText());
                        hBox.getChildren().clear();
                        hBox.getChildren().addAll(icon, editNameField);
                        editNameField.setOnAction(event -> commitEdit(item));
                    }

                    private boolean nameAlreadyOccupied() {
                        String nameCandidate = editNameField.getText();
                        return table.getItems()
                                .stream()
                                .anyMatch(i -> i.getName().equals(nameCandidate));
                    }

                    private void showNameOccupiedAlert() {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Name already occupied");
                        alert.setHeaderText("Filename already occupied in the current directory");
                        alert.showAndWait();
                    }
                };
            }
        };
    }

    /**
     * Creates an icon for the current file. Uses cache for performance
     *
     * @return an ImageView that represents a file icon
     */
    private static ImageView getIcon(String path) {
        String extension = getFileExtension(path);
        Image icon = iconCache.get(extension);
        if (icon == null) {
            icon = generateIcon(path);
            iconCache.put(extension, icon);
        }
        return new ImageView(icon);
    }

    /**
     * Generates an Image for the icon
     *
     * @param path path to the file
     * @return an Image that represent an icon
     */
    private static Image generateIcon(String path) {
        Icon icon = FileSystemView.getFileSystemView().getSystemIcon(new File(path));
        BufferedImage bi = new BufferedImage(
                icon.getIconWidth(),
                icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics g = bi.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return SwingFXUtils.toFXImage(bi, null);
    }

    /**
     * Returns a file extension of the file
     *
     * @param path a string path of the file
     * @return a string that represents file extension or empty string in case if extension is absent
     */
    private static String getFileExtension(String path) {
        int lastDotIndex = path.lastIndexOf('.');
        int lastDelimiterIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));

        return lastDotIndex > lastDelimiterIndex ? path.substring(lastDotIndex + 1) : "";
    }

    /**
     * Changes scene for the chosen source with the given weight and height
     */
    public static AbstractController changeScene(Stage stage, URL url, int width, int height, boolean resizeable) {
        try {
            Parent root;
            FXMLLoader loader = new FXMLLoader(url);
            root = loader.load();

            AbstractController controller = loader.getController();

            Scene scene = new Scene(root, width, height);
            stage = (Stage) stage.getScene().getWindow();
            stage.setResizable(resizeable);
            (stage).setScene(scene);

            return controller;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Comparator<FileItem> getNameComparator() {
        return (o1, o2) -> {
            if (o1.isParentDir()) return -1;
            if (o2.isParentDir()) return 1;
            if (o1.isDir()) return -1;
            if (o2.isDir()) return 1;
            return o1.getName().compareTo(o2.getName());
        };
    }

    private static Comparator<FileItem> getSizeComparator() {
        return (o1, o2) -> {
            if (o1.isParentDir()) return -1;
            if (o2.isParentDir()) return 1;
            if (o1.isDir()) return -1;
            if (o2.isDir()) return 1;
            if (o1.getSize() == o2.getSize()) return 0;
            return o1.getSize() < o2.getSize() ? -1 : 1;
        };
    }

    private static Comparator<FileItem> getDateComparator() {
        return (o1, o2) -> {
            if (o1.isParentDir()) return -1;
            if (o2.isParentDir()) return 1;
            if (o1.getDate() == o2.getDate()) return 0;
            return o1.getDate().getTime() < o2.getDate().getTime() ? -1 : 1;
        };
    }

    /**
     * Formats long size to the readable value
     *
     * @param size file size in bytes
     * @return String containing formatted size
     */
    private static String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

}
