package com.andrelucs.filesharingapp.communication.client.file;

import com.andrelucs.filesharingapp.communication.FileInfo;

public interface DownloadProgressListener {
    void onProgressUpdate(FileInfo fileName, float progress);
}
