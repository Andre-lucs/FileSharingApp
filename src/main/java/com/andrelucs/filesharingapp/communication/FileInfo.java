package com.andrelucs.filesharingapp.communication;

import java.io.File;

public record FileInfo(String name, String owner, Long size){
    public FileInfo {
        if (owner == null || name == null || size == null) {
            throw new IllegalArgumentException("Owner, name and size must not be null");
        }
    }

    public File toFile(){
        return new File(name);
    }
}
