package com.andrelucs.filesharingapp.communication.server;

import com.andrelucs.filesharingapp.communication.FileInfo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class Server {

    private final ServerSocket serverSocket;
    protected final List<Socket> clientConnections = new CopyOnWriteArrayList<>();
    private final Thread acceptClientsThread;

    private final List<FileInfo> clientFiles = new ArrayList<>();

    public Server() throws IOException {
        this(1234);
    }

    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        acceptClientsThread = new Thread(this::acceptClients);
    }

    public void start() {
        acceptClientsThread.start();
    }

    private void acceptClients() {
        while (!serverSocket.isClosed()) {
            try {
                System.out.println("Waiting for client connection...");
                Socket clientSocket = serverSocket.accept();
                removeClosedConnections();
                clientConnections.add(clientSocket);
                System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());
                new Thread(new ClientHandler(clientSocket, this)).start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void removeClient(Socket clientSocket) throws IOException {
        clientSocket.close();
        clientConnections.remove(clientSocket);
        String ip = clientSocket.getInetAddress().getHostAddress();
        clientFiles.removeIf(file -> file.owner().equals(ip));
    }

    private void removeClosedConnections() {
        clientConnections.stream().filter(Socket::isClosed).forEach(clientConnections::remove);
    }

    public void addToFileList(FileInfo fileInfo) {
        clientFiles.add(fileInfo);
    }

    public void addToFileList(String fileName, String ownerIp, Long fileSize) {
        clientFiles.add(new FileInfo(fileName, ownerIp, fileSize));
    }

    public void removeFromFileList(String ip, String fileName) {
        clientFiles.removeIf(file -> file.owner().equals(ip) && file.name().equals(fileName));
    }

    public void stop() {
        try {
            serverSocket.close();
            acceptClientsThread.join();
        } catch (Exception e) {
            System.err.println("Error in server: " + e.getMessage());
        }
    }

    public List<FileInfo> searchFile(String filePattern) {
        List<FileInfo> files = new ArrayList<>();
        Pattern pattern = Pattern.compile(filePattern, Pattern.CASE_INSENSITIVE);

        clientFiles.stream().filter(file -> pattern.matcher(file.name()).find()).forEach(files::add);

        return files;
    }

    public static void main(String[] args) {
        try {
            Server server = new Server();
            server.start();

            // Closes server after 10 minutes
            Thread.sleep(1000 * 60 * 10);
            server.stop();
        } catch (Exception e) {
            System.err.println("Error in server: " + e.getMessage());
        }
    }
}

