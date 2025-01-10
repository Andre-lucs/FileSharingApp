module com.andrelucs.filesharingapp {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires atlantafx.base;
    requires org.jetbrains.annotations;
    requires java.logging;
    requires java.desktop;
    requires java.prefs;

    opens com.andrelucs.filesharingapp to javafx.fxml;
    opens com.andrelucs.filesharingapp.components to javafx.fxml;
    exports com.andrelucs.filesharingapp;
    exports com.andrelucs.filesharingapp.communication.server;
    opens com.andrelucs.filesharingapp.communication.server to javafx.fxml;
    exports com.andrelucs.filesharingapp.controllers;
    opens com.andrelucs.filesharingapp.controllers to javafx.fxml;
    exports com.andrelucs.filesharingapp.communication;
    opens com.andrelucs.filesharingapp.communication to javafx.fxml;
    exports com.andrelucs.filesharingapp.communication.client;


}