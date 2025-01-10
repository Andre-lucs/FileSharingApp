package com.andrelucs.filesharingapp.controllers;

import com.andrelucs.filesharingapp.FileSharingApplication;
import com.andrelucs.filesharingapp.communication.FileInfo;
import com.andrelucs.filesharingapp.communication.client.Client;
import com.andrelucs.filesharingapp.components.FileItem;
import com.andrelucs.filesharingapp.components.UserSharing;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.TilePane;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DownloadTabController {

    @FXML
    Label fileNameLabel;
    @FXML
    Label fileSizeLabel;
    @FXML
    TilePane usersSharing;
    @FXML
    TilePane searchResults;
    @FXML
    TextField searchInput;
    @FXML
    AnchorPane fileDisplay;

    private Client client;
    private FileInfo selectedFile;
    private List<String> shownFiles = new ArrayList<>();

    @FXML
    public void initialize(){
        fileDisplay.setVisible(false);
    }

    public void updateClient() {
        System.out.println("initializing download tab");
        var newClient = FileSharingApplication.getClient();
        if (newClient != null && newClient != client) {
            if(client != null) client.removeSearchResultListener(this::handleSearchResult);
            newClient.addSearchResultListener(this::handleSearchResult);
        }
        client = newClient;
    }

    @FXML
    public void downloadFile(ActionEvent event) {
        // get selected file owners to download from
        var selectedFileOwners = usersSharing.getChildren().stream().filter(node -> node instanceof UserSharing).map(i -> (UserSharing) i)
                .filter(CheckBox::isSelected).map(UserSharing::getUserIp).collect(Collectors.toSet());
        if (selectedFileOwners.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No owner selected");
            alert.setContentText("Please select at least one owner to download from");
            alert.showAndWait();
            return;
        }
        client.downloadFileFromOwners(selectedFile.name(), selectedFileOwners, selectedFile.size());
    }

    @FXML
    public void searchForFiles(ActionEvent event) {
        //remove every child from searchResults
        shownFiles = new ArrayList<>();
        searchResults.getChildren().removeIf(node -> true);
        String searchQuery = searchInput.getText();
        client.sendSearchRequest(searchQuery);
    }

    private void handleSearchResult(FileInfo fileInfo) {
        System.out.println("Will try to add file " + fileInfo);
        if (shownFiles.contains(fileInfo.name())) return; // Do not show the same file twice
        FileItem fileItem = new FileItem(fileInfo.toFile());
        fileItem.setMaxWidth(400);
        fileItem.setOnMouseClicked(event -> showFileInfo(fileInfo));
        shownFiles.add(fileInfo.name());
        Platform.runLater(() -> {
            searchResults.getChildren().add(fileItem);
            System.out.println("Added file item: " + fileInfo.name());
        });
    }

    private void showFileInfo(FileInfo fileInfo) {
        fileDisplay.setVisible(true);
        selectedFile = fileInfo;
        fileNameLabel.setText(fileInfo.name());
        fileSizeLabel.setText(fileInfo.size() + " bytes");
        //displayFileOwners
        usersSharing.getChildren().removeIf(node -> true);
        client.getFileOwners(fileInfo.name()).forEach(owner -> {
            UserSharing userSharingCheckBox = new UserSharing(fileInfo.owner());
            usersSharing.getChildren().add(userSharingCheckBox);
        });
    }


}
