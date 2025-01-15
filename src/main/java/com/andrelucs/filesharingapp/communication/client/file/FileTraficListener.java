package com.andrelucs.filesharingapp.communication.client.file;

public interface FileTraficListener {
    void onFileAction(FileAction action, String fileName);
}
