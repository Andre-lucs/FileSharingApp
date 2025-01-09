package com.andrelucs.filesharingapp.communication.client;

import com.andrelucs.filesharingapp.communication.FileInfo;

import java.util.EventListener;

public interface SearchEventListener extends EventListener {
    void onFileReceived(FileInfo file);
}
