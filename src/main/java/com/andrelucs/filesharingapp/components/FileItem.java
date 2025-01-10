package com.andrelucs.filesharingapp.components;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class FileItem extends HBox implements Initializable {
    private static final Logger logger = Logger.getLogger(FileItem.class.getName());
    @FXML
    private Label fileNameLabel;
    @FXML
    private ImageView imageView;
    private final File file;

    public FileItem(String fileName) {
        this(new File(fileName));
    }

    public FileItem(File file) {
        this.file = file;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("file-item.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String pickImage(File file) {
        if (file == null || !file.exists()) return "/icons/file.png";

        if (file.isDirectory()) {
            return "/icons/folder.png";
        } else {
            return "/icons/file.png";
        }
    }

//    @FXML
//    public void initialize() {
////        this.contextMenu = new ContextMenu();
////        var openItem = new MenuItem("Open");
////        openItem.setOnAction(event -> {
////            try {
////                Desktop.getDesktop().open(file);
////            } catch (IOException e) {
////                throw new RuntimeException(e);
////            }
////        });
////        var deleteItem = new MenuItem("Delete");
////        deleteItem.setOnAction(event -> {
////            FileSharingApplication.deleteFile(file);
////            filesTabController.updateSharedFilesDisplay();
////        });
////        this.contextMenu.getItems().addAll(openItem, deleteItem);
//    }

//    private void openFileInfo(MouseEvent mouseEvent) {
////        logger.info("Clicked set times: "+mouseEvent.getClickCount());
////        if(mouseEvent.getButton() == MouseButton.SECONDARY){
////            contextMenu.show(this, Side.BOTTOM, 0, 0);
////            return;
////        }
//    }

    private void setIcon(String imagePath) {
        try (var icon = getClass().getResourceAsStream(imagePath)) {
            assert icon != null : "Image not found: " + imagePath;
            this.imageView.setImage(new Image(icon));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        fileNameLabel.setText(file.getName());
        setIcon(pickImage(file));
    }
}
