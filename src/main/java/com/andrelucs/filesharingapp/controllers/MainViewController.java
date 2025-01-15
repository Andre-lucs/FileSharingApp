package com.andrelucs.filesharingapp.controllers;

import com.andrelucs.filesharingapp.Icon;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class MainViewController {
    @FXML
    public Tab downloadTab;
    @FXML
    public Tab filesTab;


    @FXML
    private Label notificationAction;

    @FXML
    private Label notificationFileName;

    @FXML
    private ImageView notificationIcon;

    @FXML
    private Pane notificationOverlay;

    @FXML
    public void initialize() {
        notificationOverlay.setVisible(false);
        FXMLLoader filesTabLoader = new FXMLLoader(getClass().getResource("/com/andrelucs/filesharingapp/files-tab.fxml"));
        try {
            filesTab.setContent(filesTabLoader.load());
            FilesTabController filesTabController = filesTabLoader.getController();
            filesTab.setOnSelectionChanged(event -> {
                if (filesTab.isSelected()) {
                    filesTabController.updateSharedFilesDisplay();
                }
            });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        FXMLLoader downloadTabLoader = new FXMLLoader(getClass().getResource("/com/andrelucs/filesharingapp/download-tab.fxml"));
        try {
            downloadTab.setContent(downloadTabLoader.load());
            DownloadTabController downloadTabController = downloadTabLoader.getController();
            downloadTab.setOnSelectionChanged(event -> {
                if (downloadTab.isSelected()) {
                    downloadTabController.updateClient();
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void showNotification(String action, String fileName, String iconPath) {
        // TODO Make an animation for the notification
        // TODO Support multiple notifications
        CompletableFuture.supplyAsync(() ->{
            Platform.runLater(() -> {
                notificationAction.setText(action);
                notificationFileName.setText(fileName);
                notificationIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(iconPath))));
                notificationOverlay.setVisible(true);
                if(iconPath == Icon.ERROR.getPath()) {
                    notificationIcon.getParent().setStyle("-fx-background-color: #ff0000;");
                }
            });
            return null;
        }).thenAccept((v) -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Platform.runLater(() -> {
                notificationOverlay.setVisible(false);
                notificationIcon.getParent().setStyle("");

            });

        });
    }

}
