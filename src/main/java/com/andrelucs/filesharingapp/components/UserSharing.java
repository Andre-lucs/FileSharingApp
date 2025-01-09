package com.andrelucs.filesharingapp.components;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;

import java.util.Objects;

public class UserSharing extends CheckBox {
    private final String userIp;

    public UserSharing(String userIp) {
        this.userIp = userIp;
        getStylesheets().add(Objects.requireNonNull(getClass().getResource("styles.css")).toExternalForm());
        getStyleClass().add("user-sharing");
        Platform.runLater(() -> setText(userIp));

    }

    public String getUserIp() {
        return userIp;
    }
}
