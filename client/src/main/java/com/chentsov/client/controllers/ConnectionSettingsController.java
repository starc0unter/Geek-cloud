package com.chentsov.client.controllers;

import com.chentsov.client.Connection;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.regex.Pattern;

public class ConnectionSettingsController extends AbstractController {

    @FXML
    Button okButton;

    @FXML
    Button cancelButton;

    @FXML
    TextField addressTextField;

    @FXML
    TextField portTextField;

    public void setAddress() {
        int port;
        String address = addressTextField.getText();
        if (address.isEmpty()) {
            showErrorAlert("Address line cannot be empty");
            return;
        }

        //is it IPv4?
        Pattern pattern = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
        if (!pattern.matcher(address).matches()) {
            showErrorAlert("Please use IPv4 address format");
            addressTextField.setText("");
            return;
        }

        //is port written properly?
        try {
            port = Integer.parseInt(portTextField.getText());
            if (portTextField.getLength() != 4) {
                showErrorAlert("Wrong port format");
                portTextField.setText("");
                return;
            }
        } catch (NumberFormatException e) {
            showErrorAlert("Port must contain digits");
            portTextField.setText("");
            return;
        }
        Connection.setAddress(addressTextField.getText());
        Connection.setPort(port);
        if (!Connection.isClosed()) Connection.close();
        close();
    }

    /**
     * Closes Connection settings window
     */
    public void close() {
        if (Platform.isFxApplicationThread()) ((Stage) okButton.getScene().getWindow()).close();
        else Platform.runLater(() -> ((Stage) okButton.getScene().getWindow()).close());
    }

    /**
     * Creates a stage to show
     *
     * @param parentControllerClass a parent class that opens the window
     */
    static void showConnectionSettings(Class parentControllerClass) {
        Parent root;
        try {
            FXMLLoader fxmlLoader = new FXMLLoader();
            root = fxmlLoader.load(parentControllerClass.getResource("/connectionSettings.fxml").openStream());
            Stage stage = new Stage();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Connection settings");
            stage.setResizable(false);
            stage.alwaysOnTopProperty();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Shows an error alert in case of wrong data input
     *
     * @param cause a cause of Alert to be shown
     */
    private void showErrorAlert(String cause) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error setting address");
        alert.setHeaderText(cause);
        alert.showAndWait();
    }

}
