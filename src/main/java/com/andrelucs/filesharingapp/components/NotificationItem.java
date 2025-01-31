package com.andrelucs.filesharingapp.components;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.util.Objects;

public class NotificationItem extends HBox {
    private boolean deleted = false;
    private final Label action;
    private final Label fileName;
    private final ImageView icon;

    public NotificationItem() {
        super(5);
        this.setAlignment(Pos.CENTER);
        this.setPrefHeight(50);
        this.setStyle("-fx-background-color: #333; -fx-background-radius: 10; -fx-padding: 10; " +
                "-fx-border-color: gray; -fx-border-width: 2; -fx-border-radius: 10;");
        this.setPadding(new Insets(5, 5, 5, 5));
        Label notificationAction = new Label("Downloading:");
        notificationAction.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        this.action = notificationAction;
        Label notificationFileName = new Label("filename");
        notificationFileName.setStyle("-fx-text-fill: white;");
        this.fileName = notificationFileName;
        ImageView notificationIcon = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/file.png"))));
        notificationIcon.setFitHeight(30);
        notificationIcon.setFitWidth(30);
        notificationIcon.setPreserveRatio(true);
        this.icon = notificationIcon;
        this.getChildren().addAll(notificationAction, notificationFileName, notificationIcon);
    }

    public NotificationItem(String action, String fileName, Image iconPath) {
        this();
        this.setActionText(action);
        this.setFileNameText(fileName);
        this.setIconImage(iconPath);
    }

    public void setActionText(String action) {
        this.action.setText(action);
    }

    public void setFileNameText(String fileName) {
        this.fileName.setText(fileName);
    }

    public void setIconImage(Image image) {
        this.icon.setImage(image);
    }

    public void deleteIn(int msTimeout) {
        PauseTransition delay = new PauseTransition(Duration.millis(msTimeout));
        delay.setOnFinished(e -> {
            FadeTransition fade = new FadeTransition(Duration.millis(200), this);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(fadeEvent -> Platform.runLater(() -> {
                if (this.getParent() != null) {
                    ((javafx.scene.layout.Pane) this.getParent()).getChildren().remove(this);
                    this.deleted = true;
                }})
            );
            fade.play();
        });
        delay.play();
    }

    public boolean isDeleted(){
        return this.deleted;
    }

}
