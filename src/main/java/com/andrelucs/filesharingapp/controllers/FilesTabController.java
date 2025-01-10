package com.andrelucs.filesharingapp.controllers;

import com.andrelucs.filesharingapp.FileSharingApplication;
import com.andrelucs.filesharingapp.components.FileItem;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

public class FilesTabController {
    private static final String LAST_FOLDER_KEY = "lastFolder";
    @FXML
    Label folderName;
    @FXML
    ComboBox<String> sortComboBox;
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
        sortComboBox.setOnAction(event -> updateSharedFilesDisplay());
        uploadingOverlay.setVisible(false);
        splitPane.setDividerPosition(0, 1);
        // Scale the image display
        splitPane.getDividers().getFirst().positionProperty().addListener((observable, oldValue, newValue) -> {
            if (displayedFile == null || !isImage(displayedFile)) return;
            resizeImage(newValue.doubleValue());
        });

        Preferences prefs = Preferences.userRoot().node(getClass().getName());
        String folderName = prefs.get(LAST_FOLDER_KEY, null);
        if(folderName == null) {
            File folder = FileSharingApplication.requestFolder();
            if (folder != null) {
                prefs.put(LAST_FOLDER_KEY, folder.getAbsolutePath());
                folderName = folder.getAbsolutePath();
            }
        }
        if(folderName != null) {
            FileSharingApplication.setSharedFolder(new File(folderName));
            updateSharedFilesDisplay();
            this.folderName.setText(folderName);
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

    public void updateSharedFilesDisplay() {
        String selectedSort = sortComboBox.getSelectionModel().getSelectedItem();
        if (selectedSort == null) {
            selectedSort = "Name";
        }
        if (selectedSort.equals("Size"))
            updateSharedFilesDisplay(Comparator.comparing(File::length));
        else if (selectedSort.equals("Date"))
            updateSharedFilesDisplay(Comparator.comparing(File::lastModified).reversed());
        else
            updateSharedFilesDisplay(Comparator.comparing(File::getName));
    }

    private void updateSharedFilesDisplay(Comparator<File> comparator) { // TODO Otimizar
        List<File> sharedFiles = FileSharingApplication.getSharedFiles();
        List<Node> children = filesDisplayContainer.getChildren();

        sharedFiles.sort(comparator);

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
            Preferences prefs = Preferences.userRoot().node(getClass().getName());
            prefs.put(LAST_FOLDER_KEY, folder.getAbsolutePath());
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
