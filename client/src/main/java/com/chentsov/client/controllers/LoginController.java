package com.chentsov.client.controllers;

import com.chentsov.client.Connection;
import com.chentsov.client.util.GUIHelper;
import com.chentsov.common.messages.AbstractMessage;
import com.chentsov.common.messages.requests.AuthRequest;
import com.chentsov.common.messages.responses.AuthResponse;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;


public class LoginController extends AbstractController {

    @FXML
    VBox mainVBox;
    @FXML
    Label infoLabel;
    @FXML
    TextField userField;
    @FXML
    PasswordField passwordField;
    @FXML
    Button registerButton;
    @FXML
    Button authButton;
    @FXML
    ImageView logoImage;
    @FXML
    ImageView loginImage;
    @FXML
    ImageView passImage;
    @FXML
    Button connectionSettingsButton;

    private Connection connection;

    /**
     * Makes an attempt to log in.
     */
    public void tryLoginAction() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Connection> future = executor.submit(Connection::get);
        connection = checkConnection(future, executor, infoLabel);
        if (connection == null) return;

        if (userField.getText().isEmpty() || passwordField.getText().isEmpty())
            infoLabel.setText("Username and password cannot be empty");

        AuthRequest request = new AuthRequest(userField.getText().trim(), passwordField.getText().trim(), false);
        connection.sendMsg(request);

        listenForAuthResponse();
    }

    /**
     * Check the connection to the server
     *
     * @param future   a Future instance that incapsulates check connection task
     * @param executor an ExecutorService related to the thread that checks connection
     * @param label    a Label instance that shows connection status
     * @return Connection instance in case of success or null otherwise
     */
    static Connection checkConnection(Future<Connection> future, ExecutorService executor, Label label) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            label.setText("Please check connection settings");
            return null;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Listens for the auth response
     */
    private void listenForAuthResponse() {
        try {
            while (true) {
                AbstractMessage am = connection.readObject();
                if (am instanceof AuthResponse) {
                    if (((AuthResponse) am).success) {
                        CloudController controller = (CloudController) GUIHelper.changeScene((Stage) mainVBox.getScene().getWindow(),
                                getClass().getResource("/cloud.fxml"), 800, 600, true);
                        //setting cloud storage path
                        Objects.requireNonNull(controller).setCurrentCloudPath(((AuthResponse) am).pathToStorage);
                    } else infoLabel.setText("Wrong username or password");
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
        passwordField.setText("");
    }

    /**
     * Switches to new user registration scene
     */
    public void openRegisterScene() {
        GUIHelper.changeScene((Stage) mainVBox.getScene().getWindow(),
                getClass().getResource("/register.fxml"), 250, 350, false);
    }

    /**
     * Opens connection settings window
     */
    public void openConnectionSettings() {
        ConnectionSettingsController.showConnectionSettings(this.getClass());
    }

}
