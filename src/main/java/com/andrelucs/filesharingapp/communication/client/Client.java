package com.andrelucs.filesharingapp.communication.client;

import com.andrelucs.filesharingapp.communication.FileInfo;
import com.andrelucs.filesharingapp.communication.ProtocolCommand;
import com.andrelucs.filesharingapp.communication.client.file.DownloadProgressListener;
import com.andrelucs.filesharingapp.communication.client.file.FileAlreadyExistsException;
import com.andrelucs.filesharingapp.communication.client.file.FileTracker;
import com.andrelucs.filesharingapp.communication.client.file.FileTransferring;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.andrelucs.filesharingapp.communication.ProtocolCommand.*;

public class Client implements Closeable, SearchEventListener {
    private static final int SERVER_CONNECTION_PORT = 1234;

    protected Socket socket;
    protected PrintWriter writer;
    protected BufferedReader reader;
    protected boolean isConnected = false;
    private final Object connectionLock = new Object();

    protected final ServerResponseHandler serverResponseHandler;
    private final List<FileInfo> searchFiles = new ArrayList<>();
    private final Map<String, Set<String>> searchFileOwners = new HashMap<>(); // The owners of each file

    private final Thread responseReadingThread;

    private final Map<ProtocolCommand, Function<String, Void>> externalRequestHandlers = new HashMap<>(); // TODO: Implement

    private final List<Path> sharedFolders = new ArrayList<>();
    private final List<FileTracker> folderTrackers = new ArrayList<>();
    protected FileTransferring fileTransferring = null;
    private Thread fileTransferringThread = null;

    public Client(String serverIp) throws IOException {
        this(serverIp, List.of());
    }

    public Client(String serverIp, File sharedFolder) throws IOException {
        this(serverIp, List.of(sharedFolder));
    }

    // Common constructor
    public Client(String serverIp, List<File> initialFolders) throws IOException {
        this.socket = new Socket(serverIp, SERVER_CONNECTION_PORT);
        this.writer = new PrintWriter(socket.getOutputStream(), true);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        this.serverResponseHandler = new ServerResponseHandler(this);
        this.serverResponseHandler.addSearchResultListener(this);
        this.responseReadingThread = new Thread(this::readResponses);

        for (File folder : initialFolders) {
            shareFolder(folder);
        }
    }

    public static boolean checkServerIp(String ip) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, SERVER_CONNECTION_PORT), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the network address of the machine
     * @return The network address or <code>null</code> if it could not be found
     */
    public static String getNetworkAdress(){
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress inetAddress = inetAddresses.nextElement();
                        if (inetAddress instanceof java.net.Inet4Address) {
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void start() {
        responseReadingThread.start();
        sendJoinRequest();
        waitConnection();
        for (var tracker : folderTrackers) {
            tracker.sendPendingFiles();
        }

        if(fileTransferringThread != null) fileTransferringThread.start();
    }

    @Override
    public void close() throws IOException {
        // run all deleteAllFiles asynchronously
        CompletableFuture<Void> completableFutures = CompletableFuture.allOf(folderTrackers.stream()
                .map(tracker -> CompletableFuture.runAsync(tracker::deleteAllFiles))
                .toArray(CompletableFuture[]::new));
        completableFutures.join();
        writer.println(LEAVE.format());
        waitDisconnection();
        if(fileTransferring != null) fileTransferring.close();
        writer.close();
        reader.close();
        socket.close();
        if(fileTransferringThread != null) fileTransferringThread.interrupt();
        responseReadingThread.interrupt();
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

    /**
     * Download a file from multiple owners
     * @param fileName The name of the file
     * @param owners The owners to download from
     * @param fileSize The size of the file
     * @throws IllegalStateException If the FileTransferring is not initialized
     */
    public void downloadFileFromOwners(String fileName, Set<String> owners, Long fileSize) {
        if(fileTransferring == null) {
            throw new IllegalStateException("You must have a shared folder to download files.");
        }
        System.out.println("Downloading file " + fileName + " from owners: " + owners);
        FileInfo fileInfo = searchFiles.stream()
                .filter(file -> file.name().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        File downloadedFile = fileTransferring.downloadFromMultipleOwners(fileInfo, owners);
        shareFile(downloadedFile);
    }

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
        getFileTracker().shareFile(file);
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

    public void shareFolder(File folder) throws IOException {
        if(folder == null || !folder.exists() || !folder.isDirectory()){
            throw new IllegalArgumentException("Invalid folder");
        }

        sharedFolders.add(folder.toPath());
        FileTracker newTracker = new FileTracker(this, folder.toPath());
        folderTrackers.add(newTracker);

        if(this.fileTransferring == null && this.fileTransferringThread == null){
            this.fileTransferring = new FileTransferring(this, folder.toPath());
            this.fileTransferringThread = new Thread(fileTransferring);
            if(isConnected) fileTransferringThread.start();
        }
        // if is connected then the user already started the client, so we can send the files
        if(isConnected) newTracker.sendPendingFiles();
    }

    public List<FileInfo> getSearchFiles() {
        return searchFiles;
    }

    public List<Path> getSharedFolders() {
        return sharedFolders;
    }

    public Set<String> getFileOwners(String fileName) {
        return new HashSet<>(searchFileOwners.get(fileName));
    }


    public FileTracker getFileTracker() {
        return folderTrackers.getFirst();
    }

    public List<File> getSharedFiles() {
        if(folderTrackers.isEmpty()) return new ArrayList<>();
        return getFileTracker().getSharedFiles();
    }

    public void addSearchResultListener(SearchEventListener listener) {
        serverResponseHandler.addSearchResultListener(listener);
    }
    public void removeSearchResultListener(SearchEventListener listener) {
        serverResponseHandler.removeSearchResultListener(listener);
    }

    public void deleteFile(File file) {
        getFileTracker().deleteFile(file);
    }


    public boolean isBeingUploaded(File file) {
        return fileTransferring != null && fileTransferring.isBeingUploaded(file);
    }

    public void confirmFileSharing(String fileName) {
        fileName = decodeFileName(fileName);
        for (FileTracker folderTracker : folderTrackers) {
            if(folderTracker.getFile(fileName) != null){
                folderTracker.confirmFileSharing(fileName);
                return;
            }
        }
        System.out.println("File not found: " + fileName);
    }

    public void confirmUnsharedFile(String fileName) {
        fileName = decodeFileName(fileName);
        for (FileTracker folderTracker : folderTrackers) {
            if(folderTracker.getSharedFile(fileName) != null){
                folderTracker.confirmUnsharedFile(fileName);
                return;
            }
        }
    }

    public File getFile(String fileName) {
        for (FileTracker folderTracker : folderTrackers) {
            File file;
            if((file = folderTracker.getFile(fileName)) != null){
                return file;
            }
        }
        return null;
    }

    public FileTransferring getFileTransferring(){
        return fileTransferring;
    }

    public void addDownloadProgressListener(DownloadProgressListener listener) {
        fileTransferring.addDownloadProgressListener(listener);
    }
}
