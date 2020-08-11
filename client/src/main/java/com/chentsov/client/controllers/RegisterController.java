package com.chentsov.client.controllers;

import com.chentsov.client.Connection;
import com.chentsov.common.messages.AbstractMessage;
import com.chentsov.common.messages.requests.AuthRequest;
import com.chentsov.common.messages.responses.AuthResponse;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RegisterController extends AbstractController {

    @FXML
    VBox mainVBox;
    @FXML
    Label infoLabel;
    @FXML
    TextField userField;
    @FXML
    PasswordField passwordField1;
    @FXML
    PasswordField passwordField2;
    @FXML
    Button createUserButton;
    @FXML
    Button backToLoginButton;

    @SuppressWarnings("FieldCanBeLocal")
    private Connection connection;

    /**
     * Makes an attempt to create a new user. May fail due to occupied username
     */
    public void tryCreateNewUser() {
        boolean isUserFieldEmpty = userField.getText().isEmpty();
        boolean isPassField1Empty = passwordField1.getText().isEmpty();
        boolean isPassField2Empty = passwordField2.getText().isEmpty();

        if (isUserFieldEmpty || isPassField1Empty || isPassField2Empty) {
            infoLabel.setText("None of the field may be left blank");
            resetFields();
            return;
        }

        if (!passwordField1.getText().equals(passwordField2.getText())) {
            infoLabel.setText("Password must match");
            resetFields();
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Connection> future = executor.submit(Connection::get);
        connection = LoginController.checkConnection(future, executor, infoLabel);

        AuthRequest request = new AuthRequest(userField.getText().trim(), passwordField1.getText().trim(), true);
        connection.sendMsg(request);

        try {
            while (true) {
                AbstractMessage am = connection.readObject();
                if (am instanceof AuthResponse) {
                    if (((AuthResponse) am).success) {
                        Alert alert = new Alert(Alert.AlertType.NONE, "User successfully created", ButtonType.OK);
                        alert.showAndWait();
                        openLoginScene();
                    } else infoLabel.setText("Username already occupied");
                    resetFields();
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            resetFields();
            infoLabel.setText("A connection error occurred, please reload the app");
        }
    }

    /**
     * Resets text fields after an unsuccessful attempt
     */
    private void resetFields() {
        userField.setText("");
        passwordField1.setText("");
        passwordField2.setText("");
    }

    /**
     * Switches back to login scene
     */
    public void openLoginScene() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/login.fxml"));
            Scene scene = new Scene(root, 250, 350);
            ((Stage) mainVBox.getScene().getWindow()).setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
