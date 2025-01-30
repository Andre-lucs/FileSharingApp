package com.andrelucs.filesharingapp.communication.client.file;

import com.andrelucs.filesharingapp.communication.FileInfo;
import com.andrelucs.filesharingapp.communication.ProtocolCommand;
import com.andrelucs.filesharingapp.communication.client.Client;
import org.jetbrains.annotations.NotNull;

import java.io.*; //TODO usar java.nio em vez do java.io padrao
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileTransferring implements Runnable, Closeable {
    private static final int FILE_TRANSFER_PORT = 1235;
    private static final int BUFFER_SIZE = 1024 * 8;
    private static final Logger logger = Logger.getLogger(FileTransferring.class.getName());
    private final ServerSocket serverSocket;
    private final Client client;
    private final Path downloadFolder;
    private final ExecutorService downloadExecutorService;

    private final List<String> filesBeingUploaded = new ArrayList<>();
    private final List<String> filesBeingDownloaded = new ArrayList<>();
    private final Map<String, Float> downloadProgress = new HashMap<>();
    private final Object tempFolderLock = new Object();
    private final List<FileTraficListener> traficListeners = new ArrayList<>();

    public FileTransferring(Client client, Path downloadFolder) throws IOException {
        this.serverSocket = new ServerSocket(FILE_TRANSFER_PORT);
        this.client = client;
        this.downloadFolder = downloadFolder;
        this.downloadExecutorService = Executors.newFixedThreadPool(10);
    }

    @Override
    public void run() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                new Thread(() -> serveFile(socket)).start();
            } catch (IOException e) {
                if (e instanceof SocketException && e.getMessage().equals("Socket closed")) {
                    break;
                } else {
                    logger.log(Level.SEVERE, "Error accepting connection", e);
                }
            }
        }
    }

    private void serveFile(Socket socket) {
        try (
                socket;
                DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            String getRequest = reader.readLine();
            while (getRequest == null || ProtocolCommand.fromString(getRequest.split(" ")[0]) != ProtocolCommand.GET) {
                getRequest = reader.readLine();
            }
            String[] parts = getRequest.split(" ");

            File requestedFile = client.getFile(Client.decodeFileName(parts[1]));
            if (requestedFile == null || !requestedFile.exists() || !requestedFile.canRead()) {
                String fileName = parts[1];
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].contains(".")) {
                        fileName = Stream.of(parts).skip(1).limit(i).collect(Collectors.joining(" "));
                        break;
                    }
                }
                if(fileName.equals(parts[1])) {
                    return;
                }
                requestedFile = client.getFile(fileName);
                if(requestedFile == null || !requestedFile.exists() || !requestedFile.canRead()) {
                    return;
                }
            }
            String[] range = parts[parts.length-1].split("-");
            long startByte = Integer.parseInt(range[0]);
            long endByte = (range.length > 1) ? Integer.parseInt(range[1]) : requestedFile.length();
            long contentLength = endByte - startByte;

            filesBeingUploaded.add(requestedFile.getName());

            // Send the file -> closes the input stream -> closes the socket
            try (FileInputStream fileInputStream = new FileInputStream(requestedFile)) {
                File finalRequestedFile = requestedFile;
                traficListeners.forEach(listener -> listener.onFileAction(FileAction.UPLOAD, new FileInfo(finalRequestedFile.getName(), socket.getInetAddress().toString(), finalRequestedFile.length())));
                int bytes;
                long totalBytes = 0;
                byte[] buffer = new byte[BUFFER_SIZE];
                fileInputStream.skip(startByte);

                // While not reached end of file or not reached the requested byte range
                while ((bytes = fileInputStream.read(buffer)) != -1) {
                    bytes = (int) Math.min(bytes, contentLength - totalBytes);
                    totalBytes += bytes;
                    dataOutputStream.write(buffer, 0, bytes);
                    dataOutputStream.flush();

                    if (totalBytes >= contentLength) {
                        break;
                    }
                }
            } finally {
                filesBeingUploaded.remove(requestedFile.getName());
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error serving file", e);
        }
    }

    /**
     * Downloads a file from multiple owners and combines them into a single file.
     *
     * @param fileInfo the file to be downloaded
     * @param owners   the owners from which the file will be downloaded
     * @return the downloaded file
     */
    public File downloadFromMultipleOwners(@NotNull FileInfo fileInfo, @NotNull Set<String> owners/*, Function<Float,Void> progressTracker*/) { // TODO progress tracker
        filesBeingDownloaded.add(fileInfo.name());
        traficListeners.forEach(listener -> listener.onFileAction(FileAction.DOWNLOAD, fileInfo));
        var uniqueFileName = (Files.exists(downloadFolder.resolve(fileInfo.name())) ? UUID.randomUUID().toString().substring(0, 5) : "") + fileInfo.name();
        var newFile = downloadFolder.resolve(uniqueFileName).toFile();
        List<Future<File>> futureTempFiles = new ArrayList<>();
        List<String> ownerList = new ArrayList<>(owners);
        final long bytesPerOwner = fileInfo.size() / ownerList.size();
        long remainingBytes = fileInfo.size() % ownerList.size();
        for (int i = 0; i < ownerList.size(); i++) {
            String owner = (String) ownerList.toArray()[i];
            long startByte = i * bytesPerOwner;
            long endByte = (i + 1) * bytesPerOwner + (i == ownerList.size() - 1 ? remainingBytes : 0);
            futureTempFiles.add(asyncDownloadFileBytes(fileInfo.name(), owner, startByte, endByte, (totalBytes) -> {
                downloadProgress.put(fileInfo.name(), (float) totalBytes / fileInfo.size() * 100);
                traficListeners.forEach(listener -> listener.onFileAction(FileAction.DOWNLOAD_PROGRESS, fileInfo));
                return null;
            }));
        }
        if (futureTempFiles.size() == 1) {
            try {
                File tempFile = futureTempFiles.getFirst().get();
                Files.move(tempFile.toPath(), newFile.toPath());
            } catch (InterruptedException | ExecutionException | IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try (FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
                for (Future<File> futureTempFile : futureTempFiles) {
                    try {
                        File tempFile = futureTempFile.get();
                        try (FileInputStream fileInputStream = new FileInputStream(tempFile)) {
                            int bytes;
                            byte[] buffer = new byte[BUFFER_SIZE];
                            while ((bytes = fileInputStream.read(buffer)) != -1) {
                                fileOutputStream.write(buffer, 0, bytes);
                            }
                        }
                        tempFile.delete();
                    } catch (ExecutionException | InterruptedException e) {
                        logger.log(Level.SEVERE, "Error downloading file from multiple owners", e);
                        throw new RuntimeException(e);
                    }
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error writing combined file", e);
            }
        }

        filesBeingDownloaded.remove(fileInfo.name());
        downloadProgress.remove(fileInfo.name());
        traficListeners.forEach(listener -> listener.onFileAction(FileAction.DOWNLOAD_COMPLETE, fileInfo));
        return newFile;
    }

    private @NotNull Future<File> asyncDownloadFileBytes(String fileName, String owner, long startByte, long endByte, Function<Long, Void> progressTracker) {
        return downloadExecutorService.submit(() -> downloadFileBytes(fileName, owner, startByte, endByte, progressTracker));
    }

    /**
     * Download a set of bytes of a given file from the specified owner
     *
     * @param fileName  the name of the file to be downloaded
     * @param owner     the owner of the file
     * @param startByte the start byte
     * @param endByte   the end byte
     * @return the downloaded file
     * @throws IOException if an I/O error occurs
     */
    private @NotNull File downloadFileBytes(String fileName, String owner, long startByte, long endByte, Function<Long, Void> progressTracker) throws IOException {

        // Creates temp folder if not exists
        Path temporaryFolder = downloadFolder.resolve(".temp");
        synchronized (tempFolderLock){
            if (!Files.exists(temporaryFolder)) {
                Files.createDirectory(temporaryFolder);
                temporaryFolder.toFile().deleteOnExit();
            }
        }
        File file = getBytes(fileName, owner, startByte, endByte, progressTracker);
        if (file.length() == 0) {
            file.delete();
            String encodedFileName = Client.encodeFileName(fileName);
            file = getBytes(encodedFileName, owner, startByte, endByte, progressTracker);
            if (file.length() == 0) {
                logger.log(Level.SEVERE, "Error downloading file bytes for both encoded and uncoded name: " + fileName);
                throw new IOException("Error downloading file bytes for encoded name: " + fileName);
            }
        }
        return file;
    }

    private @NotNull File getBytes(String fileName, String owner, long startByte, long endByte, Function<Long, Void> progressTracker) throws IOException {
        File tempFile = File.createTempFile(fileName, startByte + "-" + endByte + UUID.randomUUID(), downloadFolder.toFile());
        tempFile.deleteOnExit();

        try (
                Socket socket = new Socket(owner, FILE_TRANSFER_PORT);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                FileOutputStream fileOutputStream = new FileOutputStream(tempFile)
        ) {
            writer.println(ProtocolCommand.GET.format(fileName, startByte, endByte));
            writer.flush();

            int bytes;
            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytes = 0;

            while ((bytes = dataInputStream.read(buffer)) != -1) {
                bytes = (int) Math.min(bytes, endByte - startByte - totalBytes);
                totalBytes += bytes;
                progressTracker.apply(totalBytes);
                fileOutputStream.write(buffer, 0, bytes);

                if (totalBytes >= endByte - startByte) {
                    break;
                }
            }
            fileOutputStream.flush();
            return tempFile;

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error downloading file bytes", e);
            traficListeners.forEach(listener -> listener.onFileAction(FileAction.DOWNLOAD, new FileInfo(fileName, owner, endByte - startByte)));
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        downloadExecutorService.shutdown();
        try {
            if (!downloadExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
                downloadExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Error shutting down executor service", e);
            downloadExecutorService.shutdown();
        }
    }

    public boolean isBeingUploaded(File file) {
        return filesBeingUploaded.contains(file.getName());
    }

    public void addFileTraficListener(FileTraficListener listener) {
        traficListeners.add(listener);
    }

    public void addDownloadProgressListener(DownloadProgressListener listener) {
        traficListeners.add((action, fileInfo) -> {
            if (action == FileAction.DOWNLOAD_PROGRESS) {
                listener.onProgressUpdate(fileInfo, downloadProgress.get(fileInfo.name()));
            }
        });
    }
}
