package com.andrelucs.filesharingapp.communication.client.file;

import com.andrelucs.filesharingapp.communication.client.Client;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

public class FileTracker implements Closeable {

    private final Map<String, File> files;
    protected final List<String> sharedFiles;
    private final Client client;
    private final Path sharedFolder;

    private CountDownLatch deletionLatch;

    private final WatchService watchService;
    private final ExecutorService watchServiceExecutor;
    private Function<Path, Void> fileChangeHandler = path -> null;

    public FileTracker(Client client, Path sharedFolder) throws IOException {
        this.files = new HashMap<>();
        this.sharedFiles = new ArrayList<>();
        this.client = client;
        this.sharedFolder = sharedFolder;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.watchServiceExecutor = Executors.newSingleThreadExecutor();

        // Share all files in the shared folder

        if (!Files.isDirectory(sharedFolder)) {
            throw new IllegalArgumentException("The shared folder must be a directory");
        }
        try (var filesToShare = Files.list(sharedFolder)) {
            filesToShare.forEach(file -> shareFileLater(file.toFile()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        sharedFolder.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        );

        watchServiceExecutor.submit(this::processWatchEvents);

    }

    /**
     * Shares a file by adding it to the local tracking maps and sending a create file request to the server.
     * This method immediately initiates the sharing process for the specified file.
     */
    public void shareFile(File file) {
//        if (!shareFileLater(file)) return;
        files.put(file.getName(), file);
        client.sendCreateFileRequest(file.getName(), file.length());
    }

    /**
     * Prepares a file for sharing at a later time without immediately sending a create file request.
     * This method adds the file information to the local tracking maps but does not initiate the sharing process.
     *
     * @return true if the file was successfully shared, false if the file is a directory or is an empty file.
     */
    public boolean shareFileLater(File file) {
        if (file.isDirectory()) return false;
        if (file.length() == 0) return false;
        System.out.println("Sharing file: " + file.getName() + file);
        files.put(file.getName(), file);
        return true;
    }

    public void deleteAllFiles() {
        int fileCount = files.size();
        deletionLatch = new CountDownLatch(fileCount);
        List<String> fileNames = new ArrayList<>(files.keySet());
        fileNames.forEach(client::sendDeleteFileRequest);
    }

    /**
     * Sends create file requests for all files that have not yet been shared. <br/>
     * For example files that were prepared for sharing using the shareFileLater method.
     */
    public void sendPendingFiles() {
        files.forEach((fileName, file) -> client.sendCreateFileRequest(fileName, file.length()));
    }

    public void confirmFileSharing(String fileName) {
        sharedFiles.add(fileName);
        fileChangeHandler.apply(sharedFolder.resolve(fileName));
    }

    public void confirmUnsharedFile(String fileName) {
        sharedFiles.remove(fileName);
        if (deletionLatch != null) {
            deletionLatch.countDown();
        }
        fileChangeHandler.apply(sharedFolder.resolve(fileName));
    }

    public void waitAllFilesDeletion() {
        try {
            deletionLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public File getFile(String fileName) {
        return files.get(fileName);
    }

    public Boolean getSharedFile(String fileName) {
        return sharedFiles.contains(fileName);
    }

    public List<File> getTrackedFiles() {
        return new ArrayList<>(files.values());
    }

    public List<String> getSharedFileNames() {
        return new ArrayList<>(sharedFiles);
    }

    public void unShareFile(File file) {
        sharedFiles.remove(file.getName());
        client.sendDeleteFileRequest(file.getName());
    }

    public void stopTrackingFile(File file) {
        files.remove(file.getName());
        unShareFile(file);
    }

    private void processWatchEvents() {
        try {
            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path filePath = sharedFolder.resolve((Path) event.context());

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        shareFile(filePath.toFile());
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        unShareFile(filePath.toFile());
                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        unShareFile(filePath.toFile());
                        shareFile(filePath.toFile());
                    }
                    fileChangeHandler.apply(filePath);
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    @Override
    public void close() throws IOException {
        watchService.close();
        watchServiceExecutor.shutdown();
    }

    public void setFileChangeHandler(Function<Path, Void> fileChangeHandler) {
        this.fileChangeHandler = fileChangeHandler;
    }
}
