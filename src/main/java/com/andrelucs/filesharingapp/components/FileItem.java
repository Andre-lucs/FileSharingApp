package com.andrelucs.filesharingapp.components;

import com.andrelucs.filesharingapp.FileSharingApplication;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class FileItem extends HBox implements Initializable {
    private static final Logger logger = Logger.getLogger(FileItem.class.getName());
    @FXML
    private Label fileNameLabel;
    @FXML
    private ImageView imageView;
    private File file;
    private ContextMenu contextMenu;
    private final List<MenuItem> menuItems;
    private final List<EventHandler<? super  MouseEvent>> mouseEventHandlers;

    public FileItem(String fileName) {
        this(new File(fileName));
    }

    public FileItem(File file) {
        this.file = file;
        this.menuItems = new ArrayList<>();
        this.mouseEventHandlers = new ArrayList<>();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("file-item.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        setOnMouseClicked(event -> {
            for (var handler : mouseEventHandlers) {
                handler.handle(event);
            }
        });

    }

    public static String pickImage(File file) {
        if (file == null || !file.exists()) return "/icons/file.png";

        if (file.isDirectory()) {
            return "/icons/folder.png";
        } else {
            return "/icons/file.png";
        }
    }

    private void rightButtonAction(MouseEvent mouseEvent) {
        logger.info("Clicked set times: "+mouseEvent.getClickCount());
        if(mouseEvent.getButton() == MouseButton.SECONDARY){
            contextMenu.show(this, Side.BOTTOM, 0, 0);
        }
    }

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
        setupContextMenu();
    }

    private void setupContextMenu() {
        this.contextMenu = new ContextMenu();
        MenuItem openItem = new MenuItem("Open");
        this.menuItems.add(openItem);
        openItem.setOnAction(event -> {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        MenuItem deleteItem = new MenuItem("Delete");
        this.menuItems.add(deleteItem);
        deleteItem.setOnAction(event -> {
            FileSharingApplication.unshareFile(file);
            file.delete();
        });
        addOnMouseClicked(this::rightButtonAction);
        this.contextMenu.getItems().addAll(menuItems);
    }

    public void setFile(File file) {
        this.file = file;
        fileNameLabel.setText(file.getName());
        setIcon(pickImage(file));
    }

    public void setFileName(String fileName) {
        if (Platform.isFxApplicationThread()) {
            fileNameLabel.setText(fileName);
        } else {
            Platform.runLater(() -> fileNameLabel.setText(fileName));

        }
        this.file = new File(fileName);
    }

    public File getFile() {
        return file;
    }

    public String getFileName() {
        return file.getName();
    }

    public void addOnMouseClicked(EventHandler<? super  MouseEvent> eventHandler) {
        mouseEventHandlers.add(eventHandler);
    }
}
