package com.andrelucs.filesharingapp.communication.client.file;

import java.io.File;

public class FileAlreadyExistsException extends Exception {

    public FileAlreadyExistsException(File file){
        super("File already exists: " + file.getName());
    }

}
