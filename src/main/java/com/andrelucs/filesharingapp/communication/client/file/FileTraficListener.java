package com.andrelucs.filesharingapp.communication.client.file;

import com.andrelucs.filesharingapp.communication.FileInfo;

public interface FileTraficListener {
    void onFileAction(FileAction action, FileInfo fileInfo);
}
