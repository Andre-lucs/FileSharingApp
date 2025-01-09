package com.andrelucs.filesharingapp.communication.client;

import com.andrelucs.filesharingapp.communication.FileInfo;
import com.andrelucs.filesharingapp.communication.ProtocolCommand;
import com.andrelucs.filesharingapp.communication.client.file.FileAlreadyExistsException;
import com.andrelucs.filesharingapp.communication.client.file.FileTracker;
import com.andrelucs.filesharingapp.communication.client.file.FileTransferring;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static com.andrelucs.filesharingapp.communication.ProtocolCommand.*;

public class Client implements Closeable, SearchEventListener {

    protected Socket socket;
    protected PrintWriter writer;
    protected BufferedReader reader;
    protected boolean isConnected = false;
    private final Object connectionLock = new Object();

    protected final FileTracker fileTracker;
    protected final FileTransferring fileTransferring;
    protected final ServerResponseHandler serverResponseHandler;
    private final List<FileInfo> searchFiles = new ArrayList<>();
    //    private final Map<String, Set<FileInfo>> searchOwnerFiles = new HashMap<>(); // The files of each owner
    private final Map<String, Set<String>> searchFileOwners = new HashMap<>(); // The owners of each file

    private final Path sharedFolder;

    private final Thread responseReadingThread;
    private final Thread fileTransferringThread;

    private final Map<ProtocolCommand, Function<String, Void>> externalRequestHandlers = new HashMap<>(); // TODO: Implement

    // TODO Implement client without shared folder
    public Client(String host, Path sharedFolder) throws IOException {
        this(host, 1234, sharedFolder);
    }

    public Client(String host, int port, Path sharedFolder) throws IOException {
        this.socket = new Socket(host, port);
        this.writer = new PrintWriter(socket.getOutputStream(), true);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.sharedFolder = sharedFolder;
        this.fileTracker = new FileTracker(this, sharedFolder);
        this.fileTransferring = new FileTransferring(fileTracker, sharedFolder);
        this.fileTransferringThread = new Thread(fileTransferring);
        this.serverResponseHandler = new ServerResponseHandler(this, fileTracker);
        this.serverResponseHandler.addSearchResultListener(this);
        this.responseReadingThread = new Thread(this::readResponses);
    }

    public void start() {
        responseReadingThread.start();
        sendJoinRequest();
        waitConnection();
        fileTracker.sendPendingFiles();

        fileTransferringThread.start();
    }

    @Override
    public void close() throws IOException {
        fileTracker.deleteAllFiles();
        writer.println(LEAVE.format());
        waitDisconnection();
        fileTransferring.close();
        writer.close();
        reader.close();
        socket.close();
        try {
            fileTransferringThread.join(1000);
            responseReadingThread.join(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // Requests
    public void sendJoinRequest() {
        writer.println(JOIN.format(socket.getInetAddress().getHostAddress()));
    }

    public void sendCreateFileRequest(String fileName, long fileSize) {
        writer.println(CREATEFILE.format(encodeFileName(fileName), Long.toString(fileSize)));
    }

    public void sendDeleteFileRequest(String fileName) {
        writer.println(DELETEFILE.format(encodeFileName(fileName)));
    }

    public static String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8);
    }

    public static String decodeFileName(String fileName) {
        return URLDecoder.decode(fileName, StandardCharsets.UTF_8);
    }

    public void sendSearchRequest(String pattern) {
        writer.println(SEARCH.format(pattern));
        searchFiles.clear();
//        searchOwnerFiles.clear();
        searchFileOwners.clear();
    }
    //---

    // Download methods
    public void downloadFile(FileInfo fileInfo) {
        new Thread(() -> {
            try {
                var file = fileTransferring.downloadFromSingleOwner(fileInfo, null);
                shareFile(file);
            } catch (FileAlreadyExistsException e) {
                System.err.println("Trying to download a File that already exists: " + e.getMessage());
            }
        }).start();
    }

//    public void downloadFileFromEveryOwner(String fileName) {
//        downloadFileFromOwners(fileName, searchFileOwners.get(fileName));
//    }

    public void downloadFileFromOwners(String fileName, Set<String> owners, Long fileSize) {
        System.out.println("Downloading file " + fileName + " from owners: " + owners);
        if (owners.size() == 1) {
            downloadFile(new FileInfo(fileName, owners.iterator().next(), fileSize)); // Size is not important here
            return;
        }
        FileInfo fileInfo = searchFiles.stream()
                .filter(file -> file.name().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        fileTransferring.downloadFromMultipleOwners(fileInfo, owners);
    }

    //-----

    // Utility methods
    private void waitConnection() {
        synchronized (connectionLock) {
            while (!isConnected) {
                try {
                    connectionLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void waitDisconnection() {
        synchronized (connectionLock) {
            while (isConnected) {
                try {
                    connectionLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected void setConnected(boolean connected) {
        synchronized (connectionLock) {
            isConnected = connected;
            connectionLock.notifyAll();
        }
    }

    private void shareFile(File file) {
        fileTracker.shareFile(file);
    }

    private void readResponses() {
        try {
            String response;
            while ((response = reader.readLine()) != null) {
                System.out.println("Response received: " + response);
                serverResponseHandler.handleResponse(response);
                if (!isConnected) break;
            }
        } catch (SocketException e) {
            System.out.println("Exception reading responses: Server disconnected");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onFileReceived(FileInfo file) {
        searchFiles.add(file);
        searchFileOwners.putIfAbsent(file.name(), new HashSet<>());
        searchFileOwners.get(file.name()).add(file.owner());
    }

    // Public Api Methods

    public List<FileInfo> getSearchFiles() {
        return searchFiles;
    }

    public Set<String> getFileOwners(String fileName) {
        return new HashSet<>(searchFileOwners.get(fileName));
    }


    public FileTracker getFileTracker() {
        return fileTracker;
    }

    public List<File> getSharedFiles() {
        return fileTracker.getSharedFiles();
    }

    public void addSearchResultListener(SearchEventListener listener) {
        serverResponseHandler.addSearchResultListener(listener);
    }
    public void removeSearchResultListener(SearchEventListener listener) {
        serverResponseHandler.removeSearchResultListener(listener);
    }

    public void deleteFile(File file) {
        fileTracker.deleteFile(file);
    }


    public boolean isBeingUploaded(File file) {
        return fileTransferring.isBeingUploaded(file);
    }
}
