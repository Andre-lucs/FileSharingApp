package com.andrelucs.filesharingapp;

import atlantafx.base.theme.NordDark;
import com.andrelucs.filesharingapp.communication.client.Client;
import com.andrelucs.filesharingapp.components.FolderSelectionAlert;
import com.andrelucs.filesharingapp.controllers.MainViewController;
import javafx.application.Application;
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

    private static void createClient(File sharedFolder) {
        try {
            if (sharingClient != null) {
                sharingClient.close();
            }
            sharingClient = new Client("localhost", sharedFolder.toPath());
            sharingClient.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<File> getSharedFiles() {
        if (sharingClient == null) return new ArrayList<>();
        return sharingClient.getFileTracker().getSharedFiles();
    }

    public static void deleteFile(File file) {
        sharingClient.deleteFile(file);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
