package com.chentsov.client.util;

import com.chentsov.common.FileItem;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TableContextMenu {

    private static final Logger logger = LogManager.getLogger(TableContextMenu.class.getSimpleName());

    private TableView<FileItem> owner;
    private MenuItem deleteItem;
    private MenuItem renameItem;

    TableContextMenu(TableView<FileItem> owner) {
        this.owner = owner;
        ContextMenu menu = new ContextMenu();

        renameItem = new MenuItem("Rename");
        deleteItem = new MenuItem("Delete");

        menu.getItems().add(renameItem);
        menu.getItems().add(deleteItem);

        setupRenameMenuItem();
        owner.setContextMenu(menu);
    }

    /**
     * Prepares a rename MenuItem for the chosen TableView
     */
    private void setupRenameMenuItem() {
        renameItem.disableProperty().bind(Bindings.size(owner
                .getSelectionModel()
                .getSelectedItems())
                .greaterThan(1));
        renameItem.disableProperty().bind(getRootDirBooleanBinding());

        renameItem.setOnAction(event -> {
            event.consume();
            owner.setEditable(true);
            logger.info("SetEditable enabled");
            owner.edit(owner.getSelectionModel().getSelectedIndex(), owner.getColumns().get(0));
            logger.info("SetEditable disabled");
        });
    }

    /**
     * Prepares a delete MenuItem for the chosen TableView
     */
    void setupDeleteMenuItem(Runnable operation) {
        deleteItem.disableProperty().bind(getRootDirBooleanBinding());
        deleteItem.setOnAction(event -> operation.run());
    }

    /**
     * Provides a BooleanBinding containing ObservableValue that is responsible for root dir selection:
     * contains "true" if the selection contains root FileItem and "false" otherwise
     *
     * @return a BooleanBinding instance
     */
    private BooleanBinding getRootDirBooleanBinding() {
        ObservableList<FileItem> fileItems = owner.getSelectionModel().getSelectedItems();
        return Bindings.createBooleanBinding(() -> fileItems.filtered(FileItem::isRootDir).size() > 0, fileItems);
    }

}
