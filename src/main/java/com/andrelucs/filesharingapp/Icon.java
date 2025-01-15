package com.andrelucs.filesharingapp;

import com.andrelucs.filesharingapp.communication.client.file.FileAction;

public enum Icon {
    DOWNLOAD("/icons/download-48.png"),
    UPLOAD("/icons/upload-48.png"),
    UPLOADGIF("/icons/upload.gif"),
    CHECK("/icons/check-48.png"),
    ERROR("/icons/error-90.png"),
    FILE("/icons/file.png"),
    ;

    private final String path;

    Icon(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public static Icon fromAction(FileAction action) {
        return switch (action) {
            case UPLOAD -> UPLOAD;
            case DOWNLOAD -> DOWNLOAD;
            case DELETE, DOWNLOAD_COMPLETE -> CHECK;
            case ERROR -> ERROR;
        };
    }
}
