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

            File requestedFile = client.getFile(parts[1]);
            if (requestedFile == null) {
                dataOutputStream.writeUTF("File not found");
                return;
            }
            if (!requestedFile.exists() || !requestedFile.canRead()) {
                logger.severe("File not found or cannot be read: " + requestedFile.getAbsolutePath());
                dataOutputStream.writeUTF("File not found or cannot be read");
                return;
            }
            String[] range = parts[2].split("-");
            long startByte = Integer.parseInt(range[0]);
            long endByte = (range.length > 1) ? Integer.parseInt(range[1]) : requestedFile.length();
            long contentLength = endByte - startByte;
            logger.info("Serving file: " + requestedFile.getName() + " from byte " + startByte + " to " + endByte);

            filesBeingUploaded.add(requestedFile.getName());

            // Send the file -> closes the input stream -> closes the socket
            try (FileInputStream fileInputStream = new FileInputStream(requestedFile)) {
                int bytes;
                long totalBytes = 0;
                byte[] buffer = new byte[BUFFER_SIZE];
                fileInputStream.skip(startByte);

                // While not reached end of file or not reached the requested byte range
                while ((bytes = fileInputStream.read(buffer)) != -1) {
                    totalBytes += bytes;
                    dataOutputStream.write(buffer, 0, bytes);
                    dataOutputStream.flush();
                    logger.info("Sent " + bytes + " bytes, total sent: " + totalBytes + " bytes");

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

    public File downloadFromSingleOwner(@NotNull FileInfo fileInfo, Function<Float, Void> downloadProgressTracker) throws FileAlreadyExistsException {
        filesBeingDownloaded.add(fileInfo.name());
        downloadProgress.put(fileInfo.name(), 0f);
        if(downloadProgressTracker != null) downloadProgressTracker.apply(0f);
        try {
            File tempFile = downloadFileBytes(fileInfo.name(), fileInfo.owner(), 0, fileInfo.size(), totalBytes -> {
                downloadProgress.put(fileInfo.name(), (float) totalBytes / fileInfo.size());
                if (downloadProgressTracker != null) downloadProgressTracker.apply((float) totalBytes / fileInfo.size());

                return null;
            });
            if (Files.exists(downloadFolder.resolve(fileInfo.name()))) {
                return Files.copy(tempFile.toPath(), downloadFolder.resolve(UUID.randomUUID().toString().substring(0, 5) + fileInfo.name())).toFile();
            } else {
                return Files.copy(tempFile.toPath(), downloadFolder.resolve(fileInfo.name())).toFile();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error downloading file from single owner", e);
            throw new RuntimeException(e);
        }finally {
            filesBeingDownloaded.remove(fileInfo.name());
            downloadProgress.remove(fileInfo.name());
        }
    }

    /**
     * Downloads a file from multiple owners and combines them into a single file.
     *
     * @param fileInfo the file to be downloaded
     * @param owners   the owners from which the file will be downloaded
     * @return the downloaded file
     */
    public File downloadFromMultipleOwners(@NotNull FileInfo fileInfo, @NotNull Set<String> owners/*, Function<Float,Void> progressTracker*/) {
        var uniqueFileName = fileInfo.name() + UUID.randomUUID().toString().substring(0, 5); // TODO later change this to just the filename
        var newFile = downloadFolder.resolve(uniqueFileName).toFile();
        List<Future<File>> futureTempFiles = new ArrayList<>();
        for (String owner : owners) {
            futureTempFiles.add(asyncDownloadFileBytes(fileInfo.name(), owner, 0, fileInfo.size(), totalBytes -> {
                downloadProgress.put(fileInfo.name(), (float) totalBytes / fileInfo.size());
                return null;
            }));
        }
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
                }
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing combined file", e);
        }

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
        if (!Files.exists(downloadFolder.resolve("temp"))) {
            Files.createDirectory(downloadFolder.resolve("temp"));
        }
        File tempFile = File.createTempFile(fileName, startByte + "-" + endByte, downloadFolder.resolve("temp").toFile());
        tempFile.deleteOnExit();

        try (
                Socket socket = new Socket(owner, FILE_TRANSFER_PORT);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
        ) {
            writer.println(ProtocolCommand.GET.format(fileName, startByte, endByte));
            writer.flush();
            logger.info("Requesting file: " + fileName);

            int bytes;
            byte[] buffer = new byte[4 * 1024];
            long totalBytes = 0;
            while ((bytes = dataInputStream.read(buffer)) != -1) {
                totalBytes += bytes;
                progressTracker.apply(totalBytes);
                fileOutputStream.write(buffer, 0, bytes);
                logger.info("Received " + bytes + " bytes, total received: " + totalBytes + " bytes");

                if (totalBytes >= endByte - startByte) {
                    break;
                }
            }
            fileOutputStream.flush();
            logger.info("File received: " + fileName);
            return tempFile;

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error downloading file bytes", e);
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
}
