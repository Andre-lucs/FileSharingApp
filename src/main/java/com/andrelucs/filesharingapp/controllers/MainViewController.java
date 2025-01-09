package com.andrelucs.filesharingapp.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;

import java.io.IOException;

public class MainViewController {
    @FXML
    public Tab downloadTab;
    @FXML
    public Tab filesTab;

    @FXML
    public void initialize() {
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

}
