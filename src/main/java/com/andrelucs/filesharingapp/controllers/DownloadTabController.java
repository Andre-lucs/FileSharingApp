package com.andrelucs.filesharingapp.controllers;

import com.andrelucs.filesharingapp.FileSharingApplication;
import com.andrelucs.filesharingapp.communication.FileInfo;
import com.andrelucs.filesharingapp.communication.client.Client;
import com.andrelucs.filesharingapp.components.FileItem;
import com.andrelucs.filesharingapp.components.UserSharing;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.TilePane;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class DownloadTabController implements Initializable {
    private static final int MAX_FILES_PER_PAGE = 30;
    @FXML
    Label fileNameLabel;
    @FXML
    Label fileSizeLabel;
    @FXML
    TilePane usersSharing;
    @FXML
    TextField searchInput;
    @FXML
    private Pagination searchPagination;

    private Client client;
    private FileInfo selectedFile;
    private final List<String> shownFiles = new CopyOnWriteArrayList<>();
    private final List<FileInfo> fileInfoList = new CopyOnWriteArrayList<>();
    private int currentPageIndex = 0;
    private final ScrollPane scrollPane;
    private final TilePane tilePane;
    private final FileItem[] fileItems;
    private boolean updatingPage;
    private boolean updateAgain;

    public DownloadTabController() {
        fileItems = new FileItem[MAX_FILES_PER_PAGE];
        for (int i = 0; i < fileItems.length; i++) {
            fileItems[i] = new FileItem("");
            fileItems[i].setMaxWidth(300);
        }

        this.scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        tilePane = new TilePane();
        tilePane.setAlignment(Pos.TOP_CENTER);
        tilePane.setHgap(10);
        tilePane.setVgap(10);
        scrollPane.setContent(tilePane);
    }

    public void updateClient() {
        System.out.println("initializing download tab");
        var newClient = FileSharingApplication.getClient();
        if (newClient != null && newClient != client) {
            if (client != null) client.removeSearchResultListener(this::handleSearchResult);
            newClient.addSearchResultListener(this::handleSearchResult);
        }
        client = newClient;
    }

    @FXML
    public void downloadFile(ActionEvent ignoredEvent) {
        // get selected file owners to download from
        var selectedFileOwners = usersSharing.getChildren().stream()
                .filter(node -> node instanceof UserSharing)
                .map(i -> (UserSharing) i)
                .filter(CheckBox::isSelected)
                .map(UserSharing::getUserIp)
                .collect(Collectors.toSet());
        if (selectedFileOwners.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No owner selected");
            alert.setContentText("Please select at least one owner to download from");
            alert.showAndWait();
            return;
        }
        Thread downloadThread = getDownloadThread(selectedFileOwners);
        downloadThread.start();
    }

    private @NotNull Thread getDownloadThread(Set<String> selectedFileOwners) {
        Thread downloadThread = new Thread(() -> {
            try {
                client.downloadFileFromOwners(selectedFile.name(), selectedFileOwners, selectedFile.size());
            } catch (IllegalStateException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Download error");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        });
        downloadThread.setName("Download Thread");
        return downloadThread;
    }

    @FXML
    public void searchForFiles(ActionEvent ignoredEvent) {
        shownFiles.clear();
        fileInfoList.clear();
        String searchQuery = searchInput.getText();
        client.sendSearchRequest(searchQuery);
    }

    private void handleSearchResult(FileInfo fileInfo) {
        if (shownFiles.contains(fileInfo.name())) return; // Do not show the same file twice
        shownFiles.add(fileInfo.name());
        fileInfoList.add(fileInfo);
        int newPageCount = (int) Math.ceil(fileInfoList.size() / (double) MAX_FILES_PER_PAGE);
        if (newPageCount != searchPagination.getPageCount())
            Platform.runLater(() -> searchPagination.setPageCount(newPageCount));
        if (shownFiles.size() <= MAX_FILES_PER_PAGE) {
            System.out.println("updtaing content for " + fileInfo);
            if (updatingPage) {
                updateAgain = true;
                return;
            }
            Platform.runLater(this::updatePageContent);
        }
    }


    private void showFileInfo(FileInfo fileInfo) {
        selectedFile = fileInfo;
        fileNameLabel.setText(fileInfo.name());
        fileSizeLabel.setText(fileInfo.size() + " bytes");
        //displayFileOwners
        usersSharing.getChildren().removeIf(node -> true);
        client.getFileOwners(fileInfo.name()).forEach(owner -> {
            UserSharing userSharingCheckBox = new UserSharing(owner);
            usersSharing.getChildren().add(userSharingCheckBox);
        });
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        searchPagination.setPageFactory(this::createPage);
    }

    private Node createPage(Integer pageIndex) {
        currentPageIndex = pageIndex;

        Platform.runLater(this::updatePageContent);

        return scrollPane;
    }

    private void updatePageContent() {
        updatingPage = true;
        List<FileInfo> copy = new ArrayList<>(fileInfoList);
        int sliceStart = currentPageIndex * MAX_FILES_PER_PAGE;
        int sliceEnd = Math.min((currentPageIndex + 1) * MAX_FILES_PER_PAGE, copy.size());
        if (sliceStart >= copy.size()) return;
        List<FileInfo> filesInPage = new ArrayList<>(copy.subList(sliceStart, sliceEnd));

        for (int i = 0; i < filesInPage.size(); i++) {
            FileInfo fileInfo = filesInPage.get(i);
            FileItem fileItem = fileItems[i];
            fileItem.setFileName(fileInfo.name());
            fileItem.setOnMouseClicked(event -> showFileInfo(fileInfo));
        }
        var filesToDisplay = Arrays.copyOf(fileItems, filesInPage.size());
        tilePane.getChildren().setAll(filesToDisplay);
        if (updateAgain) {
            updateAgain = false;
            Platform.runLater(this::updatePageContent);
        }
        updatingPage = false;
    }

}
