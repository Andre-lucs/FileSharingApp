package com.andrelucs.filesharingapp.controllers;

import com.andrelucs.filesharingapp.FileSharingApplication;
import com.andrelucs.filesharingapp.components.FileItem;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class FilesTabController {
    @FXML
    Label folderName;
    @FXML
    TilePane filesDisplayContainer;
    @FXML
    Label filenameLabel;
    @FXML
    Label fileSizeLabel;
    @FXML
    ImageView fileIcon;
    @FXML
    HBox uploadingOverlay;
    @FXML
    SplitPane splitPane;

    @FXML
    public void initialize() {
        uploadingOverlay.setVisible(false);
        splitPane.setDividerPosition(0, 1);
        File folder = FileSharingApplication.requestFolder();
        if (folder != null) {
            updateSharedFilesDisplay();
            folderName.setText(folder.getAbsolutePath());
        }
    }

    public void showFileInfo(File file) {
        System.out.println("Showing file info for " + file.getName());
        splitPane.setDividerPosition(0, 0.5);
        filenameLabel.setText(file.getName());
        fileIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(FileItem.pickImage(file)))));
        var size = file.length();
        if (size > 1024 * 1024) {
            fileSizeLabel.setText(size / (1024 * 1024) + " MB");
        } else if (size > 1024) {
            fileSizeLabel.setText(size / 1024 + " KB");
        } else {
            fileSizeLabel.setText(size + " bytes");
        }

        uploadingOverlay.setVisible(FileSharingApplication.isBeingUploaded(file));
    }

    public void updateSharedFilesDisplay() { // TODO Otimizar
        List<File> sharedFiles = FileSharingApplication.getSharedFiles();
        List<Node> children = filesDisplayContainer.getChildren();

        children.clear();
        sharedFiles.forEach(file -> {
            FileItem fileItem = new FileItem(file.getAbsoluteFile());
            fileItem.setMaxWidth(200);
            fileItem.setOnMouseClicked(event -> showFileInfo(file));
            children.add(fileItem);
        });
    }

    @FXML
    public void changeSharedFolder(ActionEvent ignoredEvent) {
        File folder = FileSharingApplication.changeSharedFolder();
        if (folder != null) {
            updateSharedFilesDisplay();
            folderName.setText(folder.getAbsolutePath());
        }
    }

}
