package com.andrelucs.filesharingapp.communication.client;

import com.andrelucs.filesharingapp.communication.FileInfo;
import com.andrelucs.filesharingapp.communication.client.file.DownloadProgressListener;
import com.andrelucs.filesharingapp.communication.client.file.FileTracker;
import com.andrelucs.filesharingapp.communication.client.file.FileTransferring;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

import static com.andrelucs.filesharingapp.communication.ProtocolCommand.*;

public class Client implements Closeable, SearchEventListener {
    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());
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

    private FileTracker folderTracker;
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
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        this.serverResponseHandler = new ServerResponseHandler(this);
        this.serverResponseHandler.addSearchResultListener(this);
        this.responseReadingThread = new Thread(this::readResponses);
        this.responseReadingThread.setName("Response Reading");
        this.responseReadingThread.setDaemon(true);

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
     *
     * @return The network address or <code>null</code> if it could not be found
     */
    public static String getNetworkAdress() {
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
            LOGGER.warning("Could not get network address: " + e.getMessage());
        }
        return null;
    }

    public void start() {
        responseReadingThread.start();
        sendJoinRequest();
        waitConnection();
        if(getFileTracker() != null)
            getFileTracker().sendPendingFiles();

        if (fileTransferringThread != null) fileTransferringThread.start();
    }

    @Override
    public void close() throws IOException {
        deleteAllFiles();
        writer.println(LEAVE.format());
        waitDisconnection();
        shutdown();
    }

    public void shutdown() throws IOException {
        if (fileTransferringThread != null) fileTransferringThread.interrupt();
        responseReadingThread.interrupt();
        writer.close();
        reader.close();
        socket.close();
        if (fileTransferring != null) fileTransferring.close();
        if(getFileTracker() != null) getFileTracker().close();
    }

    private void deleteAllFiles() {
        boolean hasSharedFiles = getFileTracker().getTrackedFiles().isEmpty();
        if (hasSharedFiles) {
            getFileTracker().deleteAllFiles();
        }
    }

    // Requests
    public void sendJoinRequest() {
        writer.println(JOIN.format(socket.getLocalAddress().getHostAddress()));
    }

    public void sendCreateFileRequest(String fileName, long fileSize) {
        writer.println(CREATEFILE.format(fileName, Long.toString(fileSize)));
    }

    public void sendDeleteFileRequest(String fileName) {
        writer.println(DELETEFILE.format(fileName));
    }

    public void sendSearchRequest(String pattern) {
        writer.println(SEARCH.format(pattern));
        searchFiles.clear();
        searchFileOwners.clear();
    }
    //---

    /**
     * Download a file from multiple owners
     *
     * @param fileName The name of the file
     * @param owners   The owners to download from
     * @throws IllegalStateException If the FileTransferring is not initialized
     */
    public void downloadFileFromOwners(String fileName, Set<String> owners) {
        if (fileTransferring == null) {
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
                    LOGGER.warning("Connection wait interrupted");
                }
            }
        }
    }

    private void waitDisconnection() {
        synchronized (connectionLock) {
            long endTime = System.currentTimeMillis() + 2000;
            while (isConnected && System.currentTimeMillis() < endTime) {
                try {
                    connectionLock.wait(2000);
                } catch (InterruptedException e) {
                    LOGGER.warning("Disconnection wait interrupted");
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

    private void readResponses() {
        try {
            String response;
            while ((response = reader.readLine()) != null) {
                final String RESET = "\u001B[0m";
                final String GREEN = "\u001B[32m";
                LOGGER.fine(GREEN + "Response received: " + response + RESET);
                serverResponseHandler.handleResponse(response);
                if (!isConnected) break;
            }
        } catch (SocketException e) {
            System.err.println("Exception reading responses: Server disconnected");
            try {
                shutdown();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            setConnected(false);
        } catch (IOException e) {
            LOGGER.warning("Exception reading responses: " + e.getMessage());
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
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("Invalid folder");
        }

        FileTracker newTracker = new FileTracker(this, folder.toPath());
        if (folderTracker != null) {
            folderTracker.deleteAllFiles();
            folderTracker.waitAllFilesDeletion();
            folderTracker.close();
        }
        folderTracker = newTracker;

        if (this.fileTransferring == null && this.fileTransferringThread == null) {
            this.fileTransferring = new FileTransferring(this, folder.toPath());
            this.fileTransferringThread = new Thread(fileTransferring);
            this.fileTransferringThread.setDaemon(true);
            this.fileTransferringThread.setName("FileTransferring Thread");
            if (isConnected) fileTransferringThread.start();
        }
        // if is connected then the user already started the client, so we can send the files
        if (isConnected) newTracker.sendPendingFiles();
    }

    public Set<String> getFileOwners(String fileName) {
        return new HashSet<>(searchFileOwners.get(fileName));
    }


    public FileTracker getFileTracker() {
        return folderTracker;
    }

    public List<File> getTrackedFiles() {
        if (folderTracker == null) return new ArrayList<>();
        return getFileTracker().getTrackedFiles();
    }

    public List<String> getSharedFileNames() {
        if (folderTracker == null) return new ArrayList<>();
        return getFileTracker().getSharedFileNames();
    }

    public void addSearchResultListener(SearchEventListener listener) {
        serverResponseHandler.addSearchResultListener(listener);
    }

    public void removeSearchResultListener(SearchEventListener listener) {
        serverResponseHandler.removeSearchResultListener(listener);
    }

    /**
     * Shares a file with the network
     *
     * @param file The file to be shared
     */
    public void shareFile(File file) {
        getFileTracker().shareFile(file);
    }

    /**
     * Stops sharing a file that was previously shared
     *
     * @param file The file to be unshared
     */
    public void deleteFile(File file) {
        getFileTracker().unShareFile(file);
    }

    public void confirmFileSharing(String fileName) {
        if (folderTracker.getFile(fileName) != null) {
            folderTracker.confirmFileSharing(fileName);
        }
    }

    public void confirmUnsharedFile(String fileName) {
        if (folderTracker.getSharedFile(fileName) != null) {
            folderTracker.confirmUnsharedFile(fileName);
        }
    }

    public File getFile(String fileName) {
        File file;
        if ((file = folderTracker.getFile(fileName)) != null) {
            return file;
        }
        return null;
    }

    public FileTransferring getFileTransferring() {
        return fileTransferring;
    }

    public void addDownloadProgressListener(DownloadProgressListener listener) throws IllegalStateException {
        if (fileTransferring == null) throw new IllegalStateException("FileTransferring not initialized");
        fileTransferring.addDownloadProgressListener(listener);
    }
}
