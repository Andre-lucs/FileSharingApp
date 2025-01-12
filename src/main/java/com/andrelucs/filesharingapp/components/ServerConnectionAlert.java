package com.andrelucs.filesharingapp.components;

import com.andrelucs.filesharingapp.communication.client.Client;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class ServerConnectionAlert implements Initializable {
    private String serverIp = null;
    private final Stage window;

    @FXML
    private TextField serverIpField;
    @FXML
    private Button connectButton;
    @FXML
    private ProgressIndicator checkingIndicator;
    @FXML
    private Label errorLabel;

    public ServerConnectionAlert() {
        window = new Stage();
        window.initModality(Modality.WINDOW_MODAL);
        window.setResizable(false);
        window.setTitle("Server Connection");
        window.setOnCloseRequest(event -> {
            // Close the application if the user closes the window without providing a server IP
            if (serverIp == null) {
                Platform.exit();
            }
        });
        FXMLLoader loader = new FXMLLoader(getClass().getResource("server-connection-alert.fxml"));
        loader.setController(this);
        try {
            AnchorPane root = loader.load();
            window.setScene(new Scene(root));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String requestServerIp() {
        window.showAndWait();
        return serverIp;
    }

    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        if (ip.equals("localhost")) return true;
        String ipPattern =
                "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return Pattern.compile(ipPattern).matcher(ip).matches();
    }

    private static boolean isServerIp(String ip) {
        return Client.checkServerIp(ip);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String address = Client.getNetworkAdress();
        if(address != null) serverIpField.setText(address);
        window.sizeToScene();
        window.centerOnScreen();
        checkingIndicator.setVisible(false);
        errorLabel.setVisible(false);
        connectButton.setOnAction(event -> {
            errorLabel.setVisible(false);
            String inputIp = serverIpField.getText();
            if (isValidIp(inputIp)) {
                checkingIndicator.setVisible(true);
                CompletableFuture.supplyAsync(() -> isServerIp(inputIp))
                        .thenAccept(isServer -> Platform.runLater(() -> {
                            checkingIndicator.setVisible(false);
                            if (isServer) {
                                serverIp = inputIp;
                                window.close();
                            } else {
                                displayError();
                            }
                        }));
            } else {
                displayError();
            }
        });
    }

    private void displayError() {
        serverIpField.setStyle("-fx-border-color: red;");
        errorLabel.setVisible(true);
    }

}
