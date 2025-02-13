package com.andrelucs.filesharingapp;

import atlantafx.base.theme.NordDark;
import com.andrelucs.filesharingapp.communication.client.Client;
import com.andrelucs.filesharingapp.communication.client.file.FileAction;
import com.andrelucs.filesharingapp.components.FolderSelectionAlert;
import com.andrelucs.filesharingapp.components.ServerConnectionAlert;
import com.andrelucs.filesharingapp.controllers.MainViewController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

public class FileSharingApplication extends Application {
    private static final Logger LOGGER = Logger.getLogger(FileSharingApplication.class.getName());

    public static Stage mainStage;
    public static Client sharingClient;
    public static MainViewController mainViewController;
    private static String serverIpAddress = null;

    public static Client getClient() {
        return sharingClient;
    }


    @Override
    public void start(Stage primaryStage) throws Exception {
        mainStage = primaryStage;
//        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
//        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
        if (serverIpAddress == null) {
            ServerConnectionAlert serverConnectionAlert = new ServerConnectionAlert();
            serverIpAddress = serverConnectionAlert.requestServerIp();
            if (serverIpAddress == null) {
                Platform.exit();
                return;
            }
        }
        FXMLLoader fxmlLoader = new FXMLLoader(FileSharingApplication.class.getResource("main-view.fxml"));
        Scene mainScene = new Scene(fxmlLoader.load(), 800, 600);
        mainViewController = fxmlLoader.getController();

        primaryStage.setTitle("File Sharing App");
        primaryStage.setScene(mainScene);
        primaryStage.show();
        primaryStage.setOnCloseRequest(event -> {
            if (sharingClient != null) {
                try {
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            sharingClient.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return null;
                    }).orTimeout(5000, java.util.concurrent.TimeUnit.MILLISECONDS).join();
                } catch (CompletionException ignored) {
                    LOGGER.warning("Failed to close the client");
                    try {
                        sharingClient.shutdown();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            if (mainViewController != null) {
                mainViewController.getFilesTabController().shutdownExecutorService();
            }
        });
    }

    public static File requestFolder() {
        FolderSelectionAlert folderSelectionAlert = new FolderSelectionAlert();
        File sharedFolder = folderSelectionAlert.requestFolder();
        if (sharedFolder != null) {
            createClient(sharedFolder);
        }
        return sharedFolder;
    }

    public static File changeSharedFolder() {
        File sharedFolder = FolderSelectionAlert.promptUserForFolder(mainStage);
        if (sharedFolder != null) {
            createClient(sharedFolder);
        }
        return sharedFolder;
    }

    public static void setSharedFolder(File sharedFolder) {
        createClient(sharedFolder);
    }

    private static void createClient(File sharedFolder) {
        try {
            if (sharingClient != null) {
                sharingClient.close();
            }
            sharingClient = new Client(serverIpAddress, sharedFolder);
            sharingClient.getFileTransferring().addFileTraficListener((action, fileInfo) -> {
                Icon icon = Icon.fromAction(action);
                String message = switch (action) {
                    case FileAction.UPLOAD -> "Uploading:";
                    case FileAction.DOWNLOAD -> "Downloading:";
                    case FileAction.ERROR -> "Error:";
                    case FileAction.DOWNLOAD_COMPLETE -> "Finished downloading:";
                    default -> null;
                };
                if (message == null) {
                    return;
                }

                showNotification(message, fileInfo.name(), icon);
            });
            sharingClient.getFileTracker().setFileChangeHandler(path -> {
                Platform.runLater(() -> {
                mainViewController.updateFilesDisplay();

                });
                return null;
            });
            sharingClient.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void stopSharingFolders() {
        if (sharingClient != null) {
            try {
                sharingClient.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        createClientWithNoFolder();
    }

    public static void createClientWithNoFolder() {
        try {
            sharingClient = new Client(serverIpAddress);
            sharingClient.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<File> getFiles() {
        if (sharingClient == null) return new ArrayList<>();
        return sharingClient.getTrackedFiles();
    }

    public static List<String> getSharedFileNames() {
        if (sharingClient == null) return new ArrayList<>();
        return sharingClient.getSharedFileNames();
    }

    public static void showNotification(String action, String fileName, Icon icon) {
        mainViewController.showNotification(action, fileName, icon.getPath());
    }

    public static void unshareFile(File file) {
        sharingClient.deleteFile(file);
    }

    public static void stopTrackingFile(File file) {
        sharingClient.getFileTracker().stopTrackingFile(file);
    }

    public static void shareFile(File file) {
        sharingClient.shareFile(file);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
