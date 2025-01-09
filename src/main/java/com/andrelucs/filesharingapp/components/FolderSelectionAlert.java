package com.andrelucs.filesharingapp.components;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;

public class FolderSelectionAlert {
    private File selectedFolder = null;
    private final Stage window;
    private static DirectoryChooser directoryChooser;

    @FXML
    private Button selectFolder;
    @FXML
    private Button selectLater;

    public FolderSelectionAlert() {
        window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        window.setResizable(false);
        window.setTitle("Select Folder");
        Platform.runLater(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("folder-selection-alert.fxml"));
            loader.setController(this);
            try {
                AnchorPane root = loader.load();

                window.setScene(new Scene(root));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            window.sizeToScene();
            window.centerOnScreen();
            selectFolder.setOnAction(event -> {
                selectedFolder = promptUserForFolder(window);
                if(selectedFolder != null) {
                    window.close();
                }
            });
            selectLater.setOnAction(this::cancelFolderSelection);
        });
    }

    /**
     * Show the folder selection dialog and return the selected folder
     * @return the selected folder or {@code null} if the user cancels the selection
     */
    public File requestFolder() {
        window.showAndWait();
        return selectedFolder;
    }

    public static File promptUserForFolder(Window window) {
        if (directoryChooser == null) {
            directoryChooser = new DirectoryChooser();
            try {
                directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
            } catch (Exception e) {
                System.err.println("Error setting initial directory: " + e.getMessage());
            }
        }
        return directoryChooser.showDialog(window);
    }

    public void cancelFolderSelection(Event event) {
        window.close();
    }
}
