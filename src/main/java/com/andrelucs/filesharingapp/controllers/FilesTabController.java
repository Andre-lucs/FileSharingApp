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

    private File displayedFile;

    @FXML
    public void initialize() {
        uploadingOverlay.setVisible(false);
        splitPane.setDividerPosition(0, 1);
        // Scale the image display
        splitPane.getDividers().getFirst().positionProperty().addListener((observable, oldValue, newValue) -> {
            if (displayedFile == null || !isImage(displayedFile)) return;
            resizeImage(newValue.doubleValue());
        });

        File folder = FileSharingApplication.requestFolder();
        if (folder != null) {
            updateSharedFilesDisplay();
            folderName.setText(folder.getAbsolutePath());
        }
    }

    public void showFileInfo(File file) {
        displayedFile = file;
        splitPane.setDividerPosition(0, 0.5);

        if (isImage(file)) {
            displayImage(file);
        } else {
            fileIcon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(FileItem.pickImage(file)))));
            fileIcon.setFitWidth(50);
            fileIcon.setFitHeight(50);
        }

        filenameLabel.setText(file.getName());
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

    // Image related methods

    private boolean isImage(File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") || fileName.endsWith(".gif");
    }

    private void displayImage(File image) {
        Image img = new Image(image.toURI().toString());
        fileIcon.setImage(img);
        resizeImage(0.5);
    }

    private void resizeImage(double dividerPosition) {
        fileIcon.setFitWidth((1 - dividerPosition) * splitPane.getWidth() - 20);
        fileIcon.setFitHeight((1 - dividerPosition) * splitPane.getHeight() - 20);
    }

}
