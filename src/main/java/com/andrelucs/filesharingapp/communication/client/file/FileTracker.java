package com.andrelucs.filesharingapp.communication.client.file;

import com.andrelucs.filesharingapp.communication.FileInfo;
import com.andrelucs.filesharingapp.communication.client.Client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FileTracker {

    private final Map<String, File> files = new HashMap<>();
    protected final Map<String, Boolean> sharedFiles = new HashMap<>();
    private final Client client;

    private final Object deletingFilesLock = new Object();

    public FileTracker(Client client, Path sharedFolder) {
        this.client = client;
        // Share all files in the shared folder

        if (!Files.isDirectory(sharedFolder)) {
            throw new IllegalArgumentException("The shared folder must be a directory");
        }
        try (var filesToShare = Files.list(sharedFolder)) {
            filesToShare.forEach(file -> shareFileLater(file.toFile()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Shares a file by adding it to the local tracking maps and sending a create file request to the server.
     * This method immediately initiates the sharing process for the specified file.
     * @return true if the file was successfully shared, false if the file is a directory or is an empty file.
     */
    public boolean shareFile(File file) {
        if (file.isDirectory()) return false;
        if(file.length() == 0) return false;
        files.put(file.getName(), file);
        client.sendCreateFileRequest(file.getName(), file.length());
        return true;
    }

    /**
     * Prepares a file for sharing at a later time without immediately sending a create file request.
     * This method adds the file information to the local tracking maps but does not initiate the sharing process.
     * @return true if the file was successfully shared, false if the file is a directory or is an empty file.
     */
    public boolean shareFileLater(File file) {
        if(file.isDirectory()) return false;
        if(file.length() == 0) return false;
        files.put(file.getName(), file);
        return true;
    }

    public void deleteAllFiles() {
        Iterator<String> iterator = files.keySet().iterator();
        while (iterator.hasNext()) {
            String fileName = iterator.next();
            iterator.remove();
            client.sendDeleteFileRequest(fileName);
        }
        waitAllFilesDeletion();
        System.out.println("Received confirmation of all shared files");
    }

    /**
     * Sends create file requests for all files that have not yet been shared. <br/>
     * For example files that were prepared for sharing using the shareFileLater method.
     */
    public void sendPendingFiles() {
        files.forEach((fileName, file) -> {
            client.sendCreateFileRequest(fileName, file.length());
        });
    }

    public void confirmFileSharing(String fileName) {
        sharedFiles.put(fileName, true);
    }

    public void confirmUnsharedFile(String fileName) {
        synchronized (deletingFilesLock) {
            files.remove(fileName);
            sharedFiles.remove(fileName);
            deletingFilesLock.notify();
        }
    }

    public void waitAllFilesDeletion() {
        synchronized (deletingFilesLock) {
            while (!sharedFiles.isEmpty()) {
                try {
                    deletingFilesLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public File getFile(String fileName) {
        return files.get(fileName);
    }

    public List<File> getSharedFiles() {
        return files.values().stream().toList();
    }

    public void deleteFile(File file) {
        files.remove(file.getName());
        sharedFiles.remove(file.getName());
        client.sendDeleteFileRequest(file.getName());
        file.delete();
    }

}
