package com.andrelucs.filesharingapp.controllers;

import com.andrelucs.filesharingapp.components.NotificationItem;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainViewController {
    @FXML
    public Tab downloadTab;
    @FXML
    public Tab filesTab;
    @FXML
    private Pane notificationOverlay;

    private final List<NotificationItem> notifications = new ArrayList<>();

    @FXML
    public void initialize() {
        notificationOverlay.setVisible(true);
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
        NotificationItem notification = new NotificationItem(action, fileName, new Image(Objects.requireNonNull(getClass().getResourceAsStream(iconPath))));
        notificationOverlay.setVisible(true);
        Platform.runLater(() -> notificationOverlay.getChildren().add(notification));
        notification.deleteIn(3000);
        notifications.add(notification);
        arrangeNotifications();
    }

    private void arrangeNotifications() {
        notifications.removeIf(NotificationItem::isDeleted);

        double startY = 0;
        double spacing = 60;

        for (int i = 0; i < notifications.size(); i++) {
            NotificationItem notification = notifications.get(notifications.size() - 1 - i);
            double targetY = startY - (i * spacing);

            TranslateTransition transition = new TranslateTransition(Duration.millis(300), notification);
            transition.setToY(targetY);
            transition.play();
        }
    }


}
