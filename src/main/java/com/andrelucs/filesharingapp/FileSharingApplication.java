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

public class FileSharingApplication extends Application {

    public static Stage mainStage;
    public static Client sharingClient;
    public static MainViewController mainViewController;
    private static String serverIpAddress = null;

    public static boolean isBeingUploaded(File file) {
        if (sharingClient == null) return false;
        return sharingClient.isBeingUploaded(file);
    }

    public static Client getClient() {
        System.out.println("getting client:" + sharingClient);
        return sharingClient;
    }


    @Override
    public void start(Stage primaryStage) throws Exception {
        mainStage = primaryStage;
//        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
//        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
        if (serverIpAddress ==null){
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
            try {
                if (sharingClient != null) {
                    sharingClient.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
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

    private static void createClient(File sharedFolder) { // TODO Run this in the background and create a way to await it
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
                if(message == null){
                    return;
                }

                showNotification(message, fileInfo.name(), icon);
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

    public static List<File> getSharedFiles() {
        if (sharingClient == null) return new ArrayList<>();
        return sharingClient.getSharedFiles();
    }

    public static void showNotification(String action, String fileName, Icon icon) {
        mainViewController.showNotification(action, fileName, icon.getPath());
    }

    public static void unshareFile(File file) {
        sharingClient.deleteFile(file);
    }

    public static void shareFile(File file) {
        sharingClient.shareFile(file);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
